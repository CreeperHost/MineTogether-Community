package net.creeperhost.minetogethercommunity.cosmetic.emote;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EmotePlayer {

    private static final Map<UUID, ActiveEmote> ACTIVE = new ConcurrentHashMap<>();
    private static final ThreadLocal<PreviewEmote> PREVIEW = new ThreadLocal<>();
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
        Emote emote = EmoteRegistry.getLoaded(emoteId);
        if (emote == null || !emote.type().isAvailable()) {
            render.run();
            return;
        }
        PREVIEW.set(new PreviewEmote(emote, System.currentTimeMillis()));
        try {
            render.run();
        } finally {
            PREVIEW.remove();
        }
    }

    public static @Nullable Pose poseFor(AbstractClientPlayer player, float ageInTicks) {
        PreviewEmote preview = PREVIEW.get();
        if (preview != null) {
            float elapsed = ((System.currentTimeMillis() - preview.startMillis) / 50.0F) % Math.max(1, preview.emote.animation().durationTicks());
            return poseFrom(preview.emote.animation(), elapsed, false);
        }

        ActiveEmote active = ACTIVE.get(player.getUUID());
        if (active == null) return null;
        if (isMoving(player)) {
            ACTIVE.remove(player.getUUID());
            return null;
        }
        EmoteAnimation animation = active.emote.animation();
        float elapsed = Math.max(0.0F, ageInTicks - active.startTick);
        if (elapsed > animation.durationTicks()) {
            ACTIVE.remove(player.getUUID());
            return null;
        }

        return poseFrom(animation, elapsed, true);
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
        float bodyYawWave = wave * animation.waveBodyYawAmplitudeDegrees();
        float bodyRollWave = wave * animation.waveBodyRollAmplitudeDegrees();
        float progress = Math.max(0.0F, Math.min(1.0F, elapsed / Math.max(1.0F, animation.durationTicks())));
        float pitchSpin = progress * animation.wavePitchSpinDegrees();
        float translateYArc = (float) Math.sin(progress * Math.PI) * animation.waveTranslateYAmplitude();
        return new Pose(
                radians(animation.rightArmPitchDegrees() + rightArmPitchWave + pitchSpin),
                radians(animation.rightArmYawDegrees() + rightArmYawWave),
                radians(animation.rightArmRollDegrees() + rightArmRollWave),
                radians(animation.leftArmPitchDegrees() + leftArmPitchWave + pitchSpin),
                radians(animation.leftArmYawDegrees() + leftArmYawWave),
                radians(animation.leftArmRollDegrees() + leftArmRollWave),
                radians(animation.rightLegPitchDegrees() + pitchSpin),
                radians(animation.rightLegYawDegrees()),
                radians(animation.rightLegRollDegrees()),
                radians(animation.leftLegPitchDegrees() + pitchSpin),
                radians(animation.leftLegYawDegrees()),
                radians(animation.leftLegRollDegrees()),
                radians(animation.headPitchDegrees() + pitchSpin),
                radians(animation.headYawDegrees()),
                radians(animation.headRollDegrees()),
                radians(animation.bodyPitchDegrees() + pitchSpin),
                radians(animation.bodyYawDegrees() + bodyYawWave),
                radians(animation.bodyRollDegrees() + bodyRollWave),
                animation.translateY() + translateYArc,
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

    private record ActiveEmote(Emote emote, int startTick) {
    }

    private static int tickCount() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.tickCount : 0;
    }

    private record PreviewEmote(Emote emote, long startMillis) {
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
            float weight,
            boolean lockBody
    ) {
    }
}
