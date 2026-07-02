package net.creeperhost.minetogethercommunity.cosmetic.tail;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record TailAnimation(
        int segments,
        float idlePeriodTicks,
        float walkPhaseScale,
        float passengerLiftDegrees,
        float verticalLagScale,
        float verticalLagMin,
        float verticalLagMax,
        float backwardLagScale,
        float backwardLagMin,
        float backwardLagMax,
        float sidewaysLagScale,
        float sidewaysLagMin,
        float sidewaysLagMax,
        float liftBackwardDivisor,
        float liftVerticalDivisor,
        float liftMin,
        float liftMax,
        float sideLagDivisor,
        float[] xLift,
        float[] xWalk,
        float[] xIdleCosAmplitude,
        float[] xIdleDelay,
        float ySideBase,
        float ySideStep,
        float yIdleCosAmplitude,
        float yWalkAmplitude,
        float yDelayStep,
        float[] zWalkCounter,
        List<Element> elements
) {
    public static final TailAnimation NONE = new TailAnimation(
            0,
            140.0F,
            6.0F,
            8.0F,
            10.0F,
            -6.0F,
            20.0F,
            100.0F,
            0.0F,
            40.0F,
            100.0F,
            -16.0F,
            16.0F,
            260.0F,
            500.0F,
            -0.05F,
            0.16F,
            220.0F,
            new float[0],
            new float[0],
            new float[0],
            new float[0],
            0.12F,
            0.025F,
            1.0F / 80.0F,
            0.022F,
            0.65F,
            new float[0],
            List.of()
    );

    public boolean enabled() {
        return segments > 0;
    }

    public TailPose pose(AvatarRenderState state) {
        if (!enabled()) return fallbackPose(state);

        float idleSeed = state.ageInTicks * (float) (Math.PI * 2.0D) / idlePeriodTicks;
        float walk = state.walkAnimationPos;
        float bob = state.walkAnimationSpeed;
        float walkPhase = walk * walkPhaseScale;
        float walkWave = Mth.sin(walkPhase) * bob;
        float walkCounterWave = Mth.cos(walkPhase) * bob;

        float lift = 0.0F;
        float sideLag = 0.0F;
        if (state.isPassenger) {
            lift = (float) Math.toRadians(passengerLiftDegrees);
        } else {
            float verticalLag = Mth.clamp(state.capeFlap * verticalLagScale, verticalLagMin, verticalLagMax);
            float backwardLag = Mth.clamp(state.capeLean * backwardLagScale, backwardLagMin, backwardLagMax);
            float sidewaysLag = Mth.clamp(state.capeLean2 * sidewaysLagScale, sidewaysLagMin, sidewaysLagMax);
            lift = Mth.clamp(backwardLag / liftBackwardDivisor + verticalLag / liftVerticalDivisor, liftMin, liftMax);
            sideLag = sidewaysLag / sideLagDivisor;
        }

        float[] x = new float[segments];
        float[] y = new float[segments];
        float[] z = new float[segments];
        for (int i = 0; i < segments; i++) {
            x[i] = lift * value(xLift, i) + walkWave * value(xWalk, i)
                    + Mth.cos(idleSeed - value(xIdleDelay, i)) * value(xIdleCosAmplitude, i);
            float delay = i * yDelayStep;
            y[i] = sideLag * (ySideBase + i * ySideStep)
                    + Mth.cos(idleSeed - delay) * yIdleCosAmplitude
                    + Mth.sin(walkPhase - delay) * bob * yWalkAmplitude;
            z[i] = walkCounterWave * value(zWalkCounter, i);
        }

        return new TailPose(cumulative(x), cumulative(y), cumulative(z));
    }

    private TailPose fallbackPose(AvatarRenderState state) {
        float idleSeed = state.ageInTicks * (float) (Math.PI * 2.0D) / 140.0F;
        float walk = state.walkAnimationPos;
        float bob = state.walkAnimationSpeed;
        float walkPhase = walk * 6.0F;
        float walkWave = Mth.sin(walkPhase) * bob;
        float walkCounterWave = Mth.cos(walkPhase) * bob;

        float lift;
        float sideLag;
        if (state.isPassenger) {
            lift = (float) Math.toRadians(8.0F);
            sideLag = 0.0F;
        } else {
            float verticalLag = state.capeFlap;
            float backwardLag = Mth.clamp(state.capeLean, 0.0F, 40.0F);
            float sidewaysLag = state.capeLean2;
            lift = Mth.clamp(backwardLag / 260.0F + verticalLag / 500.0F, -0.05F, 0.16F);
            sideLag = sidewaysLag / 220.0F;
        }

        float[] x = new float[6];
        float[] y = new float[6];
        float[] z = new float[6];
        x[0] = lift * 0.28F + walkWave * 0.012F;
        x[1] = lift * 0.24F + walkWave * 0.010F;
        x[2] = lift * 0.18F + Mth.cos(idleSeed - 2.0F) / 110.0F;
        x[3] = -lift * 0.08F + Mth.cos(idleSeed - 3.0F) / 95.0F;
        x[4] = -lift * 0.10F + Mth.cos(idleSeed - 4.0F) / 95.0F;
        x[5] = -lift * 0.12F + Mth.cos(idleSeed - 5.0F) / 95.0F;

        for (int i = 0; i < y.length; i++) {
            float delay = i * 0.65F;
            y[i] = sideLag * (0.12F + i * 0.025F)
                    + Mth.cos(idleSeed - delay) / 80.0F
                    + Mth.sin(walkPhase - delay) * bob * 0.022F;
        }
        z[3] = walkCounterWave * 0.004F;
        z[4] = walkCounterWave * 0.005F;
        z[5] = walkCounterWave * 0.006F;

        return new TailPose(cumulative(x), cumulative(y), cumulative(z));
    }

    public TailElementPose elementPose(float ageInTicks) {
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
        return rotations.isEmpty() ? TailElementPose.none() : new TailElementPose(rotations);
    }

    public static TailAnimation fromJson(JsonObject root) {
        if (root == null) return NONE;
        int segments = getInt(root, "segments", 0);
        return new TailAnimation(
                segments,
                getFloat(root, "idlePeriodTicks", NONE.idlePeriodTicks),
                getFloat(root, "walkPhaseScale", NONE.walkPhaseScale),
                getFloat(root, "passengerLiftDegrees", NONE.passengerLiftDegrees),
                getFloat(root, "verticalLagScale", NONE.verticalLagScale),
                getFloat(root, "verticalLagMin", NONE.verticalLagMin),
                getFloat(root, "verticalLagMax", NONE.verticalLagMax),
                getFloat(root, "backwardLagScale", NONE.backwardLagScale),
                getFloat(root, "backwardLagMin", NONE.backwardLagMin),
                getFloat(root, "backwardLagMax", NONE.backwardLagMax),
                getFloat(root, "sidewaysLagScale", NONE.sidewaysLagScale),
                getFloat(root, "sidewaysLagMin", NONE.sidewaysLagMin),
                getFloat(root, "sidewaysLagMax", NONE.sidewaysLagMax),
                getFloat(root, "liftBackwardDivisor", NONE.liftBackwardDivisor),
                getFloat(root, "liftVerticalDivisor", NONE.liftVerticalDivisor),
                getFloat(root, "liftMin", NONE.liftMin),
                getFloat(root, "liftMax", NONE.liftMax),
                getFloat(root, "sideLagDivisor", NONE.sideLagDivisor),
                readFloatArray(root, "xLift", segments),
                readFloatArray(root, "xWalk", segments),
                readFloatArray(root, "xIdleCosAmplitude", segments),
                readFloatArray(root, "xIdleDelay", segments),
                getFloat(root, "ySideBase", NONE.ySideBase),
                getFloat(root, "ySideStep", NONE.ySideStep),
                getFloat(root, "yIdleCosAmplitude", NONE.yIdleCosAmplitude),
                getFloat(root, "yWalkAmplitude", NONE.yWalkAmplitude),
                getFloat(root, "yDelayStep", NONE.yDelayStep),
                readFloatArray(root, "zWalkCounter", segments),
                readElements(root)
        );
    }

    private static List<Element> readElements(JsonObject root) {
        if (!root.has("elements") || !root.get("elements").isJsonArray()) return List.of();
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
        return elements.isEmpty() ? List.of() : List.copyOf(elements);
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

    private static float[] cumulative(float[] values) {
        float[] cumulative = new float[values.length];
        float total = 0.0F;
        for (int i = 0; i < values.length; i++) {
            total += values[i];
            cumulative[i] = total;
        }
        return cumulative;
    }

    private static float value(float[] values, int index) {
        return index >= 0 && index < values.length ? values[index] : 0.0F;
    }

    private static float[] readFloatArray(JsonObject root, String key, int size) {
        float[] values = new float[Math.max(0, size)];
        if (!root.has(key) || !root.get(key).isJsonArray()) return values;
        JsonArray array = root.getAsJsonArray(key);
        for (int i = 0; i < values.length && i < array.size(); i++) {
            try {
                values[i] = array.get(i).getAsFloat();
            } catch (Exception ignored) {
                values[i] = 0.0F;
            }
        }
        return values;
    }

    private static int getInt(JsonObject root, String key, int fallback) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) return fallback;
        try {
            return root.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
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
