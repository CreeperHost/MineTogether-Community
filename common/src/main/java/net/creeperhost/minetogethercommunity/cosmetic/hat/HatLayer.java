package net.creeperhost.minetogethercommunity.cosmetic.hat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticRenderHelper;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

public class HatLayer extends RenderLayer<PlayerRenderState, PlayerModel> {

    public HatLayer(RenderLayerParent<PlayerRenderState, ?> renderer) {
        //noinspection unchecked
        super((RenderLayerParent<PlayerRenderState, PlayerModel>) renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       PlayerRenderState state, float yRot, float xRot) {
        CosmeticSelections cs = CosmeticRenderHelper.getSelectionsForState(state);
        if (cs == null) return;
        String hatId = cs.selectedHatId;
        if (hatId == null || hatId.isEmpty()) return;

        Hat hat = HatRegistry.getLoaded(hatId);
        if (hat == null) return;
        int renderLight = CosmeticSelections.instance().fullBrightPreview ? LightTexture.FULL_BRIGHT : packedLight;

        poseStack.pushPose();
        getParentModel().head.translateAndRotate(poseStack);
        if (hat.isJsonModel() && hat.jsonModel() != null) {
            poseStack.scale(1.01F, 1.01F, 1.01F);
            poseStack.translate(-8.0F / 16.0F, -16.0F / 16.0F, -8.0F / 16.0F);
            hat.jsonModel().render(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(hat.texture())), renderLight);
        } else {
            poseStack.scale(1.01f, 1.01f, 1.01f);
            poseStack.translate(0.0D, -1.5D, 0.0D);
            HatModel model = HatRegistry.getModel(hat);
            model.renderHat(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(hat.texture())), renderLight, OverlayTexture.NO_OVERLAY);
        }
        poseStack.popPose();
    }
}
