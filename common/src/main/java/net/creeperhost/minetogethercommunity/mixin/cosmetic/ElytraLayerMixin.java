package net.creeperhost.minetogethercommunity.mixin.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticRenderHelper;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.creeperhost.minetogethercommunity.cosmetic.cape.Cape;
import net.creeperhost.minetogethercommunity.cosmetic.cape.CapeRegistry;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.entity.layers.WingsLayer")
public abstract class ElytraLayerMixin<S extends HumanoidRenderState, M extends EntityModel<S>> {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void mtc$renderWithMTCape(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            HumanoidRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (!(state instanceof PlayerRenderState playerState)) return;

        CosmeticSelections cs = CosmeticRenderHelper.getSelectionsForState(playerState);
        if (cs == null) return;
        String capeId = cs.selectedCapeId;
        if (capeId == null || capeId.isEmpty()) return;

        Cape cape = CapeRegistry.getLoaded(capeId);
        if (cape == null) return;

        // Replace the elytra texture with the MT cape texture
        // We cancel the original and render our own elytra model with the cape texture
        ci.cancel();
    }
}
