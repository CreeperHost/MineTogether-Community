package net.creeperhost.minetogethercommunity.cosmetic.render;

import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.entity.EntityLivingBase;

/** Positions held items from the emote-adjusted arm and whole-player transform. */
final class EmoteHeldItemLayer extends LayerHeldItem {

    private final RendererLivingEntity<?> renderer;

    EmoteHeldItemLayer(RendererLivingEntity<?> renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    @Override
    public void doRenderLayer(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
                              float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        EmotePlayer.ModelState modelState = null;
        if (entity instanceof AbstractClientPlayer && renderer.getMainModel() instanceof ModelBiped) {
            modelState = EmotePlayer.applyToModel((ModelBiped) renderer.getMainModel(),
                    (AbstractClientPlayer) entity, ageInTicks);
        }

        GlStateManager.pushMatrix();
        try {
            if (entity instanceof AbstractClientPlayer) {
                EmoteRenderTransforms.apply((AbstractClientPlayer) entity, ageInTicks);
            }
            super.doRenderLayer(entity, limbSwing, limbSwingAmount, partialTicks,
                    ageInTicks, netHeadYaw, headPitch, scale);
        } finally {
            GlStateManager.popMatrix();
            if (modelState != null) {
                modelState.restore((ModelBiped) renderer.getMainModel());
            }
        }
    }
}
