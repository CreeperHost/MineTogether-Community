package net.creeperhost.minetogethercommunity.cosmetic.emote;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticPreviewTime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EmotePlayer {

    private static final Map<UUID, ActiveEmote> ACTIVE = new ConcurrentHashMap<UUID, ActiveEmote>();
    private static final ThreadLocal<PreviewEmote> PREVIEW = new ThreadLocal<PreviewEmote>();
    private static final ThreadLocal<Boolean> SUPPRESS_POSE = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };
    private static final int LOAD_RETRY_ATTEMPTS = 20;
    private static final double CANCEL_MOVE_THRESHOLD_SQR = 0.0001D;

    private EmotePlayer() {
    }

    public static void playLocal(String emoteId) {
        playLocal(emoteId, true);
    }

    private static void playLocal(final String emoteId, boolean retryIfLoading) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        Emote emote = EmoteRegistry.getLoaded(emoteId);
        if (emote == null && retryIfLoading) {
            retryPlayLocal(emoteId);
            return;
        }
        if (emote == null || !emote.type().isAvailable()) return;
        UUID playerId = mc.thePlayer.getUniqueID();
        ActiveEmote active = ACTIVE.get(playerId);
        if (active != null && active.emote.id().equals(emote.id()) && emote.toggle()) {
            ACTIVE.remove(playerId);
            EmoteNetworking.tryBroadcastStop();
            return;
        }
        ACTIVE.put(playerId, new ActiveEmote(emote, mc.thePlayer.ticksExisted));
        EmoteNetworking.tryBroadcastStart(emoteId);
    }

    public static void playRemote(UUID playerId, String emoteId) {
        playRemote(playerId, emoteId, true);
    }

    public static void stopLocal() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        if (ACTIVE.remove(mc.thePlayer.getUniqueID()) != null) {
            EmoteNetworking.tryBroadcastStop();
        }
    }

    public static Emote localActiveEmote() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return null;
        ActiveEmote active = ACTIVE.get(mc.thePlayer.getUniqueID());
        return active == null ? null : active.emote;
    }

    public static boolean isLocalActive(String emoteId) {
        Emote active = localActiveEmote();
        return active != null && active.id().equals(emoteId);
    }

    private static void playRemote(final UUID playerId, final String emoteId, boolean retryIfLoading) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return;
        Emote emote = EmoteRegistry.getLoaded(emoteId);
        if (emote == null && retryIfLoading) {
            retryPlayRemote(playerId, emoteId);
            return;
        }
        if (emote == null || !emote.type().isAvailable()) return;
        ACTIVE.put(playerId, new ActiveEmote(emote, tickCount()));
    }

    private static void retryPlayRemote(final UUID playerId, final String emoteId) {
        Thread retryThread = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int attempt = 0; attempt < LOAD_RETRY_ATTEMPTS; attempt++) {
                    try {
                        Thread.sleep(50L);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (EmoteRegistry.getLoaded(emoteId) != null) {
                        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                            @Override
                            public void run() {
                                playRemote(playerId, emoteId, false);
                            }
                        });
                        return;
                    }
                }
            }
        }, "RemoteEmotePlayRetry-" + emoteId);
        retryThread.setDaemon(true);
        retryThread.start();
    }

    private static void retryPlayLocal(final String emoteId) {
        Thread retryThread = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int attempt = 0; attempt < LOAD_RETRY_ATTEMPTS; attempt++) {
                    try {
                        Thread.sleep(50L);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (EmoteRegistry.getLoaded(emoteId) != null) {
                        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                            @Override
                            public void run() {
                                playLocal(emoteId, false);
                            }
                        });
                        return;
                    }
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
        PREVIEW.set(new PreviewEmote(emote, frozenTick));
        try {
            render.run();
        } finally {
            PREVIEW.remove();
        }
    }

    public static void withoutPose(Runnable render) {
        boolean previous = SUPPRESS_POSE.get().booleanValue();
        SUPPRESS_POSE.set(Boolean.TRUE);
        try {
            render.run();
        } finally {
            SUPPRESS_POSE.set(Boolean.valueOf(previous));
        }
    }

    public static Pose poseFor(AbstractClientPlayer player, float ageInTicks) {
        PreviewEmote preview = PREVIEW.get();
        if (preview != null) {
            float duration = Math.max(1, preview.emote.animation().durationTicks());
            float elapsed = Float.isNaN(preview.frozenTick)
                    ? CosmeticPreviewTime.currentAgeInTicks() % duration
                    : preview.frozenTick;
            return poseFrom(preview.emote.animation(), elapsed, false);
        }
        if (SUPPRESS_POSE.get().booleanValue()) return null;

        ActiveEmote active = ACTIVE.get(player.getUniqueID());
        if (active == null) return null;
        boolean moving = isMoving(player);
        if (!active.emote.allowMovement() && moving) {
            ACTIVE.remove(player.getUniqueID());
            return null;
        }
        if (active.emote.requiresMovement() && !moving) return null;
        EmoteAnimation animation = active.emote.animation();
        float elapsed = Math.max(0.0F, ageInTicks - active.startTick);
        boolean loopingToggle = active.emote.toggle();
        if (loopingToggle) {
            elapsed %= Math.max(1, animation.durationTicks());
        } else if (elapsed > animation.durationTicks()) {
            ACTIVE.remove(player.getUniqueID());
            return null;
        }

        return poseFrom(animation, elapsed, !loopingToggle);
    }

    public static ModelState applyToModel(ModelPlayer model, AbstractClientPlayer player, float ageInTicks) {
        Pose pose = poseFor(player, ageInTicks);
        if (pose == null) return null;
        ModelState state = new ModelState(model);
        float weight = pose.weight();
        model.bipedRightArm.rotateAngleX = lerp(model.bipedRightArm.rotateAngleX, pose.rightArmPitch(), weight);
        model.bipedRightArm.rotateAngleY = lerp(model.bipedRightArm.rotateAngleY, pose.rightArmYaw(), weight);
        model.bipedRightArm.rotateAngleZ = lerp(model.bipedRightArm.rotateAngleZ, pose.rightArmRoll(), weight);
        model.bipedLeftArm.rotateAngleX = lerp(model.bipedLeftArm.rotateAngleX, pose.leftArmPitch(), weight);
        model.bipedLeftArm.rotateAngleY = lerp(model.bipedLeftArm.rotateAngleY, pose.leftArmYaw(), weight);
        model.bipedLeftArm.rotateAngleZ = lerp(model.bipedLeftArm.rotateAngleZ, pose.leftArmRoll(), weight);
        model.bipedRightLeg.rotateAngleX = lerp(model.bipedRightLeg.rotateAngleX, pose.rightLegPitch(), weight);
        model.bipedRightLeg.rotateAngleY = lerp(model.bipedRightLeg.rotateAngleY, pose.rightLegYaw(), weight);
        model.bipedRightLeg.rotateAngleZ = lerp(model.bipedRightLeg.rotateAngleZ, pose.rightLegRoll(), weight);
        model.bipedLeftLeg.rotateAngleX = lerp(model.bipedLeftLeg.rotateAngleX, pose.leftLegPitch(), weight);
        model.bipedLeftLeg.rotateAngleY = lerp(model.bipedLeftLeg.rotateAngleY, pose.leftLegYaw(), weight);
        model.bipedLeftLeg.rotateAngleZ = lerp(model.bipedLeftLeg.rotateAngleZ, pose.leftLegRoll(), weight);
        model.bipedHead.rotateAngleX = lerp(model.bipedHead.rotateAngleX, pose.headPitch(), weight);
        model.bipedHead.rotateAngleY = lerp(model.bipedHead.rotateAngleY, pose.headYaw(), weight);
        model.bipedHead.rotateAngleZ = lerp(model.bipedHead.rotateAngleZ, pose.headRoll(), weight);
        model.bipedBody.rotateAngleX = lerp(model.bipedBody.rotateAngleX, pose.bodyPitch(), weight);
        model.bipedBody.rotateAngleY = lerp(model.bipedBody.rotateAngleY, pose.bodyYaw(), weight);
        model.bipedBody.rotateAngleZ = lerp(model.bipedBody.rotateAngleZ, pose.bodyRoll(), weight);
        syncWearLayers(model);
        return state;
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

    private static Pose poseFrom(EmoteAnimation animation, float elapsed, boolean fade) {
        float fadeIn = Math.min(1.0F, elapsed / 5.0F);
        float fadeOut = Math.min(1.0F, (animation.durationTicks() - elapsed) / 8.0F);
        float weight = fade ? Math.max(0.0F, Math.min(fadeIn, fadeOut)) : 1.0F;
        float wave = (float) Math.sin(elapsed * animation.waveSpeed());
        float progress = Math.max(0.0F, Math.min(1.0F, elapsed / Math.max(1.0F, animation.durationTicks())));
        float headPitchWave = wave * animation.waveHeadPitchAmplitudeDegrees();
        float headYawWave = wave * animation.waveHeadYawAmplitudeDegrees();
        float headRollWave = wave * animation.waveHeadRollAmplitudeDegrees();
        float bodyPitchWave = wave * animation.waveBodyPitchAmplitudeDegrees();
        float renderPitch = animation.renderPitchDegrees() + progress * animation.wavePitchSpinDegrees()
                + wave * animation.waveRenderPitchAmplitudeDegrees();
        float renderYaw = animation.renderYawDegrees() + progress * animation.waveYawSpinDegrees()
                + wave * animation.waveRenderYawAmplitudeDegrees();
        float renderRoll = animation.renderRollDegrees() + progress * animation.waveRollSpinDegrees()
                + wave * animation.waveRenderRollAmplitudeDegrees();
        float translateYArc = (float) Math.sin(progress * Math.PI) * animation.waveTranslateYAmplitude();
        return new Pose(
                radians(animation.rightArmPitchDegrees() + wave * animation.waveRightArmPitchAmplitudeDegrees()),
                radians(animation.rightArmYawDegrees() + wave * animation.waveRightArmYawAmplitudeDegrees()),
                radians(animation.rightArmRollDegrees() + wave * animation.waveRightArmRollAmplitudeDegrees()),
                radians(animation.leftArmPitchDegrees() + wave * animation.waveLeftArmPitchAmplitudeDegrees()),
                radians(animation.leftArmYawDegrees() + wave * animation.waveLeftArmYawAmplitudeDegrees()),
                radians(animation.leftArmRollDegrees() + wave * animation.waveLeftArmRollAmplitudeDegrees()),
                radians(animation.rightLegPitchDegrees() + wave * animation.waveRightLegPitchAmplitudeDegrees()),
                radians(animation.rightLegYawDegrees() + wave * animation.waveRightLegYawAmplitudeDegrees()),
                radians(animation.rightLegRollDegrees() + wave * animation.waveRightLegRollAmplitudeDegrees()),
                radians(animation.leftLegPitchDegrees() + wave * animation.waveLeftLegPitchAmplitudeDegrees()),
                radians(animation.leftLegYawDegrees() + wave * animation.waveLeftLegYawAmplitudeDegrees()),
                radians(animation.leftLegRollDegrees() + wave * animation.waveLeftLegRollAmplitudeDegrees()),
                radians(animation.headPitchDegrees() + headPitchWave),
                radians(animation.headYawDegrees() + headYawWave),
                radians(animation.headRollDegrees() + headRollWave),
                radians(animation.bodyPitchDegrees() + bodyPitchWave),
                radians(animation.bodyYawDegrees() + wave * animation.waveBodyYawAmplitudeDegrees()),
                radians(animation.bodyRollDegrees() + wave * animation.waveBodyRollAmplitudeDegrees()),
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

    private static float lerp(float from, float to, float weight) {
        return from + (to - from) * weight;
    }

    private static boolean isMoving(AbstractClientPlayer player) {
        double x = player.motionX;
        double z = player.motionZ;
        return x * x + z * z > CANCEL_MOVE_THRESHOLD_SQR;
    }

    private static int tickCount() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer != null ? mc.thePlayer.ticksExisted : 0;
    }

    private static void syncWearLayers(ModelPlayer model) {
        ModelBiped.copyModelAngles(model.bipedHead, model.bipedHeadwear);
        ModelBiped.copyModelAngles(model.bipedBody, model.bipedBodyWear);
        ModelBiped.copyModelAngles(model.bipedRightArm, model.bipedRightArmwear);
        ModelBiped.copyModelAngles(model.bipedLeftArm, model.bipedLeftArmwear);
        ModelBiped.copyModelAngles(model.bipedRightLeg, model.bipedRightLegwear);
        ModelBiped.copyModelAngles(model.bipedLeftLeg, model.bipedLeftLegwear);
    }

    private static class ActiveEmote {
        private final Emote emote;
        private final int startTick;

        private ActiveEmote(Emote emote, int startTick) {
            this.emote = emote;
            this.startTick = startTick;
        }
    }

    private static class PreviewEmote {
        private final Emote emote;
        private final float frozenTick;

        private PreviewEmote(Emote emote, float frozenTick) {
            this.emote = emote;
            this.frozenTick = frozenTick;
        }
    }

    public static class ModelState {
        private final PartState head;
        private final PartState body;
        private final PartState rightArm;
        private final PartState leftArm;
        private final PartState rightLeg;
        private final PartState leftLeg;

        private ModelState(ModelPlayer model) {
            this.head = new PartState(model.bipedHead);
            this.body = new PartState(model.bipedBody);
            this.rightArm = new PartState(model.bipedRightArm);
            this.leftArm = new PartState(model.bipedLeftArm);
            this.rightLeg = new PartState(model.bipedRightLeg);
            this.leftLeg = new PartState(model.bipedLeftLeg);
        }

        public void restore(ModelPlayer model) {
            head.restore(model.bipedHead);
            body.restore(model.bipedBody);
            rightArm.restore(model.bipedRightArm);
            leftArm.restore(model.bipedLeftArm);
            rightLeg.restore(model.bipedRightLeg);
            leftLeg.restore(model.bipedLeftLeg);
            syncWearLayers(model);
        }
    }

    private static class PartState {
        private final float x;
        private final float y;
        private final float z;

        private PartState(ModelRenderer part) {
            this.x = part.rotateAngleX;
            this.y = part.rotateAngleY;
            this.z = part.rotateAngleZ;
        }

        private void restore(ModelRenderer part) {
            part.rotateAngleX = x;
            part.rotateAngleY = y;
            part.rotateAngleZ = z;
        }
    }

    public static class Pose {
        private final float rightArmPitch;
        private final float rightArmYaw;
        private final float rightArmRoll;
        private final float leftArmPitch;
        private final float leftArmYaw;
        private final float leftArmRoll;
        private final float rightLegPitch;
        private final float rightLegYaw;
        private final float rightLegRoll;
        private final float leftLegPitch;
        private final float leftLegYaw;
        private final float leftLegRoll;
        private final float headPitch;
        private final float headYaw;
        private final float headRoll;
        private final float bodyPitch;
        private final float bodyYaw;
        private final float bodyRoll;
        private final float translateY;
        private final float renderPitch;
        private final float renderYaw;
        private final float renderRoll;
        private final float weight;
        private final boolean lockBody;

        public Pose(float rightArmPitch, float rightArmYaw, float rightArmRoll,
                    float leftArmPitch, float leftArmYaw, float leftArmRoll,
                    float rightLegPitch, float rightLegYaw, float rightLegRoll,
                    float leftLegPitch, float leftLegYaw, float leftLegRoll,
                    float headPitch, float headYaw, float headRoll,
                    float bodyPitch, float bodyYaw, float bodyRoll,
                    float translateY, float renderPitch, float renderYaw, float renderRoll,
                    float weight, boolean lockBody) {
            this.rightArmPitch = rightArmPitch;
            this.rightArmYaw = rightArmYaw;
            this.rightArmRoll = rightArmRoll;
            this.leftArmPitch = leftArmPitch;
            this.leftArmYaw = leftArmYaw;
            this.leftArmRoll = leftArmRoll;
            this.rightLegPitch = rightLegPitch;
            this.rightLegYaw = rightLegYaw;
            this.rightLegRoll = rightLegRoll;
            this.leftLegPitch = leftLegPitch;
            this.leftLegYaw = leftLegYaw;
            this.leftLegRoll = leftLegRoll;
            this.headPitch = headPitch;
            this.headYaw = headYaw;
            this.headRoll = headRoll;
            this.bodyPitch = bodyPitch;
            this.bodyYaw = bodyYaw;
            this.bodyRoll = bodyRoll;
            this.translateY = translateY;
            this.renderPitch = renderPitch;
            this.renderYaw = renderYaw;
            this.renderRoll = renderRoll;
            this.weight = weight;
            this.lockBody = lockBody;
        }

        public float rightArmPitch() { return rightArmPitch; }
        public float rightArmYaw() { return rightArmYaw; }
        public float rightArmRoll() { return rightArmRoll; }
        public float leftArmPitch() { return leftArmPitch; }
        public float leftArmYaw() { return leftArmYaw; }
        public float leftArmRoll() { return leftArmRoll; }
        public float rightLegPitch() { return rightLegPitch; }
        public float rightLegYaw() { return rightLegYaw; }
        public float rightLegRoll() { return rightLegRoll; }
        public float leftLegPitch() { return leftLegPitch; }
        public float leftLegYaw() { return leftLegYaw; }
        public float leftLegRoll() { return leftLegRoll; }
        public float headPitch() { return headPitch; }
        public float headYaw() { return headYaw; }
        public float headRoll() { return headRoll; }
        public float bodyPitch() { return bodyPitch; }
        public float bodyYaw() { return bodyYaw; }
        public float bodyRoll() { return bodyRoll; }
        public float translateY() { return translateY; }
        public float renderPitch() { return renderPitch; }
        public float renderYaw() { return renderYaw; }
        public float renderRoll() { return renderRoll; }
        public float weight() { return weight; }
        public boolean lockBody() { return lockBody; }
    }
}
