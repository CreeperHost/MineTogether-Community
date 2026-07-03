package net.creeperhost.minetogethercommunity.cosmetic;

public final class CosmeticPreviewTime {
    private static final long START_MILLIS = System.currentTimeMillis();
    private static final ThreadLocal<Float> AGE_IN_TICKS = new ThreadLocal<>();

    private CosmeticPreviewTime() {
    }

    public static void withAnimatedPreview(Runnable render) {
        AGE_IN_TICKS.set(currentAgeInTicks());
        try {
            render.run();
        } finally {
            AGE_IN_TICKS.remove();
        }
    }

    public static float ageInTicks(float fallback) {
        Float previewAge = AGE_IN_TICKS.get();
        return previewAge != null ? previewAge : fallback;
    }

    public static float currentAgeInTicks() {
        return (float) ((System.currentTimeMillis() - START_MILLIS) / 50.0D);
    }
}
