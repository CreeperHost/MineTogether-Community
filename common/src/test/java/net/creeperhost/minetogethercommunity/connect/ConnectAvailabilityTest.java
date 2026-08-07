package net.creeperhost.minetogethercommunity.connect;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectAvailabilityTest {

    @Test
    void unavailableDiscoveryBacksOffWithoutRepeatingTheSameError() {
        ConnectAvailability availability = new ConnectAvailability();

        assertTrue(availability.shouldAttempt(1_000));
        assertTrue(availability.failed(1_000, new IOException("offline")));
        assertFalse(availability.isAvailable());
        assertFalse(availability.shouldAttempt(1_000 + ConnectAvailability.RETRY_DELAY_MILLIS - 1));
        assertFalse(availability.failed(2_000, new IOException("offline")));
        assertTrue(availability.shouldAttempt(2_000 + ConnectAvailability.RETRY_DELAY_MILLIS));
    }

    @Test
    void successfulRetryRestoresAvailabilityAndClearsBackoff() {
        ConnectAvailability availability = new ConnectAvailability();
        availability.failed(1_000, new IOException("offline"));

        availability.succeeded();

        assertTrue(availability.isAvailable());
        assertTrue(availability.shouldAttempt(1_001));
        assertTrue(availability.failed(2_000, new IOException("offline")));
    }
}
