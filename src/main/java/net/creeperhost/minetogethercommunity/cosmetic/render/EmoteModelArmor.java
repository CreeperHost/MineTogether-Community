package net.creeperhost.minetogethercommunity.cosmetic.render;

import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;

/** An armor model that reapplies the active emote after vanilla calculates its limb rotations. */
final class EmoteModelArmor extends ModelBiped {

    private EmotePlayer.ModelState emoteState;

    EmoteModelArmor(float modelSize) {
        super(modelSize);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        if (emoteState != null) {
            emoteState.restore(this);
            emoteState = null;
        }
        try {
            super.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        } finally {
            if (emoteState != null) {
                emoteState.restore(this);
                emoteState = null;
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
