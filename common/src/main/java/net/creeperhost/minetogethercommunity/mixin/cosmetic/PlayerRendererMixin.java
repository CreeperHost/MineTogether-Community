package net.creeperhost.minetogethercommunity.mixin.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import net.creeperhost.minetogethercommunity.cosmetic.cape.CapeLayer;
import net.creeperhost.minetogethercommunity.cosmetic.hat.HatLayer;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailLayer;
import net.creeperhost.minetogethercommunity.cosmetic.wing.WingLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.entity.player.PlayerRenderer")
public abstract class PlayerRendererMixin {

    private static final float EMOTE_SHOULDER_PIVOT_Y = -1.25F;

    @Inject(at = @At("TAIL"), method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V")
    private void addCosmeticLayers(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        RenderLayerParent parent = (RenderLayerParent) (Object) this;
        ((LivingEntityRendererAccess) this).minetogether$addLayer(new HatLayer<>(parent));
        ((LivingEntityRendererAccess) this).minetogether$addLayer(new CapeLayer<>(parent));
        ((LivingEntityRendererAccess) this).minetogether$addLayer(new TailLayer<>(parent));
        ((LivingEntityRendererAccess) this).minetogether$addLayer(new WingLayer<>(parent));
    }

    @Inject(method = "scale(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("TAIL"))
    private void minetogether$applyEmoteOffset(AbstractClientPlayer player, PoseStack poseStack, float partialTick, CallbackInfo ci) {
        float ageInTicks = player.tickCount + partialTick;
        float translateY = EmotePlayer.renderTranslateY(player, ageInTicks);
        if (translateY != 0.0F) {
            poseStack.translate(0.0F, translateY, 0.0F);
        }
        float yaw = EmotePlayer.renderYaw(player, ageInTicks);
        if (yaw != 0.0F) {
            poseStack.mulPose(Axis.YP.rotation(yaw));
        }
        float roll = EmotePlayer.renderRoll(player, ageInTicks);
        if (roll != 0.0F) {
            poseStack.translate(0.0F, EMOTE_SHOULDER_PIVOT_Y, 0.0F);
            poseStack.mulPose(Axis.ZP.rotation(roll));
            poseStack.translate(0.0F, -EMOTE_SHOULDER_PIVOT_Y, 0.0F);
        }
        float pitch = EmotePlayer.renderPitch(player, ageInTicks);
        if (pitch != 0.0F) {
            poseStack.translate(0.0F, EMOTE_SHOULDER_PIVOT_Y, 0.0F);
            poseStack.mulPose(Axis.XP.rotation(pitch));
            poseStack.translate(0.0F, -EMOTE_SHOULDER_PIVOT_Y, 0.0F);
        }
    }
}
