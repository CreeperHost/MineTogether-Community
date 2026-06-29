package net.creeperhost.minetogethercommunity.cosmetic.hat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.creeperhost.minetogethercommunity.cosmetic.renderstate.MineTogetherCosmeticRenderState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;

public class HatLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public HatLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;

        MineTogetherCosmeticRenderState cosmeticState = (MineTogetherCosmeticRenderState) state;
        String hatId = cosmeticState.minetogether$hatId();
        if (hatId.isEmpty()) return;

        Hat hat = HatRegistry.getLoaded(hatId);
        if (hat == null) return;

        int renderLight = cosmeticState.minetogether$fullBright() ? LightCoordsUtil.FULL_BRIGHT : lightCoords;

        poseStack.pushPose();
        getParentModel().head.translateAndRotate(poseStack);
        if (hat.isJsonModel() && hat.jsonModel() != null) {
            poseStack.scale(1.01F, 1.01F, 1.01F);
            poseStack.translate(-8.0F / 16.0F, -16.0F / 16.0F, -8.0F / 16.0F);
            submitNodeCollector.submitCustomGeometry(
                    poseStack,
                    RenderTypes.entityCutout(hat.texture()),
                    (pose, buffer) -> hat.jsonModel().render(pose, buffer, renderLight, net.creeperhost.minetogethercommunity.cosmetic.tail.TailPose.none(), cosmeticState.minetogether$fullBright())
            );
        } else {
            HatModel model = HatRegistry.getModel(hat);
            poseStack.scale(1.01F, 1.01F, 1.01F);
            poseStack.translate(0.0D, -1.5D, 0.0D);
            submitNodeCollector.submitModel(
                    model,
                    state,
                    poseStack,
                    RenderTypes.entityCutout(hat.texture()),
                    renderLight,
                    OverlayTexture.NO_OVERLAY,
                    -1,
                    null,
                    state.outlineColor,
                    null
            );
        }
        poseStack.popPose();
    }
}
