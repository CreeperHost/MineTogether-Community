package net.creeperhost.minetogethercommunity.mixin.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import net.creeperhost.minetogethercommunity.cosmetic.cape.CapeRegistry;
import net.creeperhost.minetogethercommunity.cosmetic.renderstate.MineTogetherCosmeticRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeLayer.class)
public abstract class VanillaCapeLayerMixin {

    @Inject(at = @At("HEAD"), method = "submit", cancellable = true)
    private void suppressForCustomCape(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            AvatarRenderState state,
            float yRot,
            float xRot,
            CallbackInfo ci
    ) {
        MineTogetherCosmeticRenderState cosmeticState = (MineTogetherCosmeticRenderState) state;
        if (cosmeticState.minetogether$suppressVanillaCape()) {
            ci.cancel();
            return;
        }

        String capeId = cosmeticState.minetogether$capeId();
        if (!capeId.isEmpty() && CapeRegistry.getLoaded(capeId) != null) {
            ci.cancel();
        }
    }
}
