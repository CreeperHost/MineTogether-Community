package net.creeperhost.minetogethercommunity.tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Collects every phase after all selected loaders finish, including missing/crashed runs. */
public final class SuiteReport {
    public static void main(String[] args) throws IOException {
        Path build = Path.of(args[0]);
        Path output = build.resolve("test-suite");
        Files.createDirectories(output);
        var tables = new ArrayList<TestResults>();
        var images = new LinkedHashMap<String, Path>();
        var attachments = new ArrayList<Path>();
        var loaders = args[1].equals("all") ? List.of("neoforge", "fabric") : List.of(args[1]);
        for (String loader : loaders) {
            String title = loader.equals("neoforge") ? "NeoForge" : "Fabric";
            for (String kind : List.of("dedicated-server", "title-screen")) {
                Path directory = build.resolve(kind).resolve(loader);
                String tableTitle = title + (kind.equals("dedicated-server") ? " dedicated server" : "");
                tables.add(readResult(directory.resolve("results.json"), tableTitle));
                if (kind.equals("title-screen")) {
                    for (String name : List.of("friends-screen.png", "chat-screen.png", "failure.png", "title-screen.png")) {
                        Path source = directory.resolve(name);
                        if (!Files.isRegularFile(source)) continue;
                        Path copy = output.resolve(loader + "-" + name);
                        Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
                        attachments.add(copy);
                        if (name.equals("friends-screen.png")) images.put(tableTitle + " friends", copy);
                        else if (name.equals("chat-screen.png")) images.put(tableTitle + " chat", copy);
                        else if (name.equals("failure.png")) images.put(tableTitle + " failure", copy);
                        else if (images.keySet().stream().noneMatch(key -> key.startsWith(tableTitle + " "))) {
                            images.put(tableTitle, copy);
                        }
                    }
                }
            }
        }
        Path phases = output.resolve("phases.tsv");
        var checks = new ArrayList<TestResults.Check>();
        if (Files.exists(phases)) {
            for (String line : Files.readAllLines(phases)) {
                String[] values = line.split("\t", 2);
                if (values.length == 2) checks.add(new TestResults.Check(values[0], Boolean.parseBoolean(values[1])));
            }
        }
        int expected = 1 + loaders.size() * 2;
        if (checks.size() != expected) checks.add(new TestResults.Check("All test groups completed", false));
        tables.add(new TestResults("Suite execution", checks, "Includes supporting unit tests and all selected runtime checks."));
        Path summary = output.resolve("results.txt");
        Files.writeString(summary, tables.stream().map(TestResults::asText).collect(java.util.stream.Collectors.joining("\n")));
        attachments.add(summary);
        var reporter = DiscordReporter.fromEnvironment();
        if (reporter.isPresent()) {
            reporter.get().sendSuite(tables, images, attachments);
            Files.writeString(output.resolve("discord-sent.txt"), "Combined Discord report delivered.\n");
            System.out.println("Combined test results delivered to Discord.");
        }
        if (tables.stream().flatMap(result -> result.checks().stream()).anyMatch(check -> !check.passed())) {
            throw new IllegalStateException("One or more test groups failed; see the combined report.");
        }
    }

    static TestResults readResult(Path file, String title) {
        try {
            TestResults result = TestResults.read(file);
            if (result == null || result.checks().isEmpty()) throw new IOException("Empty result");
            return new TestResults(title, result.checks(), result.details());
        } catch (Exception error) {
            return new TestResults(title, List.of(new TestResults.Check("Test results available", false)),
                    "This test did not produce a valid result. See the workflow diagnostics.");
        }
    }
}
