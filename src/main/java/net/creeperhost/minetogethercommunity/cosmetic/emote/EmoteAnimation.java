package net.creeperhost.minetogethercommunity.cosmetic.emote;

import com.google.gson.JsonObject;

public class EmoteAnimation {

    public static final EmoteAnimation WAVE = new EmoteAnimation(
            80,
            -155.0F, 0.0F, 18.0F,
            0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F,
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            28.0F, 0.0F, 0.0F, 0.0F, 0.0F, 28.0F, 0.0F,
            0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 0.0F,
            0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F, 0.0F, 0.62F,
            false
    );

    private final int durationTicks;
    private final float rightArmPitchDegrees;
    private final float rightArmYawDegrees;
    private final float rightArmRollDegrees;
    private final float leftArmPitchDegrees;
    private final float leftArmYawDegrees;
    private final float leftArmRollDegrees;
    private final float rightLegPitchDegrees;
    private final float rightLegYawDegrees;
    private final float rightLegRollDegrees;
    private final float leftLegPitchDegrees;
    private final float leftLegYawDegrees;
    private final float leftLegRollDegrees;
    private final float headPitchDegrees;
    private final float headYawDegrees;
    private final float headRollDegrees;
    private final float bodyPitchDegrees;
    private final float bodyYawDegrees;
    private final float bodyRollDegrees;
    private final float translateY;
    private final float renderPitchDegrees;
    private final float renderYawDegrees;
    private final float renderRollDegrees;
    private final float waveAmplitudeDegrees;
    private final float waveRightArmPitchAmplitudeDegrees;
    private final float waveLeftArmPitchAmplitudeDegrees;
    private final float waveRightArmYawAmplitudeDegrees;
    private final float waveLeftArmYawAmplitudeDegrees;
    private final float waveRightArmRollAmplitudeDegrees;
    private final float waveLeftArmRollAmplitudeDegrees;
    private final float waveRightLegPitchAmplitudeDegrees;
    private final float waveLeftLegPitchAmplitudeDegrees;
    private final float waveRightLegYawAmplitudeDegrees;
    private final float waveLeftLegYawAmplitudeDegrees;
    private final float waveRightLegRollAmplitudeDegrees;
    private final float waveLeftLegRollAmplitudeDegrees;
    private final float waveHeadPitchAmplitudeDegrees;
    private final float waveHeadYawAmplitudeDegrees;
    private final float waveHeadRollAmplitudeDegrees;
    private final float waveBodyPitchAmplitudeDegrees;
    private final float waveBodyYawAmplitudeDegrees;
    private final float waveBodyRollAmplitudeDegrees;
    private final float waveRenderPitchAmplitudeDegrees;
    private final float waveRenderYawAmplitudeDegrees;
    private final float waveRenderRollAmplitudeDegrees;
    private final float wavePitchSpinDegrees;
    private final float waveYawSpinDegrees;
    private final float waveRollSpinDegrees;
    private final float waveTranslateYAmplitude;
    private final float waveSpeed;
    private final boolean lockBody;

