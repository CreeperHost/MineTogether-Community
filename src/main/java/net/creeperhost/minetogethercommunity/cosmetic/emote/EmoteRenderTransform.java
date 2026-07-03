package net.creeperhost.minetogethercommunity.cosmetic.emote;

import net.minecraft.client.renderer.GlStateManager;

public final class EmoteRenderTransform {

    private EmoteRenderTransform() {
    }

    public static void apply(float translateY, float pitch, float yaw, float roll) {
        if (translateY != 0.0F) {
            GlStateManager.translate(0.0F, translateY, 0.0F);
        }
        if (yaw != 0.0F) {
            GlStateManager.rotate(yaw * 180.0F / (float) Math.PI, 0.0F, 1.0F, 0.0F);
        }
        if (roll != 0.0F) {
            GlStateManager.translate(0.0F, -1.25F, 0.0F);
            GlStateManager.rotate(roll * 180.0F / (float) Math.PI, 0.0F, 0.0F, 1.0F);
            GlStateManager.translate(0.0F, 1.25F, 0.0F);
        }
        if (pitch != 0.0F) {
            GlStateManager.translate(0.0F, -1.25F, 0.0F);
            GlStateManager.rotate(pitch * 180.0F / (float) Math.PI, 1.0F, 0.0F, 0.0F);
            GlStateManager.translate(0.0F, 1.25F, 0.0F);
        }
    }
}
