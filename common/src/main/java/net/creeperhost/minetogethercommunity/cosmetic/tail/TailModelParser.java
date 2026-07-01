package net.creeperhost.minetogethercommunity.cosmetic.tail;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Parses a Minecraft block-model JSON object into a list of {@link TailElement}s. */
public class TailModelParser {

    public static List<TailElement> parse(JsonObject root) {
        List<TailElement> elements = new ArrayList<>();
        if (!root.has("elements")) return elements;

        for (JsonElement el : root.getAsJsonArray("elements")) {
            JsonObject obj = el.getAsJsonObject();
            String name = obj.has("name") ? obj.get("name").getAsString() : "?";
            float[] from = readVec3(obj.getAsJsonArray("from"));
            float[] to   = readVec3(obj.getAsJsonArray("to"));
            TailRotation rotation = obj.has("rotation") ? readRotation(obj.getAsJsonObject("rotation")) : null;

            TailFace north = null, south = null, east = null, west = null, up = null, down = null;
            if (obj.has("faces")) {
                JsonObject faces = obj.getAsJsonObject("faces");
                north = readFace(faces, "north");
                south = readFace(faces, "south");
                east  = readFace(faces, "east");
                west  = readFace(faces, "west");
                up    = readFace(faces, "up");
                down  = readFace(faces, "down");
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
            switch (rotation.get("axis").getAsString()) {
                case "x" -> x = angle;
                case "y" -> y = angle;
                case "z" -> z = angle;
            }
        }
        return new TailRotation(readVec3(rotation.getAsJsonArray("origin")), x, y, z);
    }

    private static TailFace readFace(JsonObject faces, String dir) {
        if (!faces.has(dir)) return null;
        JsonArray uv = faces.getAsJsonObject(dir).getAsJsonArray("uv");
        return new TailFace(
                uv.get(0).getAsFloat(),
                uv.get(1).getAsFloat(),
                uv.get(2).getAsFloat(),
                uv.get(3).getAsFloat());
    }

    private static float[] readVec3(JsonArray arr) {
        return new float[]{
                arr.get(0).getAsFloat(),
                arr.get(1).getAsFloat(),
                arr.get(2).getAsFloat()
        };
    }
}
