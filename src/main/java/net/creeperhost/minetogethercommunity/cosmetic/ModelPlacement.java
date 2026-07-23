package net.creeperhost.minetogethercommunity.cosmetic;

import com.google.gson.JsonObject;

public final class ModelPlacement {
    public static final ModelPlacement NONE = new ModelPlacement(0.0F, 0.0F, 0.0F, 1.0F);
    private final float xPixels;
    private final float yPixels;
    private final float zPixels;
    private final float scale;

    public ModelPlacement(float xPixels, float yPixels, float zPixels, float scale) {
        this.xPixels = xPixels;
        this.yPixels = yPixels;
        this.zPixels = zPixels;
        this.scale = scale;
    }

    public static ModelPlacement fromMetadata(JsonObject metadata, ModelPlacement fallback) {
        JsonObject placement = placementObject(metadata);
        if (placement == null) return fallback;
        return new ModelPlacement(getFloat(placement, "x", fallback.xPixels),
                getFloat(placement, "y", fallback.yPixels),
                getFloat(placement, "z", fallback.zPixels),
                Math.max(0.01F, getFloat(placement, "scale", fallback.scale)));
    }

    public static JsonObject placementObject(JsonObject metadata) {
        return metadata != null && metadata.has("placement") && metadata.get("placement").isJsonObject()
                ? metadata.getAsJsonObject("placement") : null;
    }

    public static float getFloat(JsonObject object, String key, float fallback) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return fallback;
        try { return object.get(key).getAsFloat(); } catch (Exception ignored) { return fallback; }
    }

    public float xPixels() { return xPixels; }
    public float yPixels() { return yPixels; }
    public float zPixels() { return zPixels; }
    public float scale() { return scale; }
}
