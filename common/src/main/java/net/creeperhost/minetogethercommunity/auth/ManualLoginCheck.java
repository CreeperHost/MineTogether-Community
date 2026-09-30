package net.creeperhost.minetogethercommunity.auth;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Automatic-authentication decision, independent of the Minecraft client UI. */
final class ManualLoginCheck {
    static final String SIMULATE_FAILURE_PROPERTY = "minetogether.auth.manual.simulateFailure";

    static boolean isSimulationEnabled() {
        return Boolean.getBoolean(SIMULATE_FAILURE_PROPERTY);
    }

    static CompletableFuture<Result> check(boolean offline, boolean simulateFailure,
                                           Supplier<? extends CompletableFuture<?>> requestToken) {
        if (offline && !simulateFailure) {
            return CompletableFuture.completedFuture(new Result(Outcome.UNAVAILABLE, null));
        }

        CompletableFuture<?> request;
        try {
            request = simulateFailure
                    ? CompletableFuture.failedFuture(new IOException("Simulated automatic MineTogether authentication failure"))
                    : requestToken.get();
        } catch (RuntimeException error) {
            request = CompletableFuture.failedFuture(error);
        }
        return request.handle((token, error) -> new Result(
                token != null ? Outcome.AUTHENTICATED : Outcome.MANUAL_LOGIN_AVAILABLE, error));
    }

    enum Outcome {
        UNAVAILABLE,
        AUTHENTICATED,
        MANUAL_LOGIN_AVAILABLE
    }

    record Result(Outcome outcome, Throwable error) {}

    private ManualLoginCheck() {}
}
