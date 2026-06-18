package net.creeperhost.minetogethercommunity.mixin.cosmetic;

import net.creeperhost.minetogethercommunity.cosmetic.cape.CapeLayer;
import net.creeperhost.minetogethercommunity.cosmetic.hat.HatLayer;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.entity.player.PlayerRenderer")
public abstract class PlayerRendererMixin {

    @Inject(at = @At("TAIL"), method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V")
    private void addCosmeticLayers(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        RenderLayerParent parent = (RenderLayerParent) (Object) this;
        ((LivingEntityRendererAccess) this).minetogether$addLayer(new HatLayer<>(parent));
        ((LivingEntityRendererAccess) this).minetogether$addLayer(new CapeLayer<>(parent));
        ((LivingEntityRendererAccess) this).minetogether$addLayer(new TailLayer<>(parent));
    }
}
