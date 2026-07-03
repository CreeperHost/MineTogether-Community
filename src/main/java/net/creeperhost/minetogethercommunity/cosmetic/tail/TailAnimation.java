package net.creeperhost.minetogethercommunity.cosmetic.tail;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.creeperhost.minetogethercommunity.util.CompatMath;
import net.minecraft.client.entity.AbstractClientPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TailAnimation {

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
            Collections.<Element>emptyList()
    );

    private final int segments;
    private final float idlePeriodTicks;
    private final float walkPhaseScale;
    private final float passengerLiftDegrees;
    private final float verticalLagScale;
    private final float verticalLagMin;
    private final float verticalLagMax;
    private final float backwardLagScale;
    private final float backwardLagMin;
    private final float backwardLagMax;
    private final float sidewaysLagScale;
    private final float sidewaysLagMin;
    private final float sidewaysLagMax;
    private final float liftBackwardDivisor;
    private final float liftVerticalDivisor;
    private final float liftMin;
    private final float liftMax;
    private final float sideLagDivisor;
    private final float[] xLift;
    private final float[] xWalk;
    private final float[] xIdleCosAmplitude;
    private final float[] xIdleDelay;
    private final float ySideBase;
    private final float ySideStep;
    private final float yIdleCosAmplitude;
    private final float yWalkAmplitude;
    private final float yDelayStep;
    private final float[] zWalkCounter;
    private final List<Element> elements;

    public TailAnimation(int segments, float idlePeriodTicks, float walkPhaseScale, float passengerLiftDegrees,
                         float verticalLagScale, float verticalLagMin, float verticalLagMax,
                         float backwardLagScale, float backwardLagMin, float backwardLagMax,
                         float sidewaysLagScale, float sidewaysLagMin, float sidewaysLagMax,
                         float liftBackwardDivisor, float liftVerticalDivisor, float liftMin, float liftMax,
                         float sideLagDivisor, float[] xLift, float[] xWalk, float[] xIdleCosAmplitude,
                         float[] xIdleDelay, float ySideBase, float ySideStep, float yIdleCosAmplitude,
                         float yWalkAmplitude, float yDelayStep, float[] zWalkCounter, List<Element> elements) {
        this.segments = segments;
        this.idlePeriodTicks = idlePeriodTicks;
        this.walkPhaseScale = walkPhaseScale;
        this.passengerLiftDegrees = passengerLiftDegrees;
        this.verticalLagScale = verticalLagScale;
        this.verticalLagMin = verticalLagMin;
        this.verticalLagMax = verticalLagMax;
        this.backwardLagScale = backwardLagScale;
        this.backwardLagMin = backwardLagMin;
        this.backwardLagMax = backwardLagMax;
        this.sidewaysLagScale = sidewaysLagScale;
        this.sidewaysLagMin = sidewaysLagMin;
        this.sidewaysLagMax = sidewaysLagMax;
        this.liftBackwardDivisor = liftBackwardDivisor;
        this.liftVerticalDivisor = liftVerticalDivisor;
        this.liftMin = liftMin;
        this.liftMax = liftMax;
        this.sideLagDivisor = sideLagDivisor;
        this.xLift = xLift == null ? new float[0] : xLift;
        this.xWalk = xWalk == null ? new float[0] : xWalk;
        this.xIdleCosAmplitude = xIdleCosAmplitude == null ? new float[0] : xIdleCosAmplitude;
        this.xIdleDelay = xIdleDelay == null ? new float[0] : xIdleDelay;
        this.ySideBase = ySideBase;
        this.ySideStep = ySideStep;
        this.yIdleCosAmplitude = yIdleCosAmplitude;
        this.yWalkAmplitude = yWalkAmplitude;
        this.yDelayStep = yDelayStep;
        this.zWalkCounter = zWalkCounter == null ? new float[0] : zWalkCounter;
        this.elements = elements == null ? Collections.<Element>emptyList() : elements;
    }

    public boolean enabled() {
        return segments > 0;
    }

    public TailPose pose(AbstractClientPlayer player, float partialTicks, float ageInTicks) {
        if (!enabled()) return TailPose.none();

        float idleSeed = ageInTicks * (float) (Math.PI * 2.0D) / idlePeriodTicks;
        float walk = interpolate(player.prevDistanceWalkedModified, player.distanceWalkedModified, partialTicks);
        float bob = interpolate(player.prevCameraYaw, player.cameraYaw, partialTicks);
        float walkPhase = walk * walkPhaseScale;
        float walkWave = CompatMath.sin(walkPhase) * bob;
        float walkCounterWave = CompatMath.cos(walkPhase) * bob;

        float lift;
        float sideLag;
        if (player.isRiding()) {
            lift = (float) Math.toRadians(passengerLiftDegrees);
            sideLag = 0.0F;
        } else {
            double cloakX = interpolate(player.prevChasingPosX, player.chasingPosX, partialTicks) - interpolate(player.prevPosX, player.posX, partialTicks);
            double cloakY = interpolate(player.prevChasingPosY, player.chasingPosY, partialTicks) - interpolate(player.prevPosY, player.posY, partialTicks);
            double cloakZ = interpolate(player.prevChasingPosZ, player.chasingPosZ, partialTicks) - interpolate(player.prevPosZ, player.posZ, partialTicks);
            float bodyYaw = interpolate(player.prevRenderYawOffset, player.renderYawOffset, partialTicks);
            float sin = CompatMath.sin(bodyYaw * 0.017453292F);
            float back = -CompatMath.cos(bodyYaw * 0.017453292F);
            float verticalLag = CompatMath.clamp((float) cloakY * verticalLagScale, verticalLagMin, verticalLagMax);
            float backwardLag = CompatMath.clamp((float) (cloakX * sin + cloakZ * back) * backwardLagScale, backwardLagMin, backwardLagMax);
            float sidewaysLag = CompatMath.clamp((float) (cloakX * back - cloakZ * sin) * sidewaysLagScale, sidewaysLagMin, sidewaysLagMax);
            lift = CompatMath.clamp(backwardLag / liftBackwardDivisor + verticalLag / liftVerticalDivisor, liftMin, liftMax);
            sideLag = sidewaysLag / sideLagDivisor;
        }

        float[] x = new float[segments];
        float[] y = new float[segments];
        float[] z = new float[segments];
        for (int i = 0; i < segments; i++) {
            x[i] = lift * value(xLift, i) + walkWave * value(xWalk, i)
                    + CompatMath.cos(idleSeed - value(xIdleDelay, i)) * value(xIdleCosAmplitude, i);
            float delay = i * yDelayStep;
            y[i] = sideLag * (ySideBase + i * ySideStep)
                    + CompatMath.cos(idleSeed - delay) * yIdleCosAmplitude
                    + CompatMath.sin(walkPhase - delay) * bob * yWalkAmplitude;
            z[i] = walkCounterWave * value(zWalkCounter, i);
        }

        return new TailPose(cumulative(x), cumulative(y), cumulative(z));
    }

    public TailElementPose elementPose(float ageInTicks) {
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
        if (!root.has("elements") || !root.get("elements").isJsonArray()) return Collections.emptyList();
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
        return elements.isEmpty() ? Collections.<Element>emptyList() : Collections.unmodifiableList(elements);
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

    private static float interpolate(float previous, float current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    private static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
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
