package net.creeperhost.minetogethercommunity.mixin.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticRenderHelper;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.creeperhost.minetogethercommunity.cosmetic.cape.CapeRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.entity.layers.CapeLayer")
public abstract class VanillaCapeLayerMixin {

    @Inject(at = @At("HEAD"), method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/PlayerRenderState;FF)V",
            cancellable = true)
    private void suppressForCustomCape(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            PlayerRenderState state, float yRot, float xRot, CallbackInfo ci) {
        CosmeticSelections cs = CosmeticRenderHelper.getSelectionsForState(state);
        if (cs != null) {
            if (cs.suppressVanillaCapeForPreview) {
                ci.cancel();
                return;
            }
            String capeId = cs.selectedCapeId;
            if (capeId != null && !capeId.isEmpty() && CapeRegistry.getLoaded(capeId) != null) {
                ci.cancel();
            }
        }
    }
}
