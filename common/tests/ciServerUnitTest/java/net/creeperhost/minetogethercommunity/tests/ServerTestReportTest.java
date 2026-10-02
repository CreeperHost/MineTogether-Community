package net.creeperhost.minetogethercommunity.tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ServerTestReportTest {
    @TempDir Path output;

    private void completeLifecycle() throws IOException {
        for (String file : new String[]{"started.txt", "healthy.txt", "stopped.txt"}) {
            Files.writeString(output.resolve(file), "observed");
        }
    }

    @Test
    void passesCompletedLifecycleAndSuccessfulProcess() throws IOException {
        completeLifecycle();
        var result = ServerTestReport.evaluate(output, "Fabric", true);
        assertEquals("Fabric dedicated server", result.loader());
        assertTrue(result.checks().stream().allMatch(TestResults.Check::passed));
    }

    @Test
    void startupFailureDoesNotClaimNoCrash() throws IOException {
        var result = ServerTestReport.evaluate(output, "NeoForge", false);
        assertTrue(result.checks().stream().noneMatch(TestResults.Check::passed));
    }

    @Test
    void nonzeroExitOverridesSuccessfulLifecycleEvents() throws IOException {
        completeLifecycle();
        var result = ServerTestReport.evaluate(output, "Fabric", false);
        assertFalse(result.checks().get(2).passed());
        assertFalse(result.checks().get(3).passed());
    }

    @Test
    void crashReportOverridesZeroExitAndStoppedEvent() throws IOException {
        completeLifecycle();
        Path crashes = Files.createDirectories(output.resolve("run/crash-reports"));
        Files.writeString(crashes.resolve("crash.txt"), "crash");
        var result = ServerTestReport.evaluate(output, "NeoForge", true);
        assertFalse(result.checks().get(2).passed());
        assertFalse(result.checks().get(3).passed());
    }

    @Test
    void shutdownExceptionInLogCannotPass() throws IOException {
        completeLifecycle();
        Path logs = Files.createDirectories(output.resolve("run/logs"));
        Files.writeString(logs.resolve("latest.log"), "Exception stopping the server");
        var result = ServerTestReport.evaluate(output, "Fabric", true);
        assertFalse(result.checks().get(2).passed());
        assertFalse(result.checks().get(3).passed());
    }
}
