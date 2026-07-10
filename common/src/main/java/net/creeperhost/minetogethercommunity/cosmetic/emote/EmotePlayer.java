package net.creeperhost.minetogethercommunity.cosmetic.emote;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticPreviewTime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EmotePlayer {

    private static final Map<UUID, ActiveEmote> ACTIVE = new ConcurrentHashMap<>();
    private static final Set<String> PENDING_LOCAL_RETRIES = ConcurrentHashMap.newKeySet();
    private static final Set<RemoteRetry> PENDING_REMOTE_RETRIES = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<PreviewEmote> PREVIEW = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SUPPRESS_POSE = ThreadLocal.withInitial(() -> false);
    private static final int LOAD_RETRY_ATTEMPTS = 20;
    private static final long LOAD_RETRY_DELAY_MS = 50L;
    private static final double CANCEL_MOVE_THRESHOLD_SQR = 0.0001D;
    private static final ScheduledExecutorService RETRY_EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "EmoteAssetRetry");
        thread.setDaemon(true);
        return thread;
    });

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
        EmoteNetworking.tryBroadcastStart(emoteId, emote.toggle());
    }

    public static void stopLocal() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PENDING_LOCAL_RETRIES.clear();
        if (ACTIVE.remove(mc.player.getUUID()) != null) {
            EmoteNetworking.tryBroadcastStop();
        }
    }

    public static @Nullable Emote localActiveEmote() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        ActiveEmote active = ACTIVE.get(mc.player.getUUID());
        return active == null ? null : active.emote();
    }

    public static boolean isLocalActive(String emoteId) {
        Emote active = localActiveEmote();
        return active != null && active.id().equals(emoteId);
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
        RemoteRetry retry = new RemoteRetry(playerId, emoteId);
        synchronized (PENDING_REMOTE_RETRIES) {
            if (PENDING_REMOTE_RETRIES.contains(retry)) return;
            PENDING_REMOTE_RETRIES.removeIf(pending -> pending.playerId().equals(playerId));
            PENDING_REMOTE_RETRIES.add(retry);
        }

        scheduleRemoteRetry(retry, 0);
    }

    private static void retryPlayLocal(String emoteId) {
        synchronized (PENDING_LOCAL_RETRIES) {
            if (PENDING_LOCAL_RETRIES.contains(emoteId)) return;
            PENDING_LOCAL_RETRIES.clear();
            PENDING_LOCAL_RETRIES.add(emoteId);
        }

        scheduleLocalRetry(emoteId, 0);
    }

    private static void scheduleRemoteRetry(RemoteRetry retry, int attempt) {
        RETRY_EXECUTOR.schedule(() -> {
            if (!PENDING_REMOTE_RETRIES.contains(retry)) return;
            if (EmoteRegistry.getLoaded(retry.emoteId()) != null) {
                Minecraft.getInstance().execute(() -> {
                    if (PENDING_REMOTE_RETRIES.remove(retry)) {
                        playRemote(retry.playerId(), retry.emoteId(), false);
                    }
                });
            } else if (attempt + 1 < LOAD_RETRY_ATTEMPTS) {
                scheduleRemoteRetry(retry, attempt + 1);
            } else {
                PENDING_REMOTE_RETRIES.remove(retry);
            }
        }, LOAD_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private static void scheduleLocalRetry(String emoteId, int attempt) {
        RETRY_EXECUTOR.schedule(() -> {
            if (!PENDING_LOCAL_RETRIES.contains(emoteId)) return;
            if (EmoteRegistry.getLoaded(emoteId) != null) {
                Minecraft.getInstance().execute(() -> {
                    if (PENDING_LOCAL_RETRIES.remove(emoteId)) {
                        playLocal(emoteId, false);
                    }
                });
            } else if (attempt + 1 < LOAD_RETRY_ATTEMPTS) {
                scheduleLocalRetry(emoteId, attempt + 1);
            } else {
                PENDING_LOCAL_RETRIES.remove(emoteId);
            }
        }, LOAD_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    public static void stop(UUID playerId) {
        PENDING_REMOTE_RETRIES.removeIf(retry -> retry.playerId().equals(playerId));
        ACTIVE.remove(playerId);
    }

    /** Clears active and pending emotes when the client leaves a world. */
    public static void clearAll() {
        ACTIVE.clear();
        PENDING_LOCAL_RETRIES.clear();
        PENDING_REMOTE_RETRIES.clear();
        PREVIEW.remove();
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
        boolean moving = isMoving(player);
        if (!active.emote.allowMovement() && moving) {
            ACTIVE.remove(player.getUUID());
            return null;
        }
        if (active.emote.requiresMovement() && !moving) return null;
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
        float headPitchWave = wave * animation.waveHeadPitchAmplitudeDegrees();
        float headYawWave = wave * animation.waveHeadYawAmplitudeDegrees();
        float headRollWave = wave * animation.waveHeadRollAmplitudeDegrees();
        float bodyPitchWave = wave * animation.waveBodyPitchAmplitudeDegrees();
        float bodyYawWave = wave * animation.waveBodyYawAmplitudeDegrees();
        float bodyRollWave = wave * animation.waveBodyRollAmplitudeDegrees();
        float progress = Math.max(0.0F, Math.min(1.0F, elapsed / Math.max(1.0F, animation.durationTicks())));
        float renderPitch = animation.renderPitchDegrees() + progress * animation.wavePitchSpinDegrees()
                + wave * animation.waveRenderPitchAmplitudeDegrees();
        float renderYaw = animation.renderYawDegrees() + progress * animation.waveYawSpinDegrees()
                + wave * animation.waveRenderYawAmplitudeDegrees();
        float renderRoll = animation.renderRollDegrees() + progress * animation.waveRollSpinDegrees()
                + wave * animation.waveRenderRollAmplitudeDegrees();
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
                radians(animation.headPitchDegrees() + headPitchWave),
                radians(animation.headYawDegrees() + headYawWave),
                radians(animation.headRollDegrees() + headRollWave),
                radians(animation.bodyPitchDegrees() + bodyPitchWave),
                radians(animation.bodyYawDegrees() + bodyYawWave),
                radians(animation.bodyRollDegrees() + bodyRollWave),
                animation.translateY() + translateYArc,
                radians(renderPitch),
                radians(renderYaw),
                radians(renderRoll),
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

    public static float renderYaw(AbstractClientPlayer player, float ageInTicks) {
        Pose pose = poseFor(player, ageInTicks);
        return pose == null ? 0.0F : pose.renderYaw();
    }

    public static float renderRoll(AbstractClientPlayer player, float ageInTicks) {
        Pose pose = poseFor(player, ageInTicks);
        return pose == null ? 0.0F : pose.renderRoll();
    }

    private record ActiveEmote(Emote emote, int startTick) {
    }

    private record RemoteRetry(UUID playerId, String emoteId) {
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
            float renderYaw,
            float renderRoll,
            float weight,
            boolean lockBody
    ) {
    }
}
