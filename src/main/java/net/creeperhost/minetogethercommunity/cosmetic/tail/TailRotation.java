package net.creeperhost.minetogethercommunity.cosmetic.tail;

public class TailRotation {

    private final float[] origin;
    private final float x;
    private final float y;
    private final float z;

    public TailRotation(float[] origin, float x, float y, float z) {
        this.origin = origin;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public float[] origin() { return origin; }
    public float x() { return x; }
    public float y() { return y; }
    public float z() { return z; }
}
