package net.creeperhost.minetogethercommunity.oauth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Timer;
import java.util.TimerTask;

public class KeycloakOAuth {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether OAuth");
    private static final String API_KEY = "mt-ingame";
    private static final String API_SECRET = "d3b0c03e-4447-400b-ba48-e08902cd95d6";
    private static final String BASE_URL = "https://auth.minetogether.io/";
    private static final String REALM = "MineTogether";
    private static final String PROTECTED_RESOURCE_URL = "https://auth.minetogether.io/auth/realms/MineTogether/linksearch";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile OAuthWebServer server;
    private static volatile String status = "";

    private KeycloakOAuth() {
    }

    public static void start() {
        status = "minetogether.oauth.status.starting";
        final String secretState = "oHaiICanHazSecret" + RANDOM.nextInt(999999);
        final String codeVerifier = randomUrlToken(48);

        int port = -1;
        if (server != null && server.isAlive()) {
            server.stop();
        }
        server = null;
        for (int i = 0; i < 5 && server == null; i++) {
            try {
                port = 1000 + RANDOM.nextInt(64535);
                server = new OAuthWebServer(false, port);
            } catch (IOException ignored) {
            }
        }

        if (server == null) {
            status = "minetogether.oauth.status.server_failed";
            return;
        }

        final String callback = "http://localhost:" + port;

        try {
            if (openURL(new URL(authorizationUrl(callback, secretState, codeVerifier)))) {
                status = "minetogether.oauth.status.browser_opened";
            } else {
                status = "minetogether.oauth.status.browser_failed";
            }
        } catch (IOException ex) {
            status = "minetogether.oauth.status.browser_failed";
            closeServer(false);
            return;
        }

        server.setCodeHandler((code, state) -> {
            try {
                if (!secretState.equals(state)) {
                    status = "minetogether.oauth.status.state_failed";
                    closeServer(false);
                    return;
                }

                status = "minetogether.oauth.status.token";
                String accessToken = fetchAccessToken(callback, code, codeVerifier);
                HttpResult response = request("GET", PROTECTED_RESOURCE_URL, null, accessToken);
                if (response.code != 200) {
                    status = "minetogether.oauth.status.profile_failed";
                    closeServer(false);
                    return;
                }

                JsonElement parsed;
                try {
                    parsed = new JsonParser().parse(response.body);
                } catch (JsonParseException ex) {
                    status = "minetogether.oauth.status.profile_failed";
                    closeServer(false);
                    return;
                }

                if (!needsMinecraftLink(parsed)) {
                    status = "minetogether.oauth.status.complete";
                    closeServer(true);
                    return;
                }

                status = "minetogether.oauth.status.minecraft_auth";
                ServerAuthTest.auth((authed, mcAuthCode) -> {
                    if (!authed) {
                        status = "minetogether.oauth.status.minecraft_auth_failed";
                        closeServer(false);
                        return null;
                    }
                    try {
                        HttpResult linkResponse = request("POST", PROTECTED_RESOURCE_URL + "/linkmc/" + encode(mcAuthCode), "", accessToken);
                        status = linkResponse.code >= 200 && linkResponse.code < 300
                                ? "minetogether.oauth.status.complete"
                                : "minetogether.oauth.status.link_failed";
                    } catch (IOException ex) {
                        LOGGER.error("Failed to link MineTogether account to Minecraft", ex);
                        status = "minetogether.oauth.status.link_failed";
                    }
                    closeServer(true);
                    return null;
                });
            } catch (Throwable ex) {
                LOGGER.error("MineTogether OAuth failed", ex);
                status = "minetogether.oauth.status.failed";
                closeServer(false);
            }
        });
    }

    private static String authorizationUrl(String callback, String state, String codeVerifier) throws IOException {
        return BASE_URL + "auth/realms/" + encodePath(REALM) + "/protocol/openid-connect/auth"
                + "?response_type=code"
                + "&client_id=" + encode(API_KEY)
                + "&redirect_uri=" + encode(callback)
                + "&scope=" + encode("openid")
                + "&state=" + encode(state)
                + "&code_challenge=" + encode(codeChallenge(codeVerifier))
                + "&code_challenge_method=S256";
    }

