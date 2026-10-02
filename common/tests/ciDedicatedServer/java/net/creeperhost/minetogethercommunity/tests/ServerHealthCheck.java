package net.creeperhost.minetogethercommunity.tests;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.LongSupplier;

/** Driven by server lifecycle events; no client classes or rendering dependencies. */
public final class ServerHealthCheck {
    private final Path output;
    private final LongSupplier clock;
    private long startedAt;
    private int ticks;
    private boolean started;
    private boolean complete;

    public ServerHealthCheck() {
        this(Path.of(System.getProperty("minetogether.ci.serverOutput")), System::nanoTime);
    }

    ServerHealthCheck(Path output, LongSupplier clock) {
        this.output = output;
        this.clock = clock;
    }

    public void started() {
        startedAt = clock.getAsLong();
        started = true;
        mark("started.txt", "Dedicated server started.");
    }

    public void tick(Runnable stop) {
        if (!started || complete) return;
        ticks++;
        if (ticks >= 600 && clock.getAsLong() - startedAt >= Duration.ofSeconds(30).toNanos()) {
            complete = true;
            mark("healthy.txt", "Server completed " + ticks + " ticks and at least 30 seconds of uptime.");
            stop.run();
        }
    }

    public void stopped() {
        mark("stopped.txt", "Server stopped event received.");
    }

    private void mark(String name, String value) {
        try {
            Files.createDirectories(output);
            Files.writeString(output.resolve(name), value + "\n");
            System.out.println("Dedicated server test: " + value);
        } catch (IOException error) {
            throw new UncheckedIOException("Could not save server test progress.", error);
        }
    }
}
