package net.creeperhost.minetogethercommunity.cosmetic.wing;

import com.google.gson.JsonObject;

public record WingAnimation(
        float idleSpeed,
        float idleFlapDegrees,
        float flyingSpeed,
        float flyingFlapDegrees,
        float baseSpreadDegrees,
        float flapScale
) {
    public static final WingAnimation NONE = new WingAnimation(0.0F, 0.0F, 0.0F, 0.0F, 18.0F, 0.35F);

    public static WingAnimation fromJson(JsonObject root) {
        if (root == null) return NONE;
        JsonObject idle = root.has("idle") && root.get("idle").isJsonObject() ? root.getAsJsonObject("idle") : new JsonObject();
        JsonObject flying = root.has("flying") && root.get("flying").isJsonObject() ? root.getAsJsonObject("flying") : new JsonObject();
        return new WingAnimation(
                getFloat(idle, "speed", NONE.idleSpeed),
                getFloat(idle, "flapDegrees", NONE.idleFlapDegrees),
                getFloat(flying, "speed", NONE.flyingSpeed),
                getFloat(flying, "flapDegrees", NONE.flyingFlapDegrees),
                getFloat(root, "baseSpreadDegrees", NONE.baseSpreadDegrees),
                getFloat(root, "flapScale", NONE.flapScale)
        );
    }

    private static float getFloat(JsonObject root, String key, float fallback) {
        if (root == null || !root.has(key) || !root.get(key).isJsonPrimitive()) return fallback;
        try {
            return root.get(key).getAsFloat();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
