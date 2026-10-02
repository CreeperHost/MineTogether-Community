package net.creeperhost.minetogethercommunity.tests;

import java.util.List;
import java.util.stream.Collectors;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** An ordered set of observed checks, shared by both client loaders and future test scenarios. */
public record TestResults(String loader, List<Check> checks, String details) {
    public TestResults {
        checks = List.copyOf(checks);
    }

    public record Check(String name, boolean passed) {
        public String status() {
            return passed ? "✅" : "❌";
        }
    }

    public String checkNames() {
        return checks.stream().map(Check::name).collect(Collectors.joining("\n"));
    }

    public String checkStatuses() {
        return checks.stream().map(Check::status).collect(Collectors.joining("\n"));
    }

    public String asText() {
        return loader + " test results\n\nCheck | Result\n--- | ---\n"
                + checks.stream().map(check -> check.name() + " | " + check.status())
                        .collect(Collectors.joining("\n"))
                + "\n\n" + details + "\n";
    }

    public void write(Path file) throws IOException {
        Files.writeString(file, new Gson().toJson(this));
    }

    public static TestResults read(Path file) throws IOException {
        return new Gson().fromJson(Files.readString(file), TestResults.class);
    }
}
