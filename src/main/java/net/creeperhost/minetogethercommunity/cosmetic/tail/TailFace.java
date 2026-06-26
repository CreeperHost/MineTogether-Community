package net.creeperhost.minetogethercommunity.cosmetic.tail;

public class TailFace {

    private final float u0;
    private final float v0;
    private final float u1;
    private final float v1;

    public TailFace(float u0, float v0, float u1, float v1) {
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
    }

    public float u0() { return u0; }
    public float v0() { return v0; }
    public float u1() { return u1; }
    public float v1() { return v1; }
}
