package net.minecraft.client.renderer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public final class GlStateManager {
    private GlStateManager() {
    }

    public static void pushMatrix() {
        GL11.glPushMatrix();
    }

    public static void popMatrix() {
        GL11.glPopMatrix();
    }

    public static void translate(double x, double y, double z) {
        GL11.glTranslated(x, y, z);
    }

    public static void translate(float x, float y, float z) {
        GL11.glTranslatef(x, y, z);
    }

    public static void scale(double x, double y, double z) {
        GL11.glScaled(x, y, z);
    }

    public static void scale(float x, float y, float z) {
        GL11.glScalef(x, y, z);
    }

    public static void rotate(float angle, float x, float y, float z) {
        GL11.glRotatef(angle, x, y, z);
    }

    public static void color(float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
    }

    public static void enableBlend() {
        GL11.glEnable(GL11.GL_BLEND);
    }

    public static void disableBlend() {
        GL11.glDisable(GL11.GL_BLEND);
    }

    public static void enableAlpha() {
        GL11.glEnable(GL11.GL_ALPHA_TEST);
    }

    public static void disableAlpha() {
        GL11.glDisable(GL11.GL_ALPHA_TEST);
    }

    public static void enableDepth() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    public static void disableDepth() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    public static void enableTexture2D() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    public static void disableTexture2D() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    public static void enableLighting() {
        GL11.glEnable(GL11.GL_LIGHTING);
    }

    public static void disableLighting() {
        GL11.glDisable(GL11.GL_LIGHTING);
    }

    public static void enableRescaleNormal() {
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
    }

    public static void disableRescaleNormal() {
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
    }

    public static void enableColorMaterial() {
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
    }

    public static void disableColorMaterial() {
        GL11.glDisable(GL11.GL_COLOR_MATERIAL);
    }

    public static void tryBlendFuncSeparate(int srcFactor, int dstFactor, int srcFactorAlpha, int dstFactorAlpha) {
        OpenGlHelper.glBlendFunc(srcFactor, dstFactor, srcFactorAlpha, dstFactorAlpha);
    }

    public static void blendFunc(SourceFactor srcFactor, DestFactor dstFactor) {
        GL11.glBlendFunc(srcFactor.factor, dstFactor.factor);
    }

    public static void blendFunc(int srcFactor, int dstFactor) {
        GL11.glBlendFunc(srcFactor, dstFactor);
    }

    public static void setActiveTexture(int texture) {
        OpenGlHelper.setActiveTexture(texture);
    }

    public enum SourceFactor {
        ZERO(GL11.GL_ZERO),
        ONE(GL11.GL_ONE),
        SRC_ALPHA(GL11.GL_SRC_ALPHA);

        private final int factor;

        SourceFactor(int factor) {
            this.factor = factor;
        }
    }

    public enum DestFactor {
        ZERO(GL11.GL_ZERO),
        ONE(GL11.GL_ONE),
        ONE_MINUS_SRC_ALPHA(GL11.GL_ONE_MINUS_SRC_ALPHA);

        private final int factor;

        DestFactor(int factor) {
            this.factor = factor;
        }
    }
}
