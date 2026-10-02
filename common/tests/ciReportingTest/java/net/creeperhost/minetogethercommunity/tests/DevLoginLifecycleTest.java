package net.creeperhost.minetogethercommunity.tests;

import com.google.gson.JsonParser;
import net.covers1624.devlogin.DevLogin;
import net.covers1624.devlogin.http.HttpEngine;
import net.covers1624.devlogin.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real DevLogin cache/refresh/re-auth logic with an entirely local HTTP substitute. */
public class DevLoginLifecycleTest {
    @TempDir Path storage;
    private static List<String> launchedArgs;

    @Test
    void coldLoginCachedLoginRefreshAndRevokedGrant() throws Throwable {
        PrintStream original = System.out;
        String oldStorage = System.getProperty("devlogin.storage");
        String oldEngine = System.getProperty("devlogin.http_engine");
        var messages = new ArrayList<String>();
        var log = new ByteArrayOutputStream();
        try {
            System.setProperty("devlogin.storage", storage.toString());
            System.setProperty("devlogin.http_engine", FakeEngine.class.getName());
            System.setOut(new DiscordDevLogin.LoginConsole(new PrintStream(log), messages::add));

            login();
            assertEquals(1, messages.size());
            assertTrue(messages.getFirst().contains("**CODE00001**"));
            assertEquals("fixture-minecraft-token", launchedArgs.get(launchedArgs.indexOf("--accessToken") + 1));
            assertTrue(Files.exists(storage.resolve("accounts.json")));

            login(); // Cached credentials must not prompt again.
            assertEquals(1, messages.size());
            expireTokens();
            login(); // A valid refresh grant must silently renew credentials.
            assertEquals(1, messages.size());
            assertEquals(1, FakeEngine.refreshes);

            expireTokens();
            FakeEngine.revoke = true;
            login(); // Real DevLogin invalid_grant handling must request a replacement code.
            assertEquals(2, messages.size());
            assertTrue(messages.getLast().contains("**CODE00002**"));
            assertEquals(2, FakeEngine.refreshes);
            assertFalse(log.toString().contains("fixture-minecraft-token"));
            assertFalse(log.toString().contains("fixture-refresh-token"));
            assertFalse(log.toString().contains("CODE0000"));
        } finally {
            System.setOut(original);
            restore("devlogin.storage", oldStorage);
            restore("devlogin.http_engine", oldEngine);
        }
    }

    private void expireTokens() throws Exception {
        Path file = storage.resolve("accounts.json");
        var accounts = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        var account = accounts.getAsJsonObject("ci-fixture");
        account.getAsJsonObject("msTokens").addProperty("expiresAt", 0);
        account.getAsJsonObject("mcTokens").addProperty("expiresAt", 0);
        Files.writeString(file, accounts.toString());
    }

    private static void login() throws Throwable {
        launchedArgs = null;
        DevLogin.main(new String[]{"--launch_profile", "ci-fixture", "--launch_target", LaunchTarget.class.getName()});
        assertNotNull(launchedArgs);
    }

    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }

    public static class LaunchTarget {
        public static void main(String[] args) {
            launchedArgs = List.of(args);
        }
    }

    public static class FakeEngine extends HttpEngine {
        static boolean revoke;
        static int refreshes;
        private static int codes;

        @Override
        protected HttpResponse makeRequest(String method, String url, byte[] body, Map<String, String> headers) {
            if (url.endsWith("/devicecode")) {
                String code = "CODE0000" + ++codes;
                return response(200, """
                        {"device_code":"fixture-device-token","user_code":"%s","expires_in":60,"interval":0,
                        "message":"To sign in, use a web browser to open the page https://www.microsoft.com/link and enter the code %s to authenticate."}
                        """.formatted(code, code));
            }
            if (url.endsWith("/token")) {
                if (new String(body, StandardCharsets.UTF_8).contains("grant_type=refresh_token")) {
                    refreshes++;
                    if (revoke) return response(400, "{\"error\":\"invalid_grant\",\"error_description\":\"Revoked fixture\"}");
                }
                return response(200, "{\"access_token\":\"fixture-ms-token\",\"refresh_token\":\"fixture-refresh-token\",\"expires_in\":3600}");
            }
            if (url.startsWith("https://user.auth.xboxlive.com/") || url.startsWith("https://xsts.auth.xboxlive.com/")) {
                return response(200, "{\"Token\":\"fixture-xbox-token\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"fixture\"}]}}");
            }
            if (url.endsWith("/authentication/login_with_xbox")) {
                return response(200, "{\"access_token\":\"fixture-minecraft-token\",\"expires_in\":3600}");
            }
            if (url.endsWith("/minecraft/profile")) {
                return response(200, "{\"id\":\"12345678123442348234123456789012\",\"name\":\"FixturePlayer\"}");
            }
            throw new AssertionError("Unexpected DevLogin request: " + method + " " + url);
        }

        private static HttpResponse response(int status, String json) {
            return new HttpResponse(status, "fixture", json.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void shutdown() {}
    }
}
