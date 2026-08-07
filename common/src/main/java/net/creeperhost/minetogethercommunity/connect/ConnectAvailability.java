package net.creeperhost.minetogethercommunity.connect;

import java.util.Objects;

final class ConnectAvailability {

    static final long RETRY_DELAY_MILLIS = 30_000L;

    private boolean available = true;
    private long nextAttempt;
    private String lastFailure = "";

    synchronized boolean shouldAttempt(long now) {
        return now >= nextAttempt;
    }

    synchronized boolean failed(long now, Throwable throwable) {
        available = false;
        nextAttempt = now + RETRY_DELAY_MILLIS;
        String message = throwable.getClass().getName() + ":" + Objects.toString(throwable.getMessage(), "");
        boolean changed = !message.equals(lastFailure);
        lastFailure = message;
        return changed;
    }

    synchronized void succeeded() {
        available = true;
        nextAttempt = 0;
        lastFailure = "";
    }

    synchronized boolean isAvailable() {
        return available;
    }

    synchronized void reset() {
        available = true;
        nextAttempt = 0;
        lastFailure = "";
    }
}
