package net.creeperhost.minetogethercommunity.cosmetic.tail;

/** Per-segment tail animation offsets, in radians. */
public record TailPose(float[] x, float[] y, float[] z) {

    public static TailPose none() {
        return new TailPose(new float[0], new float[0], new float[0]);
    }

    public float x(int index) {
        return index < x.length ? x[index] : 0.0F;
    }

    public float y(int index) {
        return index < y.length ? y[index] : 0.0F;
    }

    public float z(int index) {
        return index < z.length ? z[index] : 0.0F;
    }
}
