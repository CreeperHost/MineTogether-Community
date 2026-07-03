package net.creeperhost.minetogethercommunity.cosmetic.hat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailElementPose;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HatAnimation {

    public static final HatAnimation NONE = new HatAnimation(Collections.<Element>emptyList());

    private final List<Element> elements;

    public HatAnimation(List<Element> elements) {
        this.elements = elements == null ? Collections.<Element>emptyList() : elements;
    }

    public TailElementPose pose(float ageInTicks) {
        if (elements.isEmpty()) return TailElementPose.none();
        Map<String, float[]> rotations = new HashMap<String, float[]>();
        for (Element element : elements) {
            float wave = (float) Math.sin(ageInTicks * element.speed() + element.phase());
            float x = radians(element.xDegrees() + wave * element.waveXDegrees());
            float y = radians(element.yDegrees() + wave * element.waveYDegrees());
            float z = radians(element.zDegrees() + wave * element.waveZDegrees());
            for (String name : element.names()) {
                rotations.put(name, new float[] {x, y, z});
            }
        }
        return new TailElementPose(rotations);
    }

    public static HatAnimation fromJson(JsonObject root) {
        if (root == null || !root.has("elements") || !root.get("elements").isJsonArray()) return NONE;
        List<Element> elements = new ArrayList<Element>();
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
        return elements.isEmpty() ? NONE : new HatAnimation(Collections.unmodifiableList(elements));
    }

    private static List<String> readNames(JsonObject obj) {
        if (obj.has("names") && obj.get("names").isJsonArray()) {
            List<String> names = new ArrayList<String>();
            JsonArray array = obj.getAsJsonArray("names");
            for (JsonElement el : array) {
                if (el.isJsonPrimitive()) names.add(el.getAsString());
            }
            return names;
        }
        if (obj.has("name") && obj.get("name").isJsonPrimitive()) {
            return Collections.singletonList(obj.get("name").getAsString());
        }
        return Collections.emptyList();
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

    public static class Element {
        private final List<String> names;
        private final float xDegrees;
        private final float yDegrees;
        private final float zDegrees;
        private final float waveXDegrees;
        private final float waveYDegrees;
        private final float waveZDegrees;
        private final float speed;
        private final float phase;

        public Element(List<String> names, float xDegrees, float yDegrees, float zDegrees,
                       float waveXDegrees, float waveYDegrees, float waveZDegrees, float speed, float phase) {
            this.names = names == null ? Collections.<String>emptyList() : names;
            this.xDegrees = xDegrees;
            this.yDegrees = yDegrees;
            this.zDegrees = zDegrees;
            this.waveXDegrees = waveXDegrees;
            this.waveYDegrees = waveYDegrees;
            this.waveZDegrees = waveZDegrees;
            this.speed = speed;
            this.phase = phase;
        }

        public List<String> names() { return names; }
        public float xDegrees() { return xDegrees; }
        public float yDegrees() { return yDegrees; }
        public float zDegrees() { return zDegrees; }
        public float waveXDegrees() { return waveXDegrees; }
        public float waveYDegrees() { return waveYDegrees; }
        public float waveZDegrees() { return waveZDegrees; }
        public float speed() { return speed; }
        public float phase() { return phase; }
    }
}
