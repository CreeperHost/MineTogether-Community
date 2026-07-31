package net.creeperhost.minetogethercommunity.cosmetic.render;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.entity.EntityLivingBase;

/** Keeps armor aligned with both limb poses and whole-body emote transforms. */
final class EmoteArmorLayer extends LayerBipedArmor {

    EmoteArmorLayer(RendererLivingEntity<?> renderer) {
        super(renderer);
    }

    @Override
    protected void initArmor() {
        modelLeggings = new EmoteModelArmor(0.5F);
        modelArmor = new EmoteModelArmor(1.0F);
    }

    @Override
    public void doRenderLayer(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
                              float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        GlStateManager.pushMatrix();
        try {
            if (entity instanceof AbstractClientPlayer) {
                EmoteRenderTransforms.apply((AbstractClientPlayer) entity, ageInTicks);
            }
            super.doRenderLayer(entity, limbSwing, limbSwingAmount, partialTicks,
                    ageInTicks, netHeadYaw, headPitch, scale);
        } finally {
            GlStateManager.popMatrix();
        }
    }
}
