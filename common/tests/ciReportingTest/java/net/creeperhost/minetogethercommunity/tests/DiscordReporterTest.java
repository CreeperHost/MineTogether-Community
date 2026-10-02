package net.creeperhost.minetogethercommunity.tests;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DiscordReporterTest {
    @TempDir Path directory;
    private HttpServer server;
    private volatile String body;
    private volatile String query;
    private int responseStatus = 200;
    private volatile int requests;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook/PRIVATE_TOKEN", exchange -> {
            requests++;
            body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            query = exchange.getRequestURI().getQuery();
            exchange.sendResponseHeaders(responseStatus, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private DiscordReporter reporter() {
        return new DiscordReporter(URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                + "/webhook/PRIVATE_TOKEN?thread_id=123&wait=false"), "\nRun context");
    }

    private JsonObject payload() {
        int start = body.indexOf("\r\n\r\n") + 4;
        return JsonParser.parseString(body.substring(start, body.indexOf("\r\n--", start))).getAsJsonObject();
    }

    @Test
    void sendsScreenshotAndResultsInOneReport() throws IOException {
        Path image = Files.writeString(directory.resolve("title.png"), "image-bytes");
        Path results = Files.writeString(directory.resolve("results.json"), "{\"passed\":5}");
        reporter().send("Five tests passed", List.of(image, results));

        assertEquals("thread_id=123&wait=true", query);
        JsonObject payload = payload();
        assertEquals("Five tests passed\nRun context", payload.get("content").getAsString());
        assertEquals(0, payload.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size());
        assertEquals(2, payload.getAsJsonArray("attachments").size());
        assertEquals("attachment://title.png", payload.getAsJsonArray("embeds").get(0)
                .getAsJsonObject().getAsJsonObject("image").get("url").getAsString());
        assertTrue(body.contains("name=\"files[0]\"; filename=\"title.png\""));
        assertTrue(body.contains("name=\"files[1]\"; filename=\"results.json\""));
        assertTrue(body.contains("image-bytes"));
        assertTrue(body.contains("{\"passed\":5}"));
        assertFalse(body.contains("PRIVATE_TOKEN"));
    }

    @Test
    void combinedSuiteSendsAllTablesAndLoaderScreenshotsInOneRequest() throws IOException {
        var tables = new java.util.ArrayList<TestResults>();
        var screenshots = new java.util.LinkedHashMap<String, Path>();
        for (String loader : List.of("NeoForge", "Fabric")) {
            tables.add(new TestResults(loader + " dedicated server",
                    List.of(new TestResults.Check("Server started", true)), "Server passed."));
            tables.add(new TestResults(loader, List.of(new TestResults.Check("Friends visible", true)), "Client passed."));
            screenshots.put(loader, Files.writeString(directory.resolve(loader + "-friends.png"), "image"));
        }
        tables.add(new TestResults("Suite execution", List.of(new TestResults.Check("Unit tests", false)), "A unit test failed."));
        reporter().sendSuite(tables, screenshots, List.copyOf(screenshots.values()));

        assertEquals(1, requests);
        assertTrue(payload().get("content").getAsString().contains("❌ Failed"));
        var embeds = payload().getAsJsonArray("embeds");
        assertEquals(3, embeds.size());
        assertFalse(embeds.get(0).getAsJsonObject().has("image"));
        String table = embeds.get(0).getAsJsonObject().get("description").getAsString();
        assertTrue(table.contains("NeoForge  Fabric"));
        assertEquals(1, table.lines().filter(line -> line.startsWith("Server started")).count());
        assertEquals(1, table.lines().filter(line -> line.startsWith("Friends visible")).count());
        assertTrue(table.lines().filter(line -> line.startsWith("Unit tests")).findFirst().orElseThrow().contains("❌"));
        assertEquals("attachment://NeoForge-friends.png", embeds.get(1).getAsJsonObject().getAsJsonObject("image").get("url").getAsString());
        assertEquals("attachment://Fabric-friends.png", embeds.get(2).getAsJsonObject().getAsJsonObject("image").get("url").getAsString());
    }

    @Test
    void combinedTableKeepsFailuresAndAbsentChecksDistinct() {
        String table = CombinedResultsTable.format(List.of(
                new TestResults("NeoForge", List.of(new TestResults.Check("IRC connected", false)), ""),
                new TestResults("Fabric", List.of(new TestResults.Check("IRC connected", true),
                        new TestResults.Check("Friends visible", true)), "")));
        String irc = table.lines().filter(line -> line.startsWith("IRC connected")).findFirst().orElseThrow();
        assertTrue(irc.indexOf("❌") < irc.indexOf("✅"));
        String friends = table.lines().filter(line -> line.startsWith("Friends visible")).findFirst().orElseThrow();
        assertTrue(friends.indexOf("—") < friends.indexOf("✅"));
    }

    @Test
    void savedResultsPreserveFailuresAndMissingResultsCannotPass() throws IOException {
        Path file = directory.resolve("results.json");
        var missing = SuiteReport.readResult(file, "Fabric");
        assertFalse(missing.checks().getFirst().passed());
        var expected = new TestResults("Fabric", List.of(new TestResults.Check("IRC connected", false)), "Timed out.");
        expected.write(file);
        assertEquals(expected, SuiteReport.readResult(file, "Fabric"));
        Files.writeString(file, "{}");
        assertFalse(SuiteReport.readResult(file, "Fabric").checks().getFirst().passed());
    }

    @Test
    void sendsLoaderTablesWithMixedResultsAndScreenshot() throws IOException {
        Path image = Files.writeString(directory.resolve("title.png"), "image-bytes");
        for (String loader : List.of("NeoForge", "Fabric")) {
            var results = new TestResults(loader, List.of(
                    new TestResults.Check("Game started", true),
                    new TestResults.Check("IRC connected", false)), "IRC verification timed out.");
            reporter().send(results, List.of(image));

            JsonObject embed = payload().getAsJsonArray("embeds").get(0).getAsJsonObject();
            assertEquals(loader + " test results", embed.get("title").getAsString());
            var fields = embed.getAsJsonArray("fields");
            assertEquals(2, fields.size());
            assertEquals("Check", fields.get(0).getAsJsonObject().get("name").getAsString());
            assertEquals("Game started\nIRC connected", fields.get(0).getAsJsonObject().get("value").getAsString());
            assertEquals("Result", fields.get(1).getAsJsonObject().get("name").getAsString());
            assertEquals("✅\n❌", fields.get(1).getAsJsonObject().get("value").getAsString());
            assertTrue(fields.get(0).getAsJsonObject().get("inline").getAsBoolean());
            assertTrue(fields.get(1).getAsJsonObject().get("inline").getAsBoolean());
            assertEquals("attachment://title.png", embed.getAsJsonObject("image").get("url").getAsString());
        }
    }

    @Test
    void sendsTableWhenScreenshotIsUnavailable() throws IOException {
        var results = new TestResults("Fabric", List.of(
                new TestResults.Check("Game started", true),
                new TestResults.Check("Screenshot captured", false)), "Screenshot capture failed.");
        reporter().send(results, List.of());

        JsonObject embed = payload().getAsJsonArray("embeds").get(0).getAsJsonObject();
        assertFalse(embed.has("image"));
        assertEquals("✅\n❌", embed.getAsJsonArray("fields").get(1).getAsJsonObject().get("value").getAsString());
        assertTrue(results.asText().contains("Screenshot captured | ❌"));
        assertFalse(results.asText().contains("IRC connected"));
        assertFalse(results.asText().contains("Minecraft authenticated"));
    }

    @Test
    void supportsReportsWithoutAttachments() throws IOException {
        reporter().send("A test failed: \"expected ready\"\nActual: unavailable", List.of());
        assertEquals("A test failed: \"expected ready\"\nActual: unavailable\nRun context",
                payload().get("content").getAsString());
        assertEquals(0, payload().getAsJsonArray("attachments").size());
        assertFalse(payload().has("embeds"));
    }

    @Test
    void rejectedUploadsFailWithoutExposingWebhook() {
        responseStatus = 403;
        IOException error = assertThrows(IOException.class, () -> reporter().send("Result", List.of()));
        assertEquals("Discord report failed: HTTP 403.", error.getMessage());
        assertNull(error.getCause());
    }

    @Test
    void connectionFailuresDoNotExposeWebhook() {
        DiscordReporter reporter = reporter();
        server.stop(0);
        IOException error = assertThrows(IOException.class, () -> reporter.send("Result", List.of()));
        assertEquals("Could not deliver the Discord report.", error.getMessage());
        assertNull(error.getCause());
    }

    @Test
    void missingSecretIsOptionalLocallyButRequiredInActions() throws IOException {
        assertTrue(DiscordReporter.fromEnvironment(Map.of()).isEmpty());
        IOException error = assertThrows(IOException.class,
                () -> DiscordReporter.fromEnvironment(Map.of("GITHUB_ACTIONS", "true")));
        assertEquals("Missing DISCORD_WEBHOOK Actions secret.", error.getMessage());
        assertTrue(DiscordReporter.fromEnvironment(Map.of("DISCORD_WEBHOOK",
                "https://discord.com/api/webhooks/123/PRIVATE_TOKEN")).isPresent());
    }

    @Test
    void invalidWebhookIsReportedWithoutItsValue() {
        IOException error = assertThrows(IOException.class,
                () -> DiscordReporter.fromEnvironment(Map.of("DISCORD_WEBHOOK", "PRIVATE_TOKEN invalid")));
        assertEquals("Invalid DISCORD_WEBHOOK URL.", error.getMessage());
        assertNull(error.getCause());
    }

    @Test
    void duplicateAttachmentsAreRejectedBeforePosting() throws IOException {
        Path file = Files.writeString(directory.resolve("result.txt"), "passed");
        assertThrows(IOException.class, () -> reporter().send("Result", List.of(file, file)));
        assertNull(body);
    }
}
