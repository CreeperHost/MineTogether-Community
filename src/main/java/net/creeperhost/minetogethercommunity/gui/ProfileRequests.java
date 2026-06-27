package net.creeperhost.minetogethercommunity.gui;

import com.google.gson.Gson;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogether.lib.chat.request.IsBannedRequest;
import net.creeperhost.minetogether.lib.chat.request.v2.Apiv2Response;
import net.creeperhost.minetogether.lib.chat.request.v2.DeleteBanRequest;
import net.creeperhost.minetogether.lib.chat.request.v2.GetBanRequest;
import net.creeperhost.minetogether.lib.chat.request.v2.GetNameRequest;
import net.creeperhost.minetogether.lib.chat.request.v2.GetNamesRequest;
import net.creeperhost.minetogether.lib.chat.request.v2.PutNameRequest;
import net.creeperhost.minetogether.session.MineTogetherSession;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.util.ModPackInfo;
import net.minecraft.client.resources.I18n;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ProfileRequests {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Profile");
    private static final Gson GSON = new Gson();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss", Locale.ROOT);
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final String API_V2 = "https://minetogether.io/api/v2/";
    private static final String API_CREEPER_HOST = "https://api.creeper.host/minetogether/";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3,
            new ThreadFactoryBuilder().setDaemon(true).setNameFormat("MT Profile Requests %d").build());

    private static final Map<CompletableFuture<?>, Runnable> ACTIVE_REQUESTS = new LinkedHashMap<>();
    private static final List<String> NAME_OPTIONS = new ArrayList<>();
    private static final AtomicReference<IsBannedRequest.Ban> ACTIVE_BAN = new AtomicReference<>();
    private static final AtomicReference<GetBanRequest.Ban> BAN_INFO = new AtomicReference<>();
    private static final AtomicReference<String> CUSTOM_NAME = new AtomicReference<>("");
    private static Consumer<String> feedback = value -> {};
    private static volatile long nextChange = -1L;

    private ProfileRequests() {
    }

    public static void guiOpened(Consumer<String> feedback) {
        ProfileRequests.feedback = feedback == null ? value -> {} : feedback;
        updateRequests();
    }

    public static void updateRequests() {
        List<CompletableFuture<?>> futures = new ArrayList<>(ACTIVE_REQUESTS.keySet());
        for (CompletableFuture<?> future : futures) {
            if (future.isDone()) {
                Runnable onComplete = ACTIVE_REQUESTS.remove(future);
                if (onComplete != null) onComplete.run();
            }
        }
    }

    public static boolean requestsInProgress() {
        return !ACTIVE_REQUESTS.isEmpty();
    }

    public static List<String> getNameOptions() {
        synchronized (NAME_OPTIONS) {
            return new ArrayList<String>(NAME_OPTIONS);
        }
    }

    public static String getCustomName() {
        return StringUtils.defaultString(CUSTOM_NAME.get());
    }

    public static boolean canChangeName() {
        return nextChange > 0 && System.currentTimeMillis() > nextChange * 1000L;
    }

    public static String getCanChangeText() {
        return nextChange <= 0 ? I18n.format("minetogether.gui.profile.unavailable") : DATE_FORMAT.format(new Date(nextChange * 1000L));
    }

    public static boolean isBanned() {
        return ACTIVE_BAN.get() != null;
    }

    public static String getBanId() {
        GetBanRequest.Ban ban = BAN_INFO.get();
        IsBannedRequest.Ban active = ACTIVE_BAN.get();
        return ban != null ? safe(ban.id) : active == null ? "N/A" : safe(active.id);
    }

    public static String getModerator() {
        GetBanRequest.Ban ban = BAN_INFO.get();
        IsBannedRequest.Ban active = ACTIVE_BAN.get();
        return ban != null ? safe(ban.moderator) : active == null ? "N/A" : safe(active.bannedBy);
    }

    public static String getReason() {
        GetBanRequest.Ban ban = BAN_INFO.get();
        IsBannedRequest.Ban active = ACTIVE_BAN.get();
        return ban != null ? safe(StringUtils.defaultIfBlank(ban.reason, ban.message)) : active == null ? "N/A" : safe(active.reason);
    }

    public static String getTimestamp() {
        IsBannedRequest.Ban active = ACTIVE_BAN.get();
        return active == null ? "N/A" : safe(active.timestamp);
    }

    public static String getAppealStatus() {
        GetBanRequest.Ban ban = BAN_INFO.get();
        if (ban == null || ban.appeal == null) return I18n.format("minetogether.gui.profile.ban.appeal_no_info");
        return I18n.format("minetogether.gui.profile.ban.appeal_submitted");
    }

    public static String getNextAppealText() {
        GetBanRequest.Ban ban = BAN_INFO.get();
        if (ban == null || ban.appeal == null || ban.appeal.nextAppeal <= 0) return "N/A";
        return DATE_FORMAT.format(new Date(ban.appeal.nextAppeal * 1000L));
    }

    public static String getAppealNotesText() {
        GetBanRequest.Ban ban = BAN_INFO.get();
        if (ban == null || ban.appeal == null || ban.appeal.notes == null || ban.appeal.notes.isEmpty()) {
            return "";
        }
        GetBanRequest.Note latest = ban.appeal.notes.get(ban.appeal.notes.size() - 1);
        String moderator = safe(latest.moderator);
        String message = safe(latest.message);
        String timestamp = latest.timestamp <= 0 ? "" : " (" + DATE_FORMAT.format(new Date(latest.timestamp * 1000L)) + ")";
        return I18n.format("minetogether.gui.profile.ban.appeal_note", moderator, message + timestamp);
    }

    public static void fetchName(final Runnable onComplete) {
        if (!chatReady()) return;
        CompletableFuture<?> future = CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    GetNameRequest.Response response = requestJson("GET", API_V2 + "name", null, GetNameRequest.Response.class, true, false);
                    if (response != null && response.success) {
                        CUSTOM_NAME.set(StringUtils.defaultString(response.name));
                        nextChange = response.nextChange;
                    } else {
                        report("minetogether.gui.profile.error.get_name_fail", response == null ? "Empty response" : response.reason);
                    }
                } catch (Throwable ex) {
                    LOGGER.error("Failed to retrieve current MineTogether name", ex);
                    report("minetogether.gui.profile.error.get_name_fail", ex.getMessage());
                }
            }
        }, EXECUTOR);
        ACTIVE_REQUESTS.put(future, onComplete);
    }

    public static void fetchNameOptions(final Runnable onComplete) {
        if (!chatReady()) return;
        synchronized (NAME_OPTIONS) {
            if (!NAME_OPTIONS.isEmpty()) {
                if (onComplete != null) onComplete.run();
                return;
            }
        }
        CompletableFuture<?> future = CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    GetNamesRequest.Response response = requestJson("GET", API_V2 + "names", null, GetNamesRequest.Response.class, true, false);
                    if (response != null && response.success) {
                        synchronized (NAME_OPTIONS) {
                            NAME_OPTIONS.clear();
                            if (response.names != null) NAME_OPTIONS.addAll(response.names);
                        }
                    } else {
                        report("minetogether.gui.profile.error.get_names_fail", response == null ? "Empty response" : response.reason);
                    }
                } catch (Throwable ex) {
                    LOGGER.error("Failed to retrieve MineTogether name options", ex);
                    report("minetogether.gui.profile.error.get_names_fail", ex.getMessage());
                }
            }
        }, EXECUTOR);
        ACTIVE_REQUESTS.put(future, onComplete);
    }

    public static void setName(final String newName, final Runnable onComplete) {
        if (!chatReady()) return;
        if (StringUtils.isBlank(newName)) {
            report("minetogether.gui.profile.name_is_empty");
            return;
        }
        report("minetogether.gui.profile.name_change_sent");
        CompletableFuture<?> future = CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    String body = GSON.toJson(Collections.singletonMap("name", newName.trim()));
                    PutNameRequest.Response response = requestJson("PUT", API_V2 + "name", body, PutNameRequest.Response.class, true, false);
                    if (response != null && response.success) {
                        CUSTOM_NAME.set(StringUtils.defaultString(response.name));
                        nextChange = response.nextChange;
                        report("minetogether.gui.profile.name_change_success", response.name);
                    } else {
                        if (response != null) nextChange = response.nextChange;
                        report("minetogether.gui.profile.name_change_fail", response == null ? "Empty response" : response.reason);
                    }
                } catch (Throwable ex) {
                    LOGGER.error("Failed to set MineTogether name", ex);
                    report("minetogether.gui.profile.name_change_fail", ex.getMessage());
                }
            }
        }, EXECUTOR);
        ACTIVE_REQUESTS.put(future, onComplete);
    }

    public static void fetchBannedStatus(final Runnable onComplete) {
        if (!chatReady()) return;
        CompletableFuture<?> future = CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    Profile profile = MineTogetherChat.getOurProfile();
                    String hash = profile == null ? "" : profile.getFullHash();
                    if (StringUtils.isBlank(hash)) return;
                    String body = GSON.toJson(Collections.singletonMap("hash", hash));
                    IsBannedRequest.Response response = requestJson("PUT", API_CREEPER_HOST + "isbanned", body, IsBannedRequest.Response.class, false, true);
                    if (response != null && "success".equals(response.getStatus())) {
                        ACTIVE_BAN.set(response.banned ? response.ban : null);
                        if (response.banned && response.ban != null && StringUtils.isNotBlank(response.ban.id)) {
                            fetchBanInfo(null);
                        }
                    } else {
                        ACTIVE_BAN.set(null);
                        report("minetogether.gui.profile.error.get_ban_fail", response == null ? "Empty response" : response.getMessageOrNull());
                    }
                } catch (Throwable ex) {
                    LOGGER.error("Failed to retrieve MineTogether ban status", ex);
                    report("minetogether.gui.profile.error.get_ban_fail", ex.getMessage());
                }
            }
        }, EXECUTOR);
        ACTIVE_REQUESTS.put(future, onComplete);
    }

    public static void fetchBanInfo(final Runnable onComplete) {
        if (!chatReady()) return;
        final IsBannedRequest.Ban ban = ACTIVE_BAN.get();
        if (ban == null || StringUtils.isBlank(ban.id)) return;
        CompletableFuture<?> future = CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    GetBanRequest.Response response = requestJson("GET", API_V2 + "ban/" + ban.id, null, GetBanRequest.Response.class, true, false);
                    if (response != null && response.success) {
                        BAN_INFO.set(response.ban);
                    } else {
                        report("minetogether.gui.profile.error.get_ban_info_fail", response == null ? "Empty response" : response.reason);
                    }
                } catch (Throwable ex) {
                    LOGGER.error("Failed to retrieve MineTogether ban info", ex);
                    report("minetogether.gui.profile.error.get_ban_info_fail", ex.getMessage());
                }
            }
        }, EXECUTOR);
        ACTIVE_REQUESTS.put(future, onComplete);
    }

    public static void submitAppeal(final String appeal, final BiConsumer<Boolean, String> onComplete) {
        final GetBanRequest.Ban ban = BAN_INFO.get();
        if (!chatReady() || ban == null || StringUtils.isBlank(ban.id)) return;
        if (StringUtils.isBlank(appeal)) {
            report("minetogether.gui.profile.ban.appeal_is_empty");
            return;
        }

        final AtomicBoolean success = new AtomicBoolean(false);
        final AtomicReference<String> message = new AtomicReference<>("");
        report("minetogether.gui.profile.ban.submitting");
        CompletableFuture<?> future = CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    String body = GSON.toJson(Collections.singletonMap("text", appeal.trim()));
                    Apiv2Response response = requestJson("DELETE", API_V2 + "ban/" + ban.id, body, Apiv2Response.class, true, false);
                    boolean ok = response != null && response.success;
                    success.set(ok);
                    message.set(ok
                            ? I18n.format("minetogether.gui.profile.ban.submitted")
                            : I18n.format("minetogether.gui.profile.ban.appeal_fail", response == null ? "Empty response" : response.reason));
                    report(ok ? "minetogether.gui.profile.ban.submitted" : "minetogether.gui.profile.ban.appeal_fail",
                            response == null ? "Empty response" : response.reason);
                } catch (Throwable ex) {
                    LOGGER.error("Failed to submit MineTogether ban appeal", ex);
                    message.set(I18n.format("minetogether.gui.profile.ban.appeal_fail", ex.getMessage()));
                    report("minetogether.gui.profile.ban.appeal_fail", ex.getMessage());
                }
            }
        }, EXECUTOR);
        ACTIVE_REQUESTS.put(future, new Runnable() {
            @Override
            public void run() {
                if (onComplete != null) onComplete.accept(success.get(), message.get());
            }
        });
    }

    private static boolean chatReady() {
        return MineTogetherChat.CHAT_STATE != null && MineTogetherChat.CHAT_STATE.api != null;
    }

    private static <T> T requestJson(String method, String url, String body, Class<T> responseClass, boolean auth, boolean fingerprintHeaders) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", userAgent());
            if (fingerprintHeaders) {
                connection.setRequestProperty("Fingerprint", MineTogether.FINGERPRINT);
                connection.setRequestProperty("Identifier", ModPackInfo.getInfo().realName);
            }
            if (auth) {
                Object token = MineTogetherSession.getDefault().getTokenAsync().get(10, TimeUnit.SECONDS);
                if (token == null) throw new IOException("MineTogether session token is unavailable");
                connection.setRequestProperty("Authorization", "Bearer " + token.toString());
            }
            if (body != null) {
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                OutputStream outputStream = connection.getOutputStream();
                try {
                    outputStream.write(payload);
                } finally {
                    outputStream.close();
                }
            }

            int status = connection.getResponseCode();
            InputStream inputStream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String responseText = inputStream == null ? "" : readUtf8(inputStream);
            if (StringUtils.isBlank(responseText)) {
                throw new IOException("Empty response from " + url + " (HTTP " + status + ")");
            }
            return GSON.fromJson(responseText, responseClass);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readUtf8(InputStream inputStream) throws IOException {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            inputStream.close();
        }
    }

    private static String userAgent() {
        return "MineTogether-Community-mod/" + MineTogether.VERSION + " Minecraft/1.8.9 Modloader/forge";
    }

    private static void report(String key, Object... args) {
        feedback.accept(I18n.format(key, args));
    }

    private static String safe(String value) {
        return StringUtils.defaultIfBlank(value, "N/A");
    }
}
