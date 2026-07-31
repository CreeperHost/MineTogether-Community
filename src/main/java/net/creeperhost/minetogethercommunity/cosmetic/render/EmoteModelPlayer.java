package net.creeperhost.minetogethercommunity.cosmetic.render;

import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;

/** A player model that applies an emote after vanilla calculates its limb rotations. */
final class EmoteModelPlayer extends ModelPlayer {

    private EmotePlayer.ModelState emoteState;

    EmoteModelPlayer(boolean smallArms) {
        super(0.0F, smallArms);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        if (emoteState != null) {
            emoteState.restore(this);
            emoteState = null;
        }
        GlStateManager.pushMatrix();
        try {
            if (entity instanceof AbstractClientPlayer) {
                EmoteRenderTransforms.apply((AbstractClientPlayer) entity, ageInTicks);
            }
            super.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        } finally {
            try {
                if (emoteState != null) {
                    emoteState.restore(this);
                    emoteState = null;
                }
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
}
