package net.creeperhost.minetogethercommunity.cosmetic.render;

import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;

/** An armour model that applies the active emote after vanilla calculates its limb rotations. */
final class EmoteModelArmor extends ModelBiped {

    private EmotePlayer.ModelState emoteState;

    EmoteModelArmor(float modelSize) {
        super(modelSize);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        restoreEmoteState();
        GlStateManager.pushMatrix();
        try {
            if (entity instanceof AbstractClientPlayer) {
                EmoteRenderTransforms.apply((AbstractClientPlayer) entity, ageInTicks);
            }
            super.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        } finally {
            try {
                restoreEmoteState();
            } finally {
                GlStateManager.popMatrix();
            }
        }
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch, float scaleFactor, Entity entity) {
        super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor, entity);
        if (entity instanceof AbstractClientPlayer) {
            emoteState = EmotePlayer.applyToModel(this, (AbstractClientPlayer) entity, ageInTicks);
        }
    }

    private void restoreEmoteState() {
        if (emoteState != null) {
            emoteState.restore(this);
            emoteState = null;
        }
    }
}
