package net.creeperhost.minetogethercommunity.cosmetic.emote;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.entity.Entity;

public class EmoteModelPlayer extends ModelPlayer {

    public EmoteModelPlayer(float modelSize, boolean smallArmsIn) {
        super(modelSize, smallArmsIn);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch, float scaleFactor, Entity entityIn) {
        super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor, entityIn);
        if (entityIn instanceof AbstractClientPlayer) {
            EmotePlayer.applyPoseToModel(this, (AbstractClientPlayer) entityIn, ageInTicks);
        }
    }
}
