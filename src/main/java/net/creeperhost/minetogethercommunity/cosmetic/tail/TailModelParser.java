package net.creeperhost.minetogethercommunity.cosmetic.tail;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public final class TailModelParser {

    private TailModelParser() {
    }

    public static List<TailElement> parse(JsonObject root) {
        List<TailElement> elements = new ArrayList<TailElement>();
        if (root == null || !root.has("elements") || !root.get("elements").isJsonArray()) return elements;

        for (JsonElement element : root.getAsJsonArray("elements")) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            String name = object.has("name") ? object.get("name").getAsString() : "?";
            float[] from = readVec3(object.getAsJsonArray("from"));
            float[] to = readVec3(object.getAsJsonArray("to"));
            TailRotation rotation = object.has("rotation") && object.get("rotation").isJsonObject()
                    ? readRotation(object.getAsJsonObject("rotation")) : null;

            TailFace north = null;
            TailFace south = null;
            TailFace east = null;
            TailFace west = null;
            TailFace up = null;
            TailFace down = null;
            if (object.has("faces") && object.get("faces").isJsonObject()) {
                JsonObject faces = object.getAsJsonObject("faces");
                north = readFace(faces, "north");
                south = readFace(faces, "south");
                east = readFace(faces, "east");
                west = readFace(faces, "west");
                up = readFace(faces, "up");
                down = readFace(faces, "down");
            }
            elements.add(new TailElement(name, from, to, north, south, east, west, up, down, rotation));
        }
        return elements;
    }

    private static TailRotation readRotation(JsonObject rotation) {
        float x = rotation.has("x") ? rotation.get("x").getAsFloat() : 0.0F;
        float y = rotation.has("y") ? rotation.get("y").getAsFloat() : 0.0F;
        float z = rotation.has("z") ? rotation.get("z").getAsFloat() : 0.0F;
        if (rotation.has("axis") && rotation.has("angle")) {
            float angle = rotation.get("angle").getAsFloat();
            String axis = rotation.get("axis").getAsString();
            if ("x".equals(axis)) x = angle;
            else if ("y".equals(axis)) y = angle;
            else if ("z".equals(axis)) z = angle;
        }
        return new TailRotation(readVec3(rotation.getAsJsonArray("origin")), x, y, z);
    }

    private static TailFace readFace(JsonObject faces, String direction) {
        if (!faces.has(direction) || !faces.get(direction).isJsonObject()) return null;
        JsonArray uv = faces.getAsJsonObject(direction).getAsJsonArray("uv");
        if (uv == null || uv.size() < 4) return null;
        return new TailFace(uv.get(0).getAsFloat(), uv.get(1).getAsFloat(), uv.get(2).getAsFloat(), uv.get(3).getAsFloat());
    }

    private static float[] readVec3(JsonArray array) {
        if (array == null || array.size() < 3) return new float[] {0.0F, 0.0F, 0.0F};
        return new float[] {array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }
}