    private static String fetchAccessToken(String callback, String code, String codeVerifier) throws IOException {
        String body = form(
                "grant_type", "authorization_code",
                "client_id", API_KEY,
                "client_secret", API_SECRET,
                "redirect_uri", callback,
                "code", code,
                "code_verifier", codeVerifier
        );
        HttpResult response = request("POST", BASE_URL + "auth/realms/" + encodePath(REALM) + "/protocol/openid-connect/token", body, null);
        if (response.code < 200 || response.code >= 300) {
            throw new IOException("Token endpoint returned HTTP " + response.code);
        }
        JsonElement parsed = new JsonParser().parse(response.body);
        if (!parsed.isJsonObject() || !parsed.getAsJsonObject().has("access_token")) {
            throw new IOException("Token endpoint did not return an access token");
        }
        return parsed.getAsJsonObject().getAsJsonPrimitive("access_token").getAsString();
    }

    private static HttpResult request(String method, String url, String body, String bearerToken) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Accept", "application/json");
        if (bearerToken != null && !bearerToken.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }
        if (body != null) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(bytes);
            }
        }
        int code = connection.getResponseCode();
        String responseBody;
        if (code >= 200 && code < 400) {
            responseBody = IOUtils.toString(connection.getInputStream(), StandardCharsets.UTF_8);
        } else if (connection.getErrorStream() != null) {
            responseBody = IOUtils.toString(connection.getErrorStream(), StandardCharsets.UTF_8);
        } else {
            responseBody = "";
        }
        return new HttpResult(code, responseBody);
    }

    private static String form(String... pairs) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) {
            if (builder.length() > 0) builder.append('&');
            builder.append(encode(pairs[i])).append('=').append(encode(pairs[i + 1]));
        }
        return builder.toString();
    }

    private static String randomUrlToken(int bytes) {
        byte[] random = new byte[bytes];
        RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static String codeChallenge(String verifier) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 is unavailable", ex);
        }
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String encodePath(String value) throws IOException {
        return encode(value).replace("+", "%20");
    }

    public static String getStatusKey() {
        return status == null || status.isEmpty() ? "minetogether.oauth.status.idle" : status;
    }

    public static boolean openURL(URL url) {
        String[] cmdLine;
        Util.EnumOS os = Util.getOSType();
        switch (os) {
            case WINDOWS:
                cmdLine = new String[]{"rundll32", "url.dll,FileProtocolHandler", url.toString()};
                break;
            case OSX:
                cmdLine = new String[]{"open", url.toString()};
                break;
            default:
                cmdLine = new String[]{"xdg-open", url.toString()};
                break;
        }

        try {
            Process browserProcess = AccessController.doPrivileged((PrivilegedExceptionAction<Process>) () -> Runtime.getRuntime().exec(cmdLine));
            for (String errorLine : IOUtils.readLines(browserProcess.getErrorStream(), StandardCharsets.UTF_8)) {
                LOGGER.error(errorLine);
            }
            browserProcess.getInputStream().close();
            browserProcess.getErrorStream().close();
            browserProcess.getOutputStream().close();
            return true;
        } catch (IOException | PrivilegedActionException ex) {
            LOGGER.error("Could not open OAuth URL {}", url, ex);
            return false;
        }
    }

    private static boolean needsMinecraftLink(JsonElement parsed) {
        try {
            if (!parsed.isJsonObject()) return true;
            JsonObject profile = parsed.getAsJsonObject();
            if (!profile.has("federatedIdentities")) return true;
            JsonArray identities = profile.getAsJsonArray("federatedIdentities");
            String ourId = Minecraft.getMinecraft().getSession().getProfile().getId().toString();
            for (JsonElement identity : identities) {
                if (!identity.isJsonObject()) continue;
                JsonObject object = identity.getAsJsonObject();
                if (object.has("identityProvider")
                        && "mcauth".equals(object.getAsJsonPrimitive("identityProvider").getAsString())
                        && object.has("userId")
                        && ourId.equals(object.getAsJsonPrimitive("userId").getAsString())) {
                    return false;
                }
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    private static void closeServer(final boolean refreshProfile) {
        final OAuthWebServer current = server;
        if (current == null) return;
        new Timer("MT OAuth Cleanup", true).schedule(new TimerTask() {
            @Override
            public void run() {
                current.stop();
                if (server == current) server = null;
                if (refreshProfile && MineTogetherChat.CHAT_STATE != null) {
                    MineTogetherChat.CHAT_STATE.profileManager.refreshOwnProfile();
                }
            }
        }, 1000L);
    }

    private static final class HttpResult {
        private final int code;
        private final String body;

        private HttpResult(int code, String body) {
            this.code = code;
            this.body = body == null ? "" : body;
        }
    }
}
