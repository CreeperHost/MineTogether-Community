package net.creeperhost.minetogethercommunity.tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Runs in a separate JVM after the server exits, including crashes and task timeouts. */
public final class ServerTestReport {
    public static void main(String[] args) throws IOException {
        Path output = Path.of(args[0]);
        TestResults results = evaluate(output, args[1], Boolean.parseBoolean(args[2]));
        boolean passed = results.checks().stream().allMatch(TestResults.Check::passed);
        Files.createDirectories(output);
        Path result = output.resolve(passed ? "success.txt" : "failure.txt");
        Files.writeString(result, results.asText());
        results.write(output.resolve("results.json"));
        System.out.print(results.asText());
        var reporter = DiscordReporter.fromEnvironment();
        if (reporter.isPresent() && !Boolean.parseBoolean(System.getenv("MT_CI_DEFER_REPORTS"))) {
            reporter.get().send(results, List.of(result));
            Files.writeString(output.resolve("discord-sent.txt"), "Discord report delivered.\n");
        }
        if (!passed) throw new IllegalStateException("Dedicated server test failed; see the result table and server logs.");
    }

    static TestResults evaluate(Path output, String loader, boolean processSucceeded) throws IOException {
        boolean crash = false;
        Path crashes = output.resolve("run/crash-reports");
        if (Files.isDirectory(crashes)) {
            try (var files = Files.list(crashes)) {
                crash = files.anyMatch(Files::isRegularFile);
            }
        }
        Path log = output.resolve("run/logs/latest.log");
        if (Files.exists(log)) {
            String text = Files.readString(log);
            crash |= text.contains("Encountered an unexpected exception")
                    || text.contains("Exception stopping the server")
                    || text.contains("Failed to start the minecraft server");
        }
        boolean started = Files.exists(output.resolve("started.txt"));
        boolean healthy = Files.exists(output.resolve("healthy.txt"));
        boolean cleanShutdown = processSucceeded && Files.exists(output.resolve("stopped.txt")) && !crash;
        return new TestResults(loader + " dedicated server", List.of(
                new TestResults.Check("Server started", started),
                new TestResults.Check("600 ticks / 30s uptime", healthy),
                new TestResults.Check("Clean shutdown", cleanShutdown),
                new TestResults.Check("No crash detected", started && processSucceeded && !crash)),
                started && healthy && cleanShutdown
                        ? "Dedicated server startup, ticking, and shutdown passed."
                        : "Dedicated server check failed. See the failed checks and workflow diagnostics.");
    }
}
