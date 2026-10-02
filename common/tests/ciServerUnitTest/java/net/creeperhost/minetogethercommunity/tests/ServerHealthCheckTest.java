package net.creeperhost.minetogethercommunity.tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ServerHealthCheckTest {
    @TempDir Path output;

    @Test
    void needsBothCompletedTicksAndElapsedTimeAndStopsOnlyOnce() {
        var clock = new AtomicLong();
        var stops = new AtomicInteger();
        var check = new ServerHealthCheck(output, clock::get);
        check.tick(stops::incrementAndGet); // No progress before SERVER_STARTED.
        check.started();
        for (int i = 0; i < 600; i++) check.tick(stops::incrementAndGet);
        assertEquals(0, stops.get());
        assertFalse(Files.exists(output.resolve("healthy.txt")));
        clock.set(Duration.ofSeconds(30).toNanos());
        check.tick(stops::incrementAndGet);
        check.tick(stops::incrementAndGet);
        assertEquals(1, stops.get());
        assertTrue(Files.exists(output.resolve("healthy.txt")));
        assertFalse(Files.exists(output.resolve("stopped.txt")));
        check.stopped();
        assertTrue(Files.exists(output.resolve("stopped.txt")));
    }

    @Test
    void elapsedTimeAloneCannotPassAHungServer() {
        var clock = new AtomicLong();
        var stops = new AtomicInteger();
        var check = new ServerHealthCheck(output, clock::get);
        check.started();
        clock.set(Duration.ofMinutes(2).toNanos());
        for (int i = 0; i < 599; i++) check.tick(stops::incrementAndGet);
        assertEquals(0, stops.get());
        assertFalse(Files.exists(output.resolve("healthy.txt")));
        check.tick(stops::incrementAndGet);
        assertEquals(1, stops.get());
    }
}
