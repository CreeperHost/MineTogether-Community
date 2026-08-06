package net.creeperhost.minetogethercommunity.util;

import com.google.gson.Gson;
import net.covers1624.quack.net.httpapi.EngineRequest;
import net.covers1624.quack.net.httpapi.EngineResponse;
import net.covers1624.quack.net.httpapi.HeaderList;
import net.covers1624.quack.net.httpapi.WebBody;
import net.creeperhost.minetogether.lib.web.WebConstants;
import net.creeperhost.minetogethercommunity.MineTogether;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Status-aware lookup for the optional CreeperHost Modrinth mapping.
 *
 * The generic API client validates content type before callers can inspect the
 * HTTP status. This lookup intentionally checks status first so an HTML or
 * body-less 404 can be treated as a normal, unmapped pack.
 */
public final class ModrinthPackLookup {
    private static final Gson GSON = new Gson();

    private ModrinthPackLookup() {
    }

    public static Result lookup(String identifier) throws IOException {
        return lookup(identifier, null);
    }

    public static Result lookup(String identifier, String explicitPackIdentity) throws IOException {
        EngineRequest request = MineTogether.WEB_ENGINE.newRequest();
        request.method("GET", null);
        request.url(urlFor(identifier));
        request.header("Fingerprint", MineTogether.FINGERPRINT);

        HeaderList authHeaders = MineTogether.AUTH.getAuthHeaders();
        String packIdentity = StringUtils.firstNonBlank(explicitPackIdentity, authHeaders.get("Identifier"));
        if (!StringUtils.isBlank(packIdentity)) {
            request.header("Identifier", packIdentity);
        }

        try (EngineResponse response = request.execute()) {
            int statusCode = response.statusCode();
            if (statusCode == 404) {
                return new Result(statusCode, "", "");
            }

            WebBody body = response.body();
            if (body == null) {
                return new Result(statusCode, "", "");
            }
            if (!StringUtils.startsWith(body.contentType(), WebConstants.JSON)) {
                if (statusCode < 200 || statusCode >= 300) {
                    return new Result(statusCode, "", "");
                }
                throw new IOException("Modrinth lookup returned unexpected content type '" + body.contentType() + "'");
            }

            try (InputStream input = body.open()) {
                try {
                    Payload payload = GSON.fromJson(new String(input.readAllBytes(), StandardCharsets.UTF_8), Payload.class);
                    if (payload == null) return new Result(statusCode, "", "");
                    return new Result(statusCode, payload.id, payload.name);
                } catch (RuntimeException ex) {
                    throw new IOException("Modrinth lookup returned malformed JSON", ex);
                }
            }
        }
    }

    static String urlFor(String identifier) {
        String encoded = URLEncoder.encode(StringUtils.stripToEmpty(identifier), StandardCharsets.UTF_8).replace("+", "%20");
        return WebConstants.CH + "json/modpacks/modrinth/" + encoded;
    }

    public record Result(int statusCode, String id, String name) {
        public Result {
            id = StringUtils.stripToEmpty(id);
            name = StringUtils.stripToEmpty(name);
        }

        public boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }

        public boolean isNotFound() {
            return statusCode == 404;
        }
    }

    private static class Payload {
        public String id = "";
        public String name = "";
    }
}
