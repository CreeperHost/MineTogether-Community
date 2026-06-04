package net.creeperhost.minetogethercommunity.mixin.cosmetic;

import net.creeperhost.minetogethercommunity.cosmetic.hat.HatLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.entity.player.PlayerRenderer")
public abstract class PlayerRendererMixin {

    @Inject(at = @At("TAIL"), method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V")
    private void addHatLayer(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        ((LivingEntityRendererAccess) this).minetogether$addLayer(new HatLayer<>((RenderLayerParent) (Object) this));
    }
}