    public EmoteAnimation(int durationTicks,
                          float rightArmPitchDegrees, float rightArmYawDegrees, float rightArmRollDegrees,
                          float leftArmPitchDegrees, float leftArmYawDegrees, float leftArmRollDegrees,
                          float rightLegPitchDegrees, float rightLegYawDegrees, float rightLegRollDegrees,
                          float leftLegPitchDegrees, float leftLegYawDegrees, float leftLegRollDegrees,
                          float headPitchDegrees, float headYawDegrees, float headRollDegrees,
                          float bodyPitchDegrees, float bodyYawDegrees, float bodyRollDegrees,
                          float translateY, float renderPitchDegrees, float renderYawDegrees, float renderRollDegrees,
                          float waveAmplitudeDegrees,
                          float waveRightArmPitchAmplitudeDegrees, float waveLeftArmPitchAmplitudeDegrees,
                          float waveRightArmYawAmplitudeDegrees, float waveLeftArmYawAmplitudeDegrees,
                          float waveRightArmRollAmplitudeDegrees, float waveLeftArmRollAmplitudeDegrees,
                          float waveRightLegPitchAmplitudeDegrees, float waveLeftLegPitchAmplitudeDegrees,
                          float waveRightLegYawAmplitudeDegrees, float waveLeftLegYawAmplitudeDegrees,
                          float waveRightLegRollAmplitudeDegrees, float waveLeftLegRollAmplitudeDegrees,
                          float waveHeadPitchAmplitudeDegrees, float waveHeadYawAmplitudeDegrees,
                          float waveHeadRollAmplitudeDegrees, float waveBodyPitchAmplitudeDegrees,
                          float waveBodyYawAmplitudeDegrees, float waveBodyRollAmplitudeDegrees,
                          float waveRenderPitchAmplitudeDegrees, float waveRenderYawAmplitudeDegrees,
                          float waveRenderRollAmplitudeDegrees, float wavePitchSpinDegrees,
                          float waveYawSpinDegrees, float waveRollSpinDegrees,
                          float waveTranslateYAmplitude, float waveSpeed,
                          boolean lockBody) {
        this.durationTicks = durationTicks;
        this.rightArmPitchDegrees = rightArmPitchDegrees;
        this.rightArmYawDegrees = rightArmYawDegrees;
        this.rightArmRollDegrees = rightArmRollDegrees;
        this.leftArmPitchDegrees = leftArmPitchDegrees;
        this.leftArmYawDegrees = leftArmYawDegrees;
        this.leftArmRollDegrees = leftArmRollDegrees;
        this.rightLegPitchDegrees = rightLegPitchDegrees;
        this.rightLegYawDegrees = rightLegYawDegrees;
        this.rightLegRollDegrees = rightLegRollDegrees;
        this.leftLegPitchDegrees = leftLegPitchDegrees;
        this.leftLegYawDegrees = leftLegYawDegrees;
        this.leftLegRollDegrees = leftLegRollDegrees;
        this.headPitchDegrees = headPitchDegrees;
        this.headYawDegrees = headYawDegrees;
        this.headRollDegrees = headRollDegrees;
        this.bodyPitchDegrees = bodyPitchDegrees;
        this.bodyYawDegrees = bodyYawDegrees;
        this.bodyRollDegrees = bodyRollDegrees;
        this.translateY = translateY;
        this.renderPitchDegrees = renderPitchDegrees;
        this.renderYawDegrees = renderYawDegrees;
        this.renderRollDegrees = renderRollDegrees;
        this.waveAmplitudeDegrees = waveAmplitudeDegrees;
        this.waveRightArmPitchAmplitudeDegrees = waveRightArmPitchAmplitudeDegrees;
        this.waveLeftArmPitchAmplitudeDegrees = waveLeftArmPitchAmplitudeDegrees;
        this.waveRightArmYawAmplitudeDegrees = waveRightArmYawAmplitudeDegrees;
        this.waveLeftArmYawAmplitudeDegrees = waveLeftArmYawAmplitudeDegrees;
        this.waveRightArmRollAmplitudeDegrees = waveRightArmRollAmplitudeDegrees;
        this.waveLeftArmRollAmplitudeDegrees = waveLeftArmRollAmplitudeDegrees;
        this.waveRightLegPitchAmplitudeDegrees = waveRightLegPitchAmplitudeDegrees;
        this.waveLeftLegPitchAmplitudeDegrees = waveLeftLegPitchAmplitudeDegrees;
        this.waveRightLegYawAmplitudeDegrees = waveRightLegYawAmplitudeDegrees;
        this.waveLeftLegYawAmplitudeDegrees = waveLeftLegYawAmplitudeDegrees;
        this.waveRightLegRollAmplitudeDegrees = waveRightLegRollAmplitudeDegrees;
        this.waveLeftLegRollAmplitudeDegrees = waveLeftLegRollAmplitudeDegrees;
        this.waveHeadPitchAmplitudeDegrees = waveHeadPitchAmplitudeDegrees;
        this.waveHeadYawAmplitudeDegrees = waveHeadYawAmplitudeDegrees;
        this.waveHeadRollAmplitudeDegrees = waveHeadRollAmplitudeDegrees;
        this.waveBodyPitchAmplitudeDegrees = waveBodyPitchAmplitudeDegrees;
        this.waveBodyYawAmplitudeDegrees = waveBodyYawAmplitudeDegrees;
        this.waveBodyRollAmplitudeDegrees = waveBodyRollAmplitudeDegrees;
        this.waveRenderPitchAmplitudeDegrees = waveRenderPitchAmplitudeDegrees;
        this.waveRenderYawAmplitudeDegrees = waveRenderYawAmplitudeDegrees;
        this.waveRenderRollAmplitudeDegrees = waveRenderRollAmplitudeDegrees;
        this.wavePitchSpinDegrees = wavePitchSpinDegrees;
        this.waveYawSpinDegrees = waveYawSpinDegrees;
        this.waveRollSpinDegrees = waveRollSpinDegrees;
        this.waveTranslateYAmplitude = waveTranslateYAmplitude;
        this.waveSpeed = waveSpeed;
        this.lockBody = lockBody;
    }

