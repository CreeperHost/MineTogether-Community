package net.creeperhost.minetogethercommunity.cosmetic.emote;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticPreviewTime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EmotePlayer {

    private static final Map<UUID, ActiveEmote> ACTIVE = new ConcurrentHashMap<>();
    private static final ThreadLocal<PreviewEmote> PREVIEW = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SUPPRESS_POSE = ThreadLocal.withInitial(() -> false);
    private static final int LOAD_RETRY_ATTEMPTS = 20;
    private static final double CANCEL_MOVE_THRESHOLD_SQR = 0.0001D;

    public static void playLocal(String emoteId) {
        playLocal(emoteId, true);
    }

    private static void playLocal(String emoteId, boolean retryIfLoading) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Emote emote = EmoteRegistry.getLoaded(emoteId);
        if (emote == null && retryIfLoading) {
            retryPlayLocal(emoteId);
            return;
        }
        if (emote == null || !emote.type().isAvailable()) return;
        ActiveEmote active = ACTIVE.get(mc.player.getUUID());
        if (active != null && active.emote.id().equals(emote.id()) && emote.toggle()) {
            ACTIVE.remove(mc.player.getUUID());
            EmoteNetworking.tryBroadcastStop();
            return;
        }
        ACTIVE.put(mc.player.getUUID(), new ActiveEmote(emote, mc.player.tickCount));
        EmoteNetworking.tryBroadcastStart(emoteId);
    }

    public static void playRemote(UUID playerId, String emoteId) {
        playRemote(playerId, emoteId, true);
    }

    private static void playRemote(UUID playerId, String emoteId, boolean retryIfLoading) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Emote emote = EmoteRegistry.getLoaded(emoteId);
        if (emote == null && retryIfLoading) {
            retryPlayRemote(playerId, emoteId);
            return;
        }
        if (emote == null || !emote.type().isAvailable()) return;
        ACTIVE.put(playerId, new ActiveEmote(emote, tickCount()));
    }

    private static void retryPlayRemote(UUID playerId, String emoteId) {
        Thread retryThread = new Thread(() -> {
            for (int attempt = 0; attempt < LOAD_RETRY_ATTEMPTS; attempt++) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (EmoteRegistry.getLoaded(emoteId) != null) {
                    Minecraft.getInstance().execute(() -> playRemote(playerId, emoteId, false));
                    return;
                }
            }
        }, "RemoteEmotePlayRetry-" + emoteId);
        retryThread.setDaemon(true);
        retryThread.start();
    }

    private static void retryPlayLocal(String emoteId) {
        Thread retryThread = new Thread(() -> {
            for (int attempt = 0; attempt < LOAD_RETRY_ATTEMPTS; attempt++) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (EmoteRegistry.getLoaded(emoteId) != null) {
                    Minecraft.getInstance().execute(() -> playLocal(emoteId, false));
                    return;
                }
            }
        }, "EmotePlayRetry-" + emoteId);
        retryThread.setDaemon(true);
        retryThread.start();
    }

    public static void stop(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    public static void withPreviewPose(String emoteId, Runnable render) {
        withPreviewPose(emoteId, Float.NaN, render);
    }

    public static void withPreviewPose(String emoteId, float frozenTick, Runnable render) {
        Emote emote = EmoteRegistry.getLoaded(emoteId);
        if (emote == null || !emote.type().isAvailable()) {
            render.run();
            return;
        }
        PREVIEW.set(new PreviewEmote(emote, System.currentTimeMillis(), frozenTick));
        try {
            render.run();
        } finally {
            PREVIEW.remove();
        }
    }

    public static void withoutPose(Runnable render) {
        boolean previous = SUPPRESS_POSE.get();
        SUPPRESS_POSE.set(true);
        try {
            render.run();
        } finally {
            SUPPRESS_POSE.set(previous);
        }
    }

    public static @Nullable Pose poseFor(AbstractClientPlayer player, float ageInTicks) {
        PreviewEmote preview = PREVIEW.get();
        if (preview != null) {
            float duration = Math.max(1, preview.emote().animation().durationTicks());
            float elapsed = Float.isNaN(preview.frozenTick())
                    ? CosmeticPreviewTime.currentAgeInTicks() % duration
                    : preview.frozenTick();
            return poseFrom(preview.emote.animation(), elapsed, false);
        }
        if (SUPPRESS_POSE.get()) return null;

        ActiveEmote active = ACTIVE.get(player.getUUID());
        if (active == null) return null;
        if (!active.emote.allowMovement() && isMoving(player)) {
            ACTIVE.remove(player.getUUID());
            return null;
        }
        EmoteAnimation animation = active.emote.animation();
        float elapsed = Math.max(0.0F, ageInTicks - active.startTick);
        boolean loopingToggle = active.emote.toggle();
        if (loopingToggle) {
            elapsed %= Math.max(1, animation.durationTicks());
        } else if (elapsed > animation.durationTicks()) {
            ACTIVE.remove(player.getUUID());
            return null;
        }

        return poseFrom(animation, elapsed, !loopingToggle);
    }

    private static @Nullable Pose poseFrom(EmoteAnimation animation, float elapsed, boolean fade) {
        float fadeIn = Math.min(1.0F, elapsed / 5.0F);
        float fadeOut = Math.min(1.0F, (animation.durationTicks() - elapsed) / 8.0F);
        float weight = fade ? Math.max(0.0F, Math.min(fadeIn, fadeOut)) : 1.0F;
        float wave = (float) Math.sin(elapsed * animation.waveSpeed());
        float rightArmPitchWave = wave * animation.waveRightArmPitchAmplitudeDegrees();
        float leftArmPitchWave = wave * animation.waveLeftArmPitchAmplitudeDegrees();
        float rightArmYawWave = wave * animation.waveRightArmYawAmplitudeDegrees();
        float leftArmYawWave = wave * animation.waveLeftArmYawAmplitudeDegrees();
        float rightArmRollWave = wave * animation.waveRightArmRollAmplitudeDegrees();
        float leftArmRollWave = wave * animation.waveLeftArmRollAmplitudeDegrees();
        float rightLegPitchWave = wave * animation.waveRightLegPitchAmplitudeDegrees();
        float leftLegPitchWave = wave * animation.waveLeftLegPitchAmplitudeDegrees();
        float rightLegYawWave = wave * animation.waveRightLegYawAmplitudeDegrees();
        float leftLegYawWave = wave * animation.waveLeftLegYawAmplitudeDegrees();
        float rightLegRollWave = wave * animation.waveRightLegRollAmplitudeDegrees();
        float leftLegRollWave = wave * animation.waveLeftLegRollAmplitudeDegrees();
        float bodyYawWave = wave * animation.waveBodyYawAmplitudeDegrees();
        float bodyRollWave = wave * animation.waveBodyRollAmplitudeDegrees();
        float progress = Math.max(0.0F, Math.min(1.0F, elapsed / Math.max(1.0F, animation.durationTicks())));
        float renderPitch = animation.renderPitchDegrees() + progress * animation.wavePitchSpinDegrees();
        float translateYArc = (float) Math.sin(progress * Math.PI) * animation.waveTranslateYAmplitude();
        return new Pose(
                radians(animation.rightArmPitchDegrees() + rightArmPitchWave),
                radians(animation.rightArmYawDegrees() + rightArmYawWave),
                radians(animation.rightArmRollDegrees() + rightArmRollWave),
                radians(animation.leftArmPitchDegrees() + leftArmPitchWave),
                radians(animation.leftArmYawDegrees() + leftArmYawWave),
                radians(animation.leftArmRollDegrees() + leftArmRollWave),
                radians(animation.rightLegPitchDegrees() + rightLegPitchWave),
                radians(animation.rightLegYawDegrees() + rightLegYawWave),
                radians(animation.rightLegRollDegrees() + rightLegRollWave),
                radians(animation.leftLegPitchDegrees() + leftLegPitchWave),
                radians(animation.leftLegYawDegrees() + leftLegYawWave),
                radians(animation.leftLegRollDegrees() + leftLegRollWave),
                radians(animation.headPitchDegrees()),
                radians(animation.headYawDegrees()),
                radians(animation.headRollDegrees()),
                radians(animation.bodyPitchDegrees()),
                radians(animation.bodyYawDegrees() + bodyYawWave),
                radians(animation.bodyRollDegrees() + bodyRollWave),
                animation.translateY() + translateYArc,
                radians(renderPitch),
                weight,
                animation.lockBody()
        );
    }

    private static float radians(float degrees) {
        return degrees * ((float) Math.PI / 180.0F);
    }

    private static boolean isMoving(AbstractClientPlayer player) {
        double x = player.getDeltaMovement().x;
        double z = player.getDeltaMovement().z;
        return x * x + z * z > CANCEL_MOVE_THRESHOLD_SQR;
    }

    public static float renderTranslateY(AbstractClientPlayer player, float ageInTicks) {
        Pose pose = poseFor(player, ageInTicks);
        return pose == null ? 0.0F : pose.translateY() * pose.weight();
    }

    public static float renderPitch(AbstractClientPlayer player, float ageInTicks) {
        Pose pose = poseFor(player, ageInTicks);
        return pose == null ? 0.0F : pose.renderPitch();
    }

    private record ActiveEmote(Emote emote, int startTick) {
    }

    private static int tickCount() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.tickCount : 0;
    }

    private record PreviewEmote(Emote emote, long startMillis, float frozenTick) {
    }

    public record Pose(
            float rightArmPitch,
            float rightArmYaw,
            float rightArmRoll,
            float leftArmPitch,
            float leftArmYaw,
            float leftArmRoll,
            float rightLegPitch,
            float rightLegYaw,
            float rightLegRoll,
            float leftLegPitch,
            float leftLegYaw,
            float leftLegRoll,
            float headPitch,
            float headYaw,
            float headRoll,
            float bodyPitch,
            float bodyYaw,
            float bodyRoll,
            float translateY,
            float renderPitch,
            float weight,
            boolean lockBody
    ) {
    }
}
