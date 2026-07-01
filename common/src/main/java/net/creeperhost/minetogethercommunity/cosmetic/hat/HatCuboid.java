package net.creeperhost.minetogethercommunity.cosmetic.hat;

public record HatCuboid(
        float pivotX, float pivotY, float pivotZ,
        float rotX,   float rotY,   float rotZ,
        float x,      float y,      float z,
        float sizeX,  float sizeY,  float sizeZ,
        int texU, int texV) {
}
