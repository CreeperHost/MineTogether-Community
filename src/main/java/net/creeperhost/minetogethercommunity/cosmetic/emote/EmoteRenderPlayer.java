package net.creeperhost.minetogethercommunity.cosmetic.emote;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;

public class EmoteRenderPlayer extends RenderPlayer {

    public EmoteRenderPlayer(RenderManager renderManager, boolean useSmallArms) {
        super(renderManager, useSmallArms);
        this.mainModel = new EmoteModelPlayer(0.0F, useSmallArms);
    }

    @Override
    protected void preRenderCallback(AbstractClientPlayer player, float partialTickTime) {
        super.preRenderCallback(player, partialTickTime);
        float ageInTicks = player.ticksExisted + partialTickTime;
        EmoteRenderTransform.apply(
                EmotePlayer.renderTranslateY(player, ageInTicks),
                EmotePlayer.renderPitch(player, ageInTicks),
                EmotePlayer.renderYaw(player, ageInTicks),
                EmotePlayer.renderRoll(player, ageInTicks)
        );
    }
}
