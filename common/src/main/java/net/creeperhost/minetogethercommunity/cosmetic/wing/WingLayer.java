package net.creeperhost.minetogethercommunity.cosmetic.wing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticRenderHelper;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.util.Mth;

public class WingLayer extends RenderLayer<PlayerRenderState, PlayerModel> {

    public WingLayer(RenderLayerParent<PlayerRenderState, ?> renderer) {
        //noinspection unchecked
        super((RenderLayerParent<PlayerRenderState, PlayerModel>) renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       PlayerRenderState state, float yRot, float xRot) {
        CosmeticSelections cs = CosmeticRenderHelper.getSelectionsForState(state);
        if (cs == null) return;
        String wingId = cs.selectedWingId;
        if (wingId == null || wingId.isEmpty()) return;

        Wing wing = WingRegistry.getLoaded(wingId);
        if (wing == null) return;

        boolean flying = state.isFallFlying || state.fallFlyingTimeInTicks > 0;
        WingAnimation animation = wing.animation();
        float speed = flying ? animation.flyingSpeed() : animation.idleSpeed();
        float flapDegrees = flying ? animation.flyingFlapDegrees() : animation.idleFlapDegrees();
        float flap = Mth.sin(state.ageInTicks * speed) * flapDegrees;
        int renderLight = CosmeticSelections.instance().fullBrightPreview ? LightTexture.FULL_BRIGHT : packedLight;

        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        poseStack.translate(0.0F, -7.0F / 16.0F, 4.2F / 16.0F);
        poseStack.scale(0.58F, 0.58F, 0.58F);

        renderWingSide(poseStack, bufferSource, renderLight, wing, false, animation.baseSpreadDegrees() + flap * animation.flapScale());
        renderWingSide(poseStack, bufferSource, renderLight, wing, true, animation.baseSpreadDegrees() + flap * animation.flapScale());

        poseStack.popPose();
    }

    private void renderWingSide(PoseStack poseStack, MultiBufferSource bufferSource, int renderLight, Wing wing, boolean mirrored, float flapAngle) {
        poseStack.pushPose();
        poseStack.translate(mirrored ? -2.4F / 16.0F : 2.4F / 16.0F, 0.0F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(mirrored ? flapAngle : -flapAngle));
        poseStack.mulPose(Axis.ZP.rotationDegrees(mirrored ? -27.0F : 27.0F));
        if (mirrored) {
            poseStack.scale(-1.0F, 1.0F, 1.0F);
        }
        wing.model().render(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(wing.texture())), renderLight);
        poseStack.popPose();
    }
}
