package net.creeperhost.minetogethercommunity.tests;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Shared CI reporter, independent of Minecraft and the test framework. Never shipped with the mod. */
public final class DiscordReporter {
    private final URI webhook;
    private final String runContext;

    DiscordReporter(URI webhook, String runContext) {
        String url = webhook.toString();
        url = url.matches(".*[?&]wait=[^&]*.*")
                ? url.replaceAll("([?&])wait=[^&]*", "$1wait=true")
                : url + (webhook.getRawQuery() == null ? "?" : "&") + "wait=true";
        this.webhook = URI.create(url);
        this.runContext = runContext;
    }

    public static Optional<DiscordReporter> fromEnvironment() throws IOException {
        return fromEnvironment(System.getenv());
    }

    static Optional<DiscordReporter> fromEnvironment(Map<String, String> environment) throws IOException {
        String url = environment.getOrDefault("DISCORD_WEBHOOK", "").trim();
        if (url.isEmpty()) {
            if (Boolean.parseBoolean(environment.getOrDefault("GITHUB_ACTIONS", "false"))) {
                throw new IOException("Missing DISCORD_WEBHOOK Actions secret.");
            }
            return Optional.empty(); // Local tests can capture without posting.
        }
        URI uri;
        try {
            uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid DISCORD_WEBHOOK URL.");
        }
        String context = "";
        String repository = environment.get("GITHUB_REPOSITORY");
        String runId = environment.get("GITHUB_RUN_ID");
        if (repository != null && runId != null) {
            String sha = environment.getOrDefault("GITHUB_SHA", "unknown");
            context = "\n" + repository + " · " + sha.substring(0, Math.min(7, sha.length()))
                    + "\n" + environment.getOrDefault("GITHUB_SERVER_URL", "https://github.com")
                    + "/" + repository + "/actions/runs/" + runId;
        }
        return Optional.of(new DiscordReporter(uri, context));
    }

    /** Sends a summary and optional screenshots, logs, or test-result files. Call off the render thread. */
    public void send(String summary, List<Path> attachments) throws IOException {
        send(summary, attachments, new JsonArray(), true);
    }

    /** Discord's two inline fields form the check/result columns alongside the screenshot. */
    public void send(TestResults results, List<Path> attachments) throws IOException {
        JsonArray embeds = new JsonArray();
        embeds.add(table(results));
        send(results.details(), attachments, embeds, true);
    }

    public void sendSuite(List<TestResults> results, Map<String, Path> screenshots, List<Path> attachments) throws IOException {
        JsonArray embeds = new JsonArray();
        JsonObject combined = new JsonObject();
        combined.addProperty("title", "MineTogether test results");
        combined.addProperty("description", CombinedResultsTable.format(results));
        embeds.add(combined);
        for (var screenshot : screenshots.entrySet()) {
            JsonObject embed = new JsonObject();
            embed.addProperty("title", screenshot.getKey() + " screenshot");
            embed.add("image", image(screenshot.getValue().getFileName().toString()));
            embeds.add(embed);
        }
        boolean passed = results.stream().flatMap(result -> result.checks().stream()).allMatch(TestResults.Check::passed);
        send("MineTogether full test suite: " + (passed ? "✅ Passed" : "❌ Failed"), attachments, embeds, false);
    }

    private static JsonObject table(TestResults results) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", results.loader() + " test results");
        JsonArray fields = new JsonArray();
        fields.add(column("Check", results.checkNames()));
        fields.add(column("Result", results.checkStatuses()));
        embed.add("fields", fields);
        return embed;
    }

    private static JsonObject image(String name) {
        JsonObject image = new JsonObject();
        image.addProperty("url", "attachment://" + name);
        return image;
    }

    private static JsonObject column(String name, String value) {
        JsonObject column = new JsonObject();
        column.addProperty("name", name);
        column.addProperty("value", value);
        column.addProperty("inline", true);
        return column;
    }

    private void send(String summary, List<Path> attachments, JsonArray embeds, boolean automaticImage) throws IOException {
        String content = summary + runContext;
        if (content.length() > 2000 || attachments.size() > 10 || embeds.size() > 10) {
            throw new IOException("Discord reports support up to 2000 message characters and 10 attachments; put longer results in a file.");
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("content", content);
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        payload.add("allowed_mentions", mentions);
        JsonArray files = new JsonArray();
        var names = new HashSet<String>();
        for (int i = 0; i < attachments.size(); i++) {
            String name = attachments.get(i).getFileName().toString();
            if (!name.matches("[A-Za-z0-9._-]+") || !names.add(name)) {
                throw new IOException("Report attachment names must be unique and use letters, numbers, dots, underscores, or hyphens.");
            }
            JsonObject file = new JsonObject();
            file.addProperty("id", i);
            file.addProperty("filename", name);
            files.add(file);
            if (automaticImage && name.endsWith(".png")) {
                if (embeds.isEmpty()) embeds.add(new JsonObject());
                JsonObject first = embeds.get(0).getAsJsonObject();
                if (!first.has("image")) first.add("image", image(name));
            }
        }
        if (!embeds.isEmpty()) {
            payload.add("embeds", embeds);
        }
        payload.add("attachments", files);

        String boundary = UUID.randomUUID().toString();
        List<HttpRequest.BodyPublisher> parts = new ArrayList<>();
        parts.add(text("--" + boundary + "\r\nContent-Disposition: form-data; name=\"payload_json\"\r\n"
                + "Content-Type: application/json\r\n\r\n" + payload + "\r\n"));
        for (int i = 0; i < attachments.size(); i++) {
            Path file = attachments.get(i);
            String type = Files.probeContentType(file);
            parts.add(text("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files[" + i
                    + "]\"; filename=\"" + file.getFileName() + "\"\r\nContent-Type: "
                    + (type == null ? "application/octet-stream" : type) + "\r\n\r\n"));
            parts.add(HttpRequest.BodyPublishers.ofFile(file));
            parts.add(text("\r\n"));
        }
        parts.add(text("--" + boundary + "--\r\n"));
        HttpRequest request = HttpRequest.newBuilder(webhook)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("User-Agent", "MineTogether-CI")
                .POST(HttpRequest.BodyPublishers.concat(parts.toArray(HttpRequest.BodyPublisher[]::new)))
                .build();
        int status;
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Discord report interrupted.");
        } catch (IOException | RuntimeException error) {
            // HTTP exceptions can contain the secret webhook URL. Do not propagate them or their causes.
            throw new IOException("Could not deliver the Discord report.");
        }
        if (status != 200) throw new IOException("Discord report failed: HTTP " + status + ".");
    }

    private static HttpRequest.BodyPublisher text(String value) {
        return HttpRequest.BodyPublishers.ofString(value, StandardCharsets.UTF_8);
    }
}