    public static EmoteAnimation fromJson(JsonObject root) {
        if (root == null) return WAVE;
        JsonObject rightArm = object(root, "rightArm");
        JsonObject leftArm = object(root, "leftArm");
        JsonObject rightLeg = object(root, "rightLeg");
        JsonObject leftLeg = object(root, "leftLeg");
        JsonObject head = object(root, "head");
        JsonObject body = object(root, "body");
        JsonObject wave = object(root, "wave");
        return new EmoteAnimation(
                getInt(root, "durationTicks", WAVE.durationTicks),
                getFloat(rightArm, "pitchDegrees", WAVE.rightArmPitchDegrees),
                getFloat(rightArm, "yawDegrees", WAVE.rightArmYawDegrees),
                getFloat(rightArm, "rollDegrees", WAVE.rightArmRollDegrees),
                getFloat(leftArm, "pitchDegrees", WAVE.leftArmPitchDegrees),
                getFloat(leftArm, "yawDegrees", WAVE.leftArmYawDegrees),
                getFloat(leftArm, "rollDegrees", WAVE.leftArmRollDegrees),
                getFloat(rightLeg, "pitchDegrees", WAVE.rightLegPitchDegrees),
                getFloat(rightLeg, "yawDegrees", WAVE.rightLegYawDegrees),
                getFloat(rightLeg, "rollDegrees", WAVE.rightLegRollDegrees),
                getFloat(leftLeg, "pitchDegrees", WAVE.leftLegPitchDegrees),
                getFloat(leftLeg, "yawDegrees", WAVE.leftLegYawDegrees),
                getFloat(leftLeg, "rollDegrees", WAVE.leftLegRollDegrees),
                getFloat(head, "pitchDegrees", WAVE.headPitchDegrees),
                getFloat(head, "yawDegrees", WAVE.headYawDegrees),
                getFloat(head, "rollDegrees", WAVE.headRollDegrees),
                getFloat(body, "pitchDegrees", WAVE.bodyPitchDegrees),
                getFloat(body, "yawDegrees", WAVE.bodyYawDegrees),
                getFloat(body, "rollDegrees", WAVE.bodyRollDegrees),
                getFloat(root, "translateY", WAVE.translateY),
                getFloat(root, "renderPitchDegrees", WAVE.renderPitchDegrees),
                getFloat(root, "renderYawDegrees", WAVE.renderYawDegrees),
                getFloat(root, "renderRollDegrees", WAVE.renderRollDegrees),
                getFloat(wave, "amplitudeDegrees", WAVE.waveAmplitudeDegrees),
                getFloat(wave, "rightArmPitchAmplitudeDegrees", WAVE.waveRightArmPitchAmplitudeDegrees),
                getFloat(wave, "leftArmPitchAmplitudeDegrees", WAVE.waveLeftArmPitchAmplitudeDegrees),
                getFloat(wave, "rightArmYawAmplitudeDegrees", WAVE.waveRightArmYawAmplitudeDegrees),
                getFloat(wave, "leftArmYawAmplitudeDegrees", WAVE.waveLeftArmYawAmplitudeDegrees),
                getFloat(wave, "rightArmRollAmplitudeDegrees", getFloat(wave, "amplitudeDegrees", WAVE.waveRightArmRollAmplitudeDegrees)),
                getFloat(wave, "leftArmRollAmplitudeDegrees", WAVE.waveLeftArmRollAmplitudeDegrees),
                getFloat(wave, "rightLegPitchAmplitudeDegrees", WAVE.waveRightLegPitchAmplitudeDegrees),
                getFloat(wave, "leftLegPitchAmplitudeDegrees", WAVE.waveLeftLegPitchAmplitudeDegrees),
                getFloat(wave, "rightLegYawAmplitudeDegrees", WAVE.waveRightLegYawAmplitudeDegrees),
                getFloat(wave, "leftLegYawAmplitudeDegrees", WAVE.waveLeftLegYawAmplitudeDegrees),
                getFloat(wave, "rightLegRollAmplitudeDegrees", WAVE.waveRightLegRollAmplitudeDegrees),
                getFloat(wave, "leftLegRollAmplitudeDegrees", WAVE.waveLeftLegRollAmplitudeDegrees),
                getFloat(wave, "headPitchAmplitudeDegrees", WAVE.waveHeadPitchAmplitudeDegrees),
                getFloat(wave, "headYawAmplitudeDegrees", WAVE.waveHeadYawAmplitudeDegrees),
                getFloat(wave, "headRollAmplitudeDegrees", WAVE.waveHeadRollAmplitudeDegrees),
                getFloat(wave, "bodyPitchAmplitudeDegrees", WAVE.waveBodyPitchAmplitudeDegrees),
                getFloat(wave, "bodyYawAmplitudeDegrees", WAVE.waveBodyYawAmplitudeDegrees),
                getFloat(wave, "bodyRollAmplitudeDegrees", WAVE.waveBodyRollAmplitudeDegrees),
                getFloat(wave, "renderPitchAmplitudeDegrees", WAVE.waveRenderPitchAmplitudeDegrees),
                getFloat(wave, "renderYawAmplitudeDegrees", WAVE.waveRenderYawAmplitudeDegrees),
                getFloat(wave, "renderRollAmplitudeDegrees", WAVE.waveRenderRollAmplitudeDegrees),
                getFloat(wave, "pitchSpinDegrees", WAVE.wavePitchSpinDegrees),
                getFloat(wave, "yawSpinDegrees", WAVE.waveYawSpinDegrees),
                getFloat(wave, "rollSpinDegrees", WAVE.waveRollSpinDegrees),
                getFloat(wave, "translateYAmplitude", WAVE.waveTranslateYAmplitude),
                getFloat(wave, "speed", WAVE.waveSpeed),
                getBoolean(root, "lockBody", WAVE.lockBody)
        );
    }

