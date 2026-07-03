package net.creeperhost.minetogethercommunity.cosmetic.tail;

import java.util.Collections;
import java.util.Map;

public class TailElementPose {

    private static final TailElementPose NONE = new TailElementPose(Collections.<String, float[]>emptyMap());

    private final Map<String, float[]> rotations;

    public TailElementPose(Map<String, float[]> rotations) {
        this.rotations = rotations == null ? Collections.<String, float[]>emptyMap() : rotations;
    }

    public static TailElementPose none() {
        return NONE;
    }

    public Map<String, float[]> rotations() {
        return rotations;
    }

    public float x(String name) {
        return axis(name, 0);
    }

    public float y(String name) {
        return axis(name, 1);
    }

    public float z(String name) {
        return axis(name, 2);
    }

    public boolean isEmpty() {
        return rotations.isEmpty();
    }

    private float axis(String name, int index) {
        float[] value = rotations.get(name);
        return value == null || value.length <= index ? 0.0F : value[index];
    }
}
