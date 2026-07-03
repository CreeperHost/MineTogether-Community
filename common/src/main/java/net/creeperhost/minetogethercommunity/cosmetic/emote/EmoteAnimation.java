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
        float waveAmplitudeDegrees,
        float waveRightArmPitchAmplitudeDegrees,
        float waveLeftArmPitchAmplitudeDegrees,
        float waveRightArmYawAmplitudeDegrees,
        float waveLeftArmYawAmplitudeDegrees,
        float waveRightArmRollAmplitudeDegrees,
        float waveLeftArmRollAmplitudeDegrees,
        float waveBodyYawAmplitudeDegrees,
        float waveBodyRollAmplitudeDegrees,
        float waveSpeed,
        boolean lockBody
) {
    public static final EmoteAnimation WAVE = new EmoteAnimation(
            80,
            -155.0F, 0.0F, 18.0F,
            0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F,
            0.0F,
            28.0F, 0.0F, 0.0F, 0.0F, 0.0F, 28.0F, 0.0F, 0.0F, 0.0F, 0.62F,
            false
    );

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
                getFloat(wave, "amplitudeDegrees", WAVE.waveAmplitudeDegrees),
                getFloat(wave, "rightArmPitchAmplitudeDegrees", WAVE.waveRightArmPitchAmplitudeDegrees),
                getFloat(wave, "leftArmPitchAmplitudeDegrees", WAVE.waveLeftArmPitchAmplitudeDegrees),
                getFloat(wave, "rightArmYawAmplitudeDegrees", WAVE.waveRightArmYawAmplitudeDegrees),
                getFloat(wave, "leftArmYawAmplitudeDegrees", WAVE.waveLeftArmYawAmplitudeDegrees),
                getFloat(wave, "rightArmRollAmplitudeDegrees", getFloat(wave, "amplitudeDegrees", WAVE.waveRightArmRollAmplitudeDegrees)),
                getFloat(wave, "leftArmRollAmplitudeDegrees", WAVE.waveLeftArmRollAmplitudeDegrees),
                getFloat(wave, "bodyYawAmplitudeDegrees", WAVE.waveBodyYawAmplitudeDegrees),
                getFloat(wave, "bodyRollAmplitudeDegrees", WAVE.waveBodyRollAmplitudeDegrees),
                getFloat(wave, "speed", WAVE.waveSpeed),
                getBoolean(root, "lockBody", WAVE.lockBody)
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
