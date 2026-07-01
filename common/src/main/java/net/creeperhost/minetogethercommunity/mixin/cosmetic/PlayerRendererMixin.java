package net.creeperhost.minetogethercommunity.mixin.cosmetic;

import net.creeperhost.minetogethercommunity.cosmetic.cape.CapeLayer;
import net.creeperhost.minetogethercommunity.cosmetic.hat.HatLayer;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailLayer;
import net.creeperhost.minetogethercommunity.cosmetic.wing.WingLayer;
import net.creeperhost.minetogethercommunity.cosmetic.PlayerRenderStateAccess;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.entity.player.PlayerRenderer")
public abstract class PlayerRendererMixin {

    @Inject(at = @At("TAIL"), method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V")
    private void addCosmeticLayers(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        @SuppressWarnings("unchecked")
        RenderLayerParent<PlayerRenderState, ?> parent = (RenderLayerParent<PlayerRenderState, ?>) (Object) this;
        ((LivingEntityRendererAccess) this).minetogether$addLayer(new HatLayer(parent));
        ((LivingEntityRendererAccess) this).minetogether$addLayer(new CapeLayer(parent));
        ((LivingEntityRendererAccess) this).minetogether$addLayer(new TailLayer(parent));
        ((LivingEntityRendererAccess) this).minetogether$addLayer(new WingLayer(parent));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
            at = @At("TAIL"))
    private void injectPlayerUUID(AbstractClientPlayer player, PlayerRenderState state, float partialTick, CallbackInfo ci) {
        ((PlayerRenderStateAccess) state).minetogether$setPlayerUUID(player.getUUID());
    }
}
