package net.creeperhost.minetogethercommunity.cosmetic.emote;

import com.google.gson.JsonObject;

public record EmoteAnimation(
        int durationTicks,
        float rightArmPitchDegrees,
        float rightArmYawDegrees,
        float rightArmRollDegrees,
        float leftArmPitchDegrees,
        float leftArmYawDegrees,
        float leftArmRollDegrees,
        float rightLegPitchDegrees,
        float rightLegYawDegrees,
        float rightLegRollDegrees,
        float leftLegPitchDegrees,
        float leftLegYawDegrees,
        float leftLegRollDegrees,
        float headPitchDegrees,
        float headYawDegrees,
        float headRollDegrees,
        float bodyPitchDegrees,
        float bodyYawDegrees,
        float bodyRollDegrees,
        float translateY,
        float renderPitchDegrees,
        float renderYawDegrees,
        float renderRollDegrees,
        float waveAmplitudeDegrees,
        float waveRightArmPitchAmplitudeDegrees,
        float waveLeftArmPitchAmplitudeDegrees,
        float waveRightArmYawAmplitudeDegrees,
        float waveLeftArmYawAmplitudeDegrees,
        float waveRightArmRollAmplitudeDegrees,
        float waveLeftArmRollAmplitudeDegrees,
        float waveRightLegPitchAmplitudeDegrees,
        float waveLeftLegPitchAmplitudeDegrees,
        float waveRightLegYawAmplitudeDegrees,
        float waveLeftLegYawAmplitudeDegrees,
        float waveRightLegRollAmplitudeDegrees,
        float waveLeftLegRollAmplitudeDegrees,
        float waveHeadPitchAmplitudeDegrees,
        float waveHeadYawAmplitudeDegrees,
        float waveHeadRollAmplitudeDegrees,
        float waveBodyPitchAmplitudeDegrees,
        float waveBodyYawAmplitudeDegrees,
        float waveBodyRollAmplitudeDegrees,
        float waveRenderPitchAmplitudeDegrees,
        float waveRenderYawAmplitudeDegrees,
        float waveRenderRollAmplitudeDegrees,
        float wavePitchSpinDegrees,
        float waveYawSpinDegrees,
        float waveRollSpinDegrees,
        float waveTranslateYAmplitude,
        float waveSpeed,
        boolean lockBody
) {
    public static final EmoteAnimation DEFAULT = new EmoteAnimation(
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

    public static EmoteAnimation fromJson(JsonObject root) {
        if (root == null) return DEFAULT;
        JsonObject rightArm = object(root, "rightArm");
        JsonObject leftArm = object(root, "leftArm");
        JsonObject rightLeg = object(root, "rightLeg");
        JsonObject leftLeg = object(root, "leftLeg");
        JsonObject head = object(root, "head");
        JsonObject body = object(root, "body");
        JsonObject wave = object(root, "wave");
        return new EmoteAnimation(
                getInt(root, "durationTicks", DEFAULT.durationTicks),
                getFloat(rightArm, "pitchDegrees", DEFAULT.rightArmPitchDegrees),
                getFloat(rightArm, "yawDegrees", DEFAULT.rightArmYawDegrees),
                getFloat(rightArm, "rollDegrees", DEFAULT.rightArmRollDegrees),
                getFloat(leftArm, "pitchDegrees", DEFAULT.leftArmPitchDegrees),
                getFloat(leftArm, "yawDegrees", DEFAULT.leftArmYawDegrees),
                getFloat(leftArm, "rollDegrees", DEFAULT.leftArmRollDegrees),
                getFloat(rightLeg, "pitchDegrees", DEFAULT.rightLegPitchDegrees),
                getFloat(rightLeg, "yawDegrees", DEFAULT.rightLegYawDegrees),
                getFloat(rightLeg, "rollDegrees", DEFAULT.rightLegRollDegrees),
                getFloat(leftLeg, "pitchDegrees", DEFAULT.leftLegPitchDegrees),
                getFloat(leftLeg, "yawDegrees", DEFAULT.leftLegYawDegrees),
                getFloat(leftLeg, "rollDegrees", DEFAULT.leftLegRollDegrees),
                getFloat(head, "pitchDegrees", DEFAULT.headPitchDegrees),
                getFloat(head, "yawDegrees", DEFAULT.headYawDegrees),
                getFloat(head, "rollDegrees", DEFAULT.headRollDegrees),
                getFloat(body, "pitchDegrees", DEFAULT.bodyPitchDegrees),
                getFloat(body, "yawDegrees", DEFAULT.bodyYawDegrees),
                getFloat(body, "rollDegrees", DEFAULT.bodyRollDegrees),
                getFloat(root, "translateY", DEFAULT.translateY),
                getFloat(root, "renderPitchDegrees", DEFAULT.renderPitchDegrees),
                getFloat(root, "renderYawDegrees", DEFAULT.renderYawDegrees),
                getFloat(root, "renderRollDegrees", DEFAULT.renderRollDegrees),
                getFloat(wave, "amplitudeDegrees", DEFAULT.waveAmplitudeDegrees),
                getFloat(wave, "rightArmPitchAmplitudeDegrees", DEFAULT.waveRightArmPitchAmplitudeDegrees),
                getFloat(wave, "leftArmPitchAmplitudeDegrees", DEFAULT.waveLeftArmPitchAmplitudeDegrees),
                getFloat(wave, "rightArmYawAmplitudeDegrees", DEFAULT.waveRightArmYawAmplitudeDegrees),
                getFloat(wave, "leftArmYawAmplitudeDegrees", DEFAULT.waveLeftArmYawAmplitudeDegrees),
                getFloat(wave, "rightArmRollAmplitudeDegrees", getFloat(wave, "amplitudeDegrees", DEFAULT.waveRightArmRollAmplitudeDegrees)),
                getFloat(wave, "leftArmRollAmplitudeDegrees", DEFAULT.waveLeftArmRollAmplitudeDegrees),
                getFloat(wave, "rightLegPitchAmplitudeDegrees", DEFAULT.waveRightLegPitchAmplitudeDegrees),
                getFloat(wave, "leftLegPitchAmplitudeDegrees", DEFAULT.waveLeftLegPitchAmplitudeDegrees),
                getFloat(wave, "rightLegYawAmplitudeDegrees", DEFAULT.waveRightLegYawAmplitudeDegrees),
                getFloat(wave, "leftLegYawAmplitudeDegrees", DEFAULT.waveLeftLegYawAmplitudeDegrees),
                getFloat(wave, "rightLegRollAmplitudeDegrees", DEFAULT.waveRightLegRollAmplitudeDegrees),
                getFloat(wave, "leftLegRollAmplitudeDegrees", DEFAULT.waveLeftLegRollAmplitudeDegrees),
                getFloat(wave, "headPitchAmplitudeDegrees", DEFAULT.waveHeadPitchAmplitudeDegrees),
                getFloat(wave, "headYawAmplitudeDegrees", DEFAULT.waveHeadYawAmplitudeDegrees),
                getFloat(wave, "headRollAmplitudeDegrees", DEFAULT.waveHeadRollAmplitudeDegrees),
                getFloat(wave, "bodyPitchAmplitudeDegrees", DEFAULT.waveBodyPitchAmplitudeDegrees),
                getFloat(wave, "bodyYawAmplitudeDegrees", DEFAULT.waveBodyYawAmplitudeDegrees),
                getFloat(wave, "bodyRollAmplitudeDegrees", DEFAULT.waveBodyRollAmplitudeDegrees),
                getFloat(wave, "renderPitchAmplitudeDegrees", DEFAULT.waveRenderPitchAmplitudeDegrees),
                getFloat(wave, "renderYawAmplitudeDegrees", DEFAULT.waveRenderYawAmplitudeDegrees),
                getFloat(wave, "renderRollAmplitudeDegrees", DEFAULT.waveRenderRollAmplitudeDegrees),
                getFloat(wave, "pitchSpinDegrees", DEFAULT.wavePitchSpinDegrees),
                getFloat(wave, "yawSpinDegrees", DEFAULT.waveYawSpinDegrees),
                getFloat(wave, "rollSpinDegrees", DEFAULT.waveRollSpinDegrees),
                getFloat(wave, "translateYAmplitude", DEFAULT.waveTranslateYAmplitude),
                getFloat(wave, "speed", DEFAULT.waveSpeed),
                getBoolean(root, "lockBody", DEFAULT.lockBody)
        );
    }

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
