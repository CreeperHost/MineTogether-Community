package net.creeperhost.minetogethercommunity.connect;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.creeperhost.minetogether.session.JWebToken;
import net.creeperhost.minetogether.session.MineTogetherSession;
import net.creeperhost.minetogethercommunity.config.Config;
import net.creeperhost.minetogethercommunity.connect.netty.HostNettyClient;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DedicatedServerConnect {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Connect");
    private static final Gson GSON = new Gson();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Path SESSION_DIR = Paths.get("./.mtsession");
    private static final long POLL_INTERVAL_MS = 5000L;
    private static final String SITE_PROPERTY = "minetogether.connect.site";
    private static final String DEFAULT_SITE = "https://minetogether.io";
    private static final String CONSOLE_CYAN = "\u001B[36m";
    private static final String CONSOLE_RESET = "\u001B[0m";
    private static final String CONNECT_JOIN_ADVERT = "Connected via MineTogether by CreeperHost. Need managed 24/7 Minecraft hosting? https://www.creeperhost.net";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MT Dedicated Connect");
        thread.setDaemon(true);
        return thread;
    });

    private static final Set<NetworkManager> CONNECT_CONNECTIONS = Collections.newSetFromMap(new ConcurrentHashMap<NetworkManager, Boolean>());
    private static final Set<UUID> ADVERTISED_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    private static volatile Future<?> activeTask;
    private static volatile HostNettyClient.HostConnection publishedServer;
    private static volatile boolean stopping;

    public static void serverStarted(MinecraftServer server) {
        if (server == null || !server.isDedicatedServer()) {
            return;
        }

        String mode = normalizeMode(Config.instance().dedicatedServerConnect);
        if ("off".equals(mode)) {
            LOGGER.info("Dedicated server MineTogether Connect is disabled by config.");
            return;
        }
        if ("auto".equals(mode) && !hasPrivateBind(server)) {
            LOGGER.info("Dedicated server MineTogether Connect auto mode did not start because the server bind is blank or public. Set dedicatedServerConnect to \"on\" to force it.");
            return;
        }

        if (activeTask != null && !activeTask.isDone()) {
            LOGGER.warn("Dedicated server MineTogether Connect is already starting.");
            return;
        }

        stopping = false;
        CONNECT_CONNECTIONS.clear();
        ADVERTISED_PLAYERS.clear();
        activeTask = EXECUTOR.submit(() -> runServerConnect(server));
    }

    public static void serverStopping(MinecraftServer server) {
        stopping = true;
        Future<?> task = activeTask;
        if (task != null) {
            task.cancel(true);
            activeTask = null;
        }
        HostNettyClient.HostConnection connection = publishedServer;
        if (connection != null) {
            connection.disconnect();
            publishedServer = null;
        }
        CONNECT_CONNECTIONS.clear();
        ADVERTISED_PLAYERS.clear();
    }

    public static void playerJoined(EntityPlayerMP player) {
        NetworkManager connection = playerConnection(player);
        if (connection == null || !CONNECT_CONNECTIONS.remove(connection)) return;
        if (!ADVERTISED_PLAYERS.add(player.getGameProfile().getId())) return;

        player.addChatMessage(new ChatComponentText(CONNECT_JOIN_ADVERT));
    }

    private static void runServerConnect(final MinecraftServer server) {
        try {
            JWebToken token = loadCachedToken();
            if (token == null) {
                token = pairThroughWebsite();
                if (token == null || stopping) return;
                writeSession(token);
            }

            LOGGER.info("Publishing dedicated server to MineTogether Connect as {}.", token.getUsername());
            publishedServer = HostNettyClient.publishServer(
                    server,
                    ConnectServices.getEndpoint(),
                    token,
                    ConnectServices.getModpackKey(),
                    server.getMaxPlayers(),
                    new HostNettyClient.HostListener() {
                        @Override
                        public void onAccepted() {
                            LOGGER.info("Dedicated server is published on MineTogether Connect.");
                        }

                        @Override
                        public void onDisconnected(String message) {
                            LOGGER.error("Dedicated server MineTogether Connect publish failed: {}", message);
                            publishedServer = null;
                        }

                        @Override
                        public void onChannelInactive(boolean disconnectRequested) {
                            if (!disconnectRequested) {
                                LOGGER.warn("Dedicated server MineTogether Connect proxy connection closed.");
                            }
                            publishedServer = null;
                        }

                        @Override
                        public void onMaxPlayers(int maxPlayers, int proxyMaxPlayers) {
                            LOGGER.info("Dedicated server MineTogether Connect player cap: {}", maxPlayers == Integer.MAX_VALUE ? "unlimited" : Integer.valueOf(maxPlayers));
                        }

                        @Override
                        public void onServerLink(NetworkManager connection) {
                            CONNECT_CONNECTIONS.add(connection);
                        }

                        @Override
                        public void onMessage(String message) {
                            LOGGER.info("[MTConnect Broadcast] {}", message);
                        }
                    }
            );
        } catch (Throwable ex) {
            if (!stopping) {
                LOGGER.error("Dedicated server MineTogether Connect failed to start.", ex);
            }
            publishedServer = null;
        }
    }

    private static JWebToken loadCachedToken() {
        if (!Files.isDirectory(SESSION_DIR)) return null;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(SESSION_DIR, "*.session")) {
            for (Path file : stream) {
                try {
                    String rawToken = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
                    JWebToken token = JWebToken.tryParse(rawToken);
                    if (token != null && token.isValid(MineTogetherSession.SessionServer.DEFAULT.publicKey)) {
                        LOGGER.info("Found valid MineTogether session cache: {}", file.toAbsolutePath());
                        return token;
                    }
                } catch (IOException ex) {
                    LOGGER.warn("Failed to read MineTogether session cache: {}", file.toAbsolutePath(), ex);
                }
            }
        } catch (IOException ex) {
            LOGGER.warn("Failed to scan MineTogether session cache.", ex);
        }
        return null;
    }

    private static JWebToken pairThroughWebsite() throws IOException {
        String code = generatePairingCode();
        String link = siteOrigin() + "/server-connect/" + code;

        LOGGER.info("{}MineTogether dedicated server Connect needs authentication. Visit: {}{}", CONSOLE_CYAN, link, CONSOLE_RESET);

        while (!stopping && !Thread.currentThread().isInterrupted()) {
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

    private static JWebToken pollForToken(String code) throws IOException {
        URL url = new URL(siteOrigin() + "/server-connect/api/poll?code=" + URLEncoder.encode(code, "UTF-8"));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");

        int status = connection.getResponseCode();
        String body = readBody(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
        if (status == HttpURLConnection.HTTP_ACCEPTED) return null;
        if (status != HttpURLConnection.HTTP_OK) {
            LOGGER.warn("MineTogether server pairing poll returned {}: {}", Integer.valueOf(status), body);
            return null;
        }

        JsonObject json = GSON.fromJson(body, JsonObject.class);
        if (json == null) return null;
        String responseStatus = getString(json, "status");
        if (!"success".equals(responseStatus)) return null;

        String rawToken = getString(json, "token");
        if (rawToken == null) {
            LOGGER.warn("MineTogether server pairing completed without a token.");
            return null;
        }

        JWebToken token = JWebToken.tryParse(rawToken);
        if (token == null || !token.isValid(MineTogetherSession.SessionServer.DEFAULT.publicKey)) {
            LOGGER.warn("MineTogether server pairing returned an invalid token.");
            return null;
        }
        return token;
    }

    private static void writeSession(JWebToken token) throws IOException {
        Files.createDirectories(SESSION_DIR);
        Path file = SESSION_DIR.resolve(token.getUuid() + ".session");
        Files.write(file, token.toString().getBytes(StandardCharsets.UTF_8));
        LOGGER.info("Stored MineTogether session cache: {}", file.toAbsolutePath());
    }

    private static String normalizeMode(String mode) {
        String normalized = mode == null ? "auto" : mode.trim().toLowerCase(Locale.ROOT);
        if ("off".equals(normalized) || "auto".equals(normalized) || "on".equals(normalized)) {
            return normalized;
        }
        LOGGER.warn("Unknown dedicatedServerConnect value '{}'. Falling back to auto.", mode);
        return "auto";
    }

    private static boolean hasPrivateBind(MinecraftServer server) {
        String bind = server.getServerHostname();
        if (bind == null || bind.trim().isEmpty()) return false;

        bind = bind.trim();
        if ("0.0.0.0".equals(bind) || "::".equals(bind)) return false;

        try {
            InetAddress address = InetAddress.getByName(bind);
            return address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress() || isUniqueLocalIpv6(address);
        } catch (IOException ex) {
            LOGGER.warn("Could not parse dedicated server bind address '{}'.", bind);
            return false;
        }
    }

    private static boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    private static NetworkManager playerConnection(EntityPlayerMP player) {
        if (player == null || player.playerNetServerHandler == null) return null;
        return player.playerNetServerHandler.netManager;
    }

    private static String getString(JsonObject json, String name) {
        JsonElement element = json.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static String readBody(InputStream stream) throws IOException {
        if (stream == null) return "";

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            stream.close();
        }
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
}
