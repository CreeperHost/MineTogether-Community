package net.creeperhost.minetogethercommunity.cosmetic.render;

import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;

final class EmoteRenderTransforms {

    private EmoteRenderTransforms() {
    }

    static void apply(AbstractClientPlayer player, float ageInTicks) {
        float translateY = EmotePlayer.renderTranslateY(player, ageInTicks);
        float pitch = EmotePlayer.renderPitch(player, ageInTicks);
        float yaw = EmotePlayer.renderYaw(player, ageInTicks);
        float roll = EmotePlayer.renderRoll(player, ageInTicks);
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