    public int durationTicks() { return durationTicks; }
    public float rightArmPitchDegrees() { return rightArmPitchDegrees; }
    public float rightArmYawDegrees() { return rightArmYawDegrees; }
    public float rightArmRollDegrees() { return rightArmRollDegrees; }
    public float leftArmPitchDegrees() { return leftArmPitchDegrees; }
    public float leftArmYawDegrees() { return leftArmYawDegrees; }
    public float leftArmRollDegrees() { return leftArmRollDegrees; }
    public float rightLegPitchDegrees() { return rightLegPitchDegrees; }
    public float rightLegYawDegrees() { return rightLegYawDegrees; }
    public float rightLegRollDegrees() { return rightLegRollDegrees; }
    public float leftLegPitchDegrees() { return leftLegPitchDegrees; }
    public float leftLegYawDegrees() { return leftLegYawDegrees; }
    public float leftLegRollDegrees() { return leftLegRollDegrees; }
    public float headPitchDegrees() { return headPitchDegrees; }
    public float headYawDegrees() { return headYawDegrees; }
    public float headRollDegrees() { return headRollDegrees; }
    public float bodyPitchDegrees() { return bodyPitchDegrees; }
    public float bodyYawDegrees() { return bodyYawDegrees; }
    public float bodyRollDegrees() { return bodyRollDegrees; }
    public float translateY() { return translateY; }
    public float renderPitchDegrees() { return renderPitchDegrees; }
    public float renderYawDegrees() { return renderYawDegrees; }
    public float renderRollDegrees() { return renderRollDegrees; }
    public float waveRightArmPitchAmplitudeDegrees() { return waveRightArmPitchAmplitudeDegrees; }
    public float waveLeftArmPitchAmplitudeDegrees() { return waveLeftArmPitchAmplitudeDegrees; }
    public float waveRightArmYawAmplitudeDegrees() { return waveRightArmYawAmplitudeDegrees; }
    public float waveLeftArmYawAmplitudeDegrees() { return waveLeftArmYawAmplitudeDegrees; }
    public float waveRightArmRollAmplitudeDegrees() { return waveRightArmRollAmplitudeDegrees; }
    public float waveLeftArmRollAmplitudeDegrees() { return waveLeftArmRollAmplitudeDegrees; }
    public float waveRightLegPitchAmplitudeDegrees() { return waveRightLegPitchAmplitudeDegrees; }
    public float waveLeftLegPitchAmplitudeDegrees() { return waveLeftLegPitchAmplitudeDegrees; }
    public float waveRightLegYawAmplitudeDegrees() { return waveRightLegYawAmplitudeDegrees; }
    public float waveLeftLegYawAmplitudeDegrees() { return waveLeftLegYawAmplitudeDegrees; }
    public float waveRightLegRollAmplitudeDegrees() { return waveRightLegRollAmplitudeDegrees; }
    public float waveLeftLegRollAmplitudeDegrees() { return waveLeftLegRollAmplitudeDegrees; }
    public float waveHeadPitchAmplitudeDegrees() { return waveHeadPitchAmplitudeDegrees; }
    public float waveHeadYawAmplitudeDegrees() { return waveHeadYawAmplitudeDegrees; }
    public float waveHeadRollAmplitudeDegrees() { return waveHeadRollAmplitudeDegrees; }
    public float waveBodyPitchAmplitudeDegrees() { return waveBodyPitchAmplitudeDegrees; }
    public float waveBodyYawAmplitudeDegrees() { return waveBodyYawAmplitudeDegrees; }
    public float waveBodyRollAmplitudeDegrees() { return waveBodyRollAmplitudeDegrees; }
    public float waveRenderPitchAmplitudeDegrees() { return waveRenderPitchAmplitudeDegrees; }
    public float waveRenderYawAmplitudeDegrees() { return waveRenderYawAmplitudeDegrees; }
    public float waveRenderRollAmplitudeDegrees() { return waveRenderRollAmplitudeDegrees; }
    public float wavePitchSpinDegrees() { return wavePitchSpinDegrees; }
    public float waveYawSpinDegrees() { return waveYawSpinDegrees; }
    public float waveRollSpinDegrees() { return waveRollSpinDegrees; }
    public float waveTranslateYAmplitude() { return waveTranslateYAmplitude; }
    public float waveSpeed() { return waveSpeed; }
    public boolean lockBody() { return lockBody; }

    private static JsonObject object(JsonObject root, String key) {
        return root != null && root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : new JsonObject();
    }

    private static int getInt(JsonObject root, String key, int fallback) {
        if (root == null || !root.has(key) || !root.get(key).isJsonPrimitive()) return fallback;
        try {
            return root.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float getFloat(JsonObject root, String key, float fallback) {
        if (root == null || !root.has(key) || !root.get(key).isJsonPrimitive()) return fallback;
        try {
            return root.get(key).getAsFloat();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean getBoolean(JsonObject root, String key, boolean fallback) {
        if (root == null || !root.has(key) || !root.get(key).isJsonPrimitive()) return fallback;
        try {
            return root.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
