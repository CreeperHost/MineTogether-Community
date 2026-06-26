package net.creeperhost.minetogethercommunity.cosmetic.wing;

import com.google.gson.JsonObject;

public class WingAnimation {

    public static final WingAnimation NONE = new WingAnimation(0.0F, 0.0F, 0.0F, 0.0F, 18.0F, 0.35F);

    private final float idleSpeed;
    private final float idleFlapDegrees;
    private final float flyingSpeed;
    private final float flyingFlapDegrees;
    private final float baseSpreadDegrees;
    private final float flapScale;

    public WingAnimation(float idleSpeed, float idleFlapDegrees, float flyingSpeed, float flyingFlapDegrees, float baseSpreadDegrees, float flapScale) {
        this.idleSpeed = idleSpeed;
        this.idleFlapDegrees = idleFlapDegrees;
        this.flyingSpeed = flyingSpeed;
        this.flyingFlapDegrees = flyingFlapDegrees;
        this.baseSpreadDegrees = baseSpreadDegrees;
        this.flapScale = flapScale;
    }

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
                getFloat(root, "flapScale", NONE.flapScale));
    }

    public float idleSpeed() { return idleSpeed; }
    public float idleFlapDegrees() { return idleFlapDegrees; }
    public float flyingSpeed() { return flyingSpeed; }
    public float flyingFlapDegrees() { return flyingFlapDegrees; }
    public float baseSpreadDegrees() { return baseSpreadDegrees; }
    public float flapScale() { return flapScale; }

    private static float getFloat(JsonObject root, String key, float fallback) {
        if (root == null || !root.has(key) || !root.get(key).isJsonPrimitive()) return fallback;
        try {
            return root.get(key).getAsFloat();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
