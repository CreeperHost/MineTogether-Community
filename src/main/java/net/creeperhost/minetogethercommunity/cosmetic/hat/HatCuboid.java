package net.creeperhost.minetogethercommunity.cosmetic.hat;

public class HatCuboid {

    private final float pivotX;
    private final float pivotY;
    private final float pivotZ;
    private final float rotX;
    private final float rotY;
    private final float rotZ;
    private final float x;
    private final float y;
    private final float z;
    private final float sizeX;
    private final float sizeY;
    private final float sizeZ;
    private final int texU;
    private final int texV;

    public HatCuboid(float pivotX, float pivotY, float pivotZ, float rotX, float rotY, float rotZ,
                     float x, float y, float z, float sizeX, float sizeY, float sizeZ, int texU, int texV) {
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
        this.rotX = rotX;
        this.rotY = rotY;
        this.rotZ = rotZ;
        this.x = x;
        this.y = y;
        this.z = z;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.texU = texU;
        this.texV = texV;
    }

    public float pivotX() { return pivotX; }
    public float pivotY() { return pivotY; }
    public float pivotZ() { return pivotZ; }
    public float rotX() { return rotX; }
    public float rotY() { return rotY; }
    public float rotZ() { return rotZ; }
    public float x() { return x; }
    public float y() { return y; }
    public float z() { return z; }
    public float sizeX() { return sizeX; }
    public float sizeY() { return sizeY; }
    public float sizeZ() { return sizeZ; }
    public int texU() { return texU; }
    public int texV() { return texV; }
}
