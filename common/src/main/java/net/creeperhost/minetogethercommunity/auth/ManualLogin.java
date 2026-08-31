package net.creeperhost.minetogethercommunity.auth;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.architectury.hooks.client.screen.ScreenHooks;
import net.creeperhost.minetogether.session.JWebToken;
import net.creeperhost.minetogether.session.MineTogetherSession;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Offers website pairing when the normal Mojang-backed session request fails. */
public final class ManualLogin {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new Gson();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SITE_PROPERTY = "minetogether.connect.site";
    private static final String DEFAULT_SITE = "https://minetogether.io";
    private static final long POLL_INTERVAL_MS = 5000L;
    private static final long PAIRING_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        thread.setName("MineTogether Manual Login");
        return thread;
    });

    private static final AtomicReference<State> STATE = new AtomicReference<>(State.CHECKING);
    private static final AtomicBoolean PROMPT_SHOWN = new AtomicBoolean();
    private static volatile @Nullable Button titleButton;

    public static void init() {
        MineTogetherSession session = MineTogetherSession.getDefault();
        if (session.isOffline()) {
            STATE.set(State.UNAVAILABLE);
            return;
        }

        session.getTokenAsync().whenComplete((token, error) -> {
            if (token != null) {
                STATE.set(State.AUTHENTICATED);
                return;
            }

            if (error == null) {
                LOGGER.warn("Automatic MineTogether authentication returned no session token. Manual login is available.");
            } else {
                LOGGER.warn("Automatic MineTogether authentication failed. Manual login is available.", error);
            }
            STATE.set(State.AVAILABLE);
            Minecraft.getInstance().execute(ManualLogin::offerOnCurrentTitleScreen);
        });
    }

    public static void addTitleScreenButton(TitleScreen screen) {
        State state = STATE.get();
        if (state == State.CHECKING || state == State.UNAVAILABLE || state == State.AUTHENTICATED) return;

        Button existing = titleButton;
        if (existing != null && screen.children().contains(existing)) return;

        Button button = Button.builder(buttonText(state), ignored -> start())
                .bounds(screen.width - 155, 5, 150, 20)
                .build();
        button.active = state == State.AVAILABLE;
        titleButton = button;
        ScreenHooks.addRenderableWidget(screen, button);

        if (state == State.AVAILABLE && PROMPT_SHOWN.compareAndSet(false, true)) {
            Minecraft.getInstance().execute(() -> showPrompt(screen));
        }
    }

    private static void offerOnCurrentTitleScreen() {
        if (Minecraft.getInstance().screen instanceof TitleScreen titleScreen) {
            addTitleScreenButton(titleScreen);
        }
    }

    private static void showPrompt(TitleScreen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != parent || STATE.get() != State.AVAILABLE) return;

        minecraft.setScreen(new ConfirmScreen(accepted -> {
            minecraft.setScreen(parent);
            if (accepted) start();
        },
                Component.translatable("minetogether:auth.manual.title"),
                Component.translatable("minetogether:auth.manual.description"),
                Component.translatable("minetogether:auth.manual.sign_in"),
                Component.translatable("minetogether:auth.manual.not_now")
        ));
    }

    private static void start() {
        if (!STATE.compareAndSet(State.AVAILABLE, State.PAIRING)) return;
        updateButton(State.PAIRING);

        EXECUTOR.execute(() -> {
            try {
                String code = generatePairingCode();
                String loginUrl = siteOrigin() + "/server-connect/" + code;
                Minecraft.getInstance().execute(() -> Util.getPlatform().openUri(loginUrl));

                JWebToken token = waitForToken(code);
                if (token == null) {
                    throw new IOException("Manual login timed out before pairing completed.");
                }
                validateIdentity(token);
                installToken(token);

                STATE.set(State.AUTHENTICATED);
                Minecraft.getInstance().execute(ManualLogin::loginSucceeded);
            } catch (Throwable ex) {
                LOGGER.error("MineTogether manual login failed.", ex);
                STATE.set(State.AVAILABLE);
                Minecraft.getInstance().execute(() -> loginFailed(ex));
            }
        });
    }

    private static @Nullable JWebToken waitForToken(String code) throws IOException {
        long deadline = System.currentTimeMillis() + PAIRING_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            JWebToken token = pollForToken(code);
            if (token != null) return token;

            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static @Nullable JWebToken pollForToken(String code) throws IOException {
        URL url = new URL(siteOrigin() + "/server-connect/api/poll?code=" + URLEncoder.encode(code, "UTF-8"));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");

            int status = connection.getResponseCode();
            String body = readBody(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (status == HttpURLConnection.HTTP_ACCEPTED) return null;
            if (status != HttpURLConnection.HTTP_OK) {
                LOGGER.debug("MineTogether manual login poll returned {}: {}", status, body);
                return null;
            }

            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (json == null || !"success".equals(getString(json, "status"))) return null;

            String rawToken = getString(json, "token");
            if (rawToken == null) throw new IOException("Manual login completed without a token.");

            JWebToken token = JWebToken.tryParse(rawToken);
            if (token == null || !token.isValid(MineTogetherSession.SessionServer.DEFAULT.publicKey)) {
                throw new IOException("Manual login returned an invalid session token.");
            }
            return token;
        } finally {
            connection.disconnect();
        }
    }

    private static void validateIdentity(JWebToken token) throws IOException {
        UUID expectedUuid = Minecraft.getInstance().getUser().getProfileId();
        if (!expectedUuid.equals(token.getUuid())) {
            throw new IOException("The MineTogether account is linked to a different Minecraft account.");
        }
    }

    private static void installToken(JWebToken token) throws Exception {
        MineTogetherSession session = MineTogetherSession.getDefault();
        session.forceResetToken();

        Path sessionFile = Paths.get("./.mtsession").resolve(token.getUuid() + ".session");
        Files.createDirectories(sessionFile.getParent());
        Files.write(sessionFile, token.toString().getBytes(StandardCharsets.UTF_8));

        JWebToken loaded = session.getTokenAsync().get(30, TimeUnit.SECONDS);
        if (loaded == null || !token.getUuid().equals(loaded.getUuid())) {
            throw new IOException("The manual session token could not be installed.");
        }
    }

    private static void loginSucceeded() {
        Button button = titleButton;
        if (button != null) button.visible = false;
        MineTogetherChat.simpleToast(
                Component.translatable("minetogether:auth.manual.success"),
                Component.translatable("minetogether:auth.manual.success.description")
        );
        MineTogetherChat.CHAT_STATE.ircClient.restart();
    }

    private static void loginFailed(Throwable error) {
        updateButton(State.AVAILABLE);
        MineTogetherChat.simpleToast(
                Component.translatable("minetogether:auth.manual.failed"),
                Component.literal(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage())
        );
    }

    private static void updateButton(State state) {
        Button button = titleButton;
        if (button == null) return;
        button.setMessage(buttonText(state));
        button.active = state == State.AVAILABLE;
    }

    private static Component buttonText(State state) {
        return Component.translatable(state == State.PAIRING
                ? "minetogether:auth.manual.waiting"
                : "minetogether:auth.manual.button");
    }

    private static String generatePairingCode() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String siteOrigin() {
        String origin = System.getProperty(SITE_PROPERTY, DEFAULT_SITE).trim();
        while (origin.endsWith("/")) {
            origin = origin.substring(0, origin.length() - 1);
        }
        return origin.isEmpty() ? DEFAULT_SITE : origin;
    }

    private static @Nullable String getString(JsonObject json, String name) {
        JsonElement element = json.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static String readBody(@Nullable InputStream stream) throws IOException {
        if (stream == null) return "";
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private enum State {
        CHECKING,
        AVAILABLE,
        PAIRING,
        AUTHENTICATED,
        UNAVAILABLE
    }

    private ManualLogin() {
    }
}
