package net.creeperhost.minetogethercommunity.util;

import com.google.gson.Gson;
import net.covers1624.quack.net.httpapi.EngineRequest;
import net.covers1624.quack.net.httpapi.EngineResponse;
import net.covers1624.quack.net.httpapi.HeaderList;
import net.covers1624.quack.net.httpapi.WebBody;
import net.creeperhost.minetogether.lib.web.WebConstants;
import net.creeperhost.minetogethercommunity.MineTogether;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Status-aware lookup for CreeperHost's optional Modrinth mapping. */
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
        if (!StringUtils.isBlank(packIdentity)) request.header("Identifier", packIdentity);

        try (EngineResponse response = request.execute()) {
            int statusCode = response.statusCode();
            if (statusCode == 404) return new Result(statusCode, "", "");
            WebBody body = response.body();
            if (body == null) return new Result(statusCode, "", "");
            if (!StringUtils.startsWith(body.contentType(), WebConstants.JSON)) {
                if (statusCode < 200 || statusCode >= 300) return new Result(statusCode, "", "");
                throw new IOException("Modrinth lookup returned unexpected content type '" + body.contentType() + "'");
            }
            try (InputStream input = body.open()) {
                try {
                    Payload payload = GSON.fromJson(readUtf8(input), Payload.class);
                    return payload == null ? new Result(statusCode, "", "") : new Result(statusCode, payload.id, payload.name);
                } catch (RuntimeException ex) {
                    throw new IOException("Modrinth lookup returned malformed JSON", ex);
                }
            }
        }
    }

    static String urlFor(String identifier) throws IOException {
        String encoded = URLEncoder.encode(StringUtils.stripToEmpty(identifier), "UTF-8").replace("+", "%20");
        return WebConstants.CH + "json/modpacks/modrinth/" + encoded;
    }

    private static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    public static final class Result {
        private final int statusCode;
        private final String id;
        private final String name;

        public Result(int statusCode, String id, String name) {
            this.statusCode = statusCode;
            this.id = StringUtils.stripToEmpty(id);
            this.name = StringUtils.stripToEmpty(name);
        }

        public int getStatusCode() { return statusCode; }
        public String getId() { return id; }
        public String getName() { return name; }
        public boolean isSuccessful() { return statusCode >= 200 && statusCode < 300; }
        public boolean isNotFound() { return statusCode == 404; }
    }

    private static final class Payload {
        String id = "";
        String name = "";
    }
}
