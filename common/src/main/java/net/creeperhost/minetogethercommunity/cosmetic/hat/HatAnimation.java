package net.creeperhost.minetogethercommunity.cosmetic.hat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailElementPose;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record HatAnimation(List<Element> elements) {
    public static final HatAnimation NONE = new HatAnimation(List.of());

    public TailElementPose pose(float ageInTicks) {
        if (elements.isEmpty()) return TailElementPose.none();
        Map<String, float[]> rotations = new HashMap<>();
        for (Element element : elements) {
            float wave = (float) Math.sin(ageInTicks * element.speed() + element.phase());
            float x = radians(element.xDegrees() + wave * element.waveXDegrees());
            float y = radians(element.yDegrees() + wave * element.waveYDegrees());
            float z = radians(element.zDegrees() + wave * element.waveZDegrees());
            for (String name : element.names()) {
                rotations.put(name, new float[]{x, y, z});
            }
        }
        return new TailElementPose(rotations);
    }

    public static HatAnimation fromJson(JsonObject root) {
        if (root == null || !root.has("elements") || !root.get("elements").isJsonArray()) return NONE;
        List<Element> elements = new java.util.ArrayList<>();
        for (JsonElement el : root.getAsJsonArray("elements")) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            List<String> names = readNames(obj);
            if (names.isEmpty()) continue;
            elements.add(new Element(
                    names,
                    getFloat(obj, "xDegrees", 0.0F),
                    getFloat(obj, "yDegrees", 0.0F),
                    getFloat(obj, "zDegrees", 0.0F),
                    getFloat(obj, "waveXDegrees", 0.0F),
                    getFloat(obj, "waveYDegrees", 0.0F),
                    getFloat(obj, "waveZDegrees", 0.0F),
                    getFloat(obj, "speed", 0.0F),
                    getFloat(obj, "phase", 0.0F)
            ));
        }
        return elements.isEmpty() ? NONE : new HatAnimation(List.copyOf(elements));
    }

    private static List<String> readNames(JsonObject obj) {
        if (obj.has("names") && obj.get("names").isJsonArray()) {
            List<String> names = new java.util.ArrayList<>();
            JsonArray array = obj.getAsJsonArray("names");
            for (JsonElement el : array) {
                if (el.isJsonPrimitive()) names.add(el.getAsString());
            }
            return names;
        }
        if (obj.has("name") && obj.get("name").isJsonPrimitive()) {
            return List.of(obj.get("name").getAsString());
        }
        return List.of();
    }

    private static float getFloat(JsonObject root, String key, float fallback) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) return fallback;
        try {
            return root.get(key).getAsFloat();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float radians(float degrees) {
        return degrees * ((float) Math.PI / 180.0F);
    }

    public record Element(
            List<String> names,
            float xDegrees,
            float yDegrees,
            float zDegrees,
            float waveXDegrees,
            float waveYDegrees,
            float waveZDegrees,
            float speed,
            float phase
    ) {
    }
}
