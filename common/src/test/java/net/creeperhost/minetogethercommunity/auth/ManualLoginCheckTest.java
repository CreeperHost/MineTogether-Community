package net.creeperhost.minetogethercommunity.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static net.creeperhost.minetogethercommunity.auth.ManualLoginCheck.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class ManualLoginCheckTest {
    @Test
    void absentSimulationFlagUsesNormalAuthentication() {
        withSimulationProperty(null, ManualLoginCheckTest::assertNormalAuthentication);
    }

    @Test
    void explicitlyFalseSimulationFlagUsesNormalAuthentication() {
        withSimulationProperty("false", ManualLoginCheckTest::assertNormalAuthentication);
    }

    @Test
    void disablingSimulationRestoresNormalAuthentication() {
        withSimulationProperty("true", () -> {
            assertTrue(ManualLoginCheck.isSimulationEnabled());
            var simulated = ManualLoginCheck.check(false, ManualLoginCheck.isSimulationEnabled(),
                    unexpectedTokenRequest()).join();
            assertEquals(MANUAL_LOGIN_AVAILABLE, simulated.outcome());

            System.setProperty(ManualLoginCheck.SIMULATE_FAILURE_PROPERTY, "false");
            assertNormalAuthentication();
        });
    }

    private static void assertNormalAuthentication() {
        assertFalse(ManualLoginCheck.isSimulationEnabled());
        AtomicInteger tokenRequests = new AtomicInteger();
        CompletableFuture<Object> tokenRequest = new CompletableFuture<>();
        var check = ManualLoginCheck.check(false, ManualLoginCheck.isSimulationEnabled(), () -> {
            tokenRequests.incrementAndGet();
            return tokenRequest;
        });

        assertEquals(1, tokenRequests.get());
        assertFalse(check.isDone(), "Normal authentication must wait for the session response");
        tokenRequest.complete(new Object());
        var result = check.join();
        assertEquals(AUTHENTICATED, result.outcome());
        assertNull(result.error());
        assertEquals(1, tokenRequests.get());
    }

    private static void withSimulationProperty(String value, Runnable test) {
        String previous = System.getProperty(ManualLoginCheck.SIMULATE_FAILURE_PROPERTY);
        try {
            if (value == null) System.clearProperty(ManualLoginCheck.SIMULATE_FAILURE_PROPERTY);
            else System.setProperty(ManualLoginCheck.SIMULATE_FAILURE_PROPERTY, value);
            test.run();
        } finally {
            if (previous == null) System.clearProperty(ManualLoginCheck.SIMULATE_FAILURE_PROPERTY);
            else System.setProperty(ManualLoginCheck.SIMULATE_FAILURE_PROPERTY, previous);
        }
    }

    @Test
    void successfulAuthenticationDoesNotOfferManualLogin() {
        var result = ManualLoginCheck.check(false, false,
                () -> CompletableFuture.completedFuture(new Object())).join();

        assertEquals(AUTHENTICATED, result.outcome());
        assertNull(result.error());
    }

    @Test
    void failedAuthenticationOffersManualLoginAndPreservesTheCause() {
        IOException failure = new IOException("Authentication service unavailable");
        var result = ManualLoginCheck.check(false, false,
                () -> CompletableFuture.failedFuture(failure)).join();

        assertEquals(MANUAL_LOGIN_AVAILABLE, result.outcome());
        assertSame(failure, result.error());
    }

    @Test
    void missingTokenAlsoOffersManualLogin() {
        var result = ManualLoginCheck.check(false, false,
                () -> CompletableFuture.completedFuture(null)).join();

        assertEquals(MANUAL_LOGIN_AVAILABLE, result.outcome());
        assertNull(result.error());
    }

    @Test
    void offlineAccountsDoNotRequestTokensOrOfferManualLogin() {
        var result = ManualLoginCheck.check(true, false, unexpectedTokenRequest()).join();

        assertEquals(UNAVAILABLE, result.outcome());
        assertNull(result.error());
    }

    @Test
    void simulationOffersManualLoginWithoutAccessingRealOrCachedTokens() {
        var result = ManualLoginCheck.check(false, true, unexpectedTokenRequest()).join();

        assertEquals(MANUAL_LOGIN_AVAILABLE, result.outcome());
        assertTrue(result.error() instanceof IOException);
    }

    @Test
    void simulationAlsoWorksWithAnOfflineDevelopmentAccount() {
        var result = ManualLoginCheck.check(true, true, unexpectedTokenRequest()).join();

        assertEquals(MANUAL_LOGIN_AVAILABLE, result.outcome());
    }

    @Test
    void waitsForAuthenticationBeforeNotifyingTheUiExactlyOnce() {
        CompletableFuture<Object> request = new CompletableFuture<>();
        AtomicInteger notifications = new AtomicInteger();
        var check = ManualLoginCheck.check(false, false, () -> request);
        var notification = check.thenAccept(result -> notifications.incrementAndGet());
        assertFalse(check.isDone());
        assertEquals(0, notifications.get());

        request.completeExceptionally(new IOException("Authentication failed"));
        notification.join();
        assertEquals(MANUAL_LOGIN_AVAILABLE, check.join().outcome());
        assertEquals(1, notifications.get());
        assertFalse(request.complete(new Object()));
        assertEquals(1, notifications.get());
    }

    @Test
    void synchronousAuthenticationFailureAlsoOffersManualLogin() {
        IllegalStateException failure = new IllegalStateException("Session unavailable");
        var result = ManualLoginCheck.check(false, false, () -> { throw failure; }).join();

        assertEquals(MANUAL_LOGIN_AVAILABLE, result.outcome());
        assertSame(failure, result.error());
    }

    private static Supplier<CompletableFuture<?>> unexpectedTokenRequest() {
        return () -> { throw new AssertionError("Real session access was not expected"); };
    }
}
