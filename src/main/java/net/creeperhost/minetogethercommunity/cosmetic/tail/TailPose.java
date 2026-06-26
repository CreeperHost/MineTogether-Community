package net.creeperhost.minetogethercommunity.cosmetic.tail;

public class TailPose {

    private final float[] x;
    private final float[] y;
    private final float[] z;

    public TailPose(float[] x, float[] y, float[] z) {
        this.x = x == null ? new float[0] : x;
        this.y = y == null ? new float[0] : y;
        this.z = z == null ? new float[0] : z;
    }

    public static TailPose none() {
        return new TailPose(new float[0], new float[0], new float[0]);
    }

    public float x(int index) {
        return index >= 0 && index < x.length ? x[index] : 0.0F;
    }

    public float y(int index) {
        return index >= 0 && index < y.length ? y[index] : 0.0F;
    }

    public float z(int index) {
        return index >= 0 && index < z.length ? z[index] : 0.0F;
    }
}
