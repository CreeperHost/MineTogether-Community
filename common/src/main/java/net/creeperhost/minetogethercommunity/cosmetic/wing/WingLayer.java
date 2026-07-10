package net.creeperhost.minetogethercommunity.cosmetic.wing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticPreviewTime;
import net.creeperhost.minetogethercommunity.cosmetic.ModelPlacement;
import net.creeperhost.minetogethercommunity.cosmetic.renderstate.MineTogetherCosmeticRenderState;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

public class WingLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public WingLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;

        MineTogetherCosmeticRenderState cosmeticState = (MineTogetherCosmeticRenderState) state;
        String wingId = cosmeticState.minetogether$wingId();
        if (wingId.isEmpty()) return;

        Wing wing = WingRegistry.getLoaded(wingId);
        if (wing == null) return;

        boolean flying = state.isFallFlying || state.isAutoSpinAttack || state.fallFlyingTimeInTicks > 0.0F;
        WingAnimation animation = wing.animation();
        float speed = flying ? animation.flyingSpeed() : animation.idleSpeed();
        float flapDegrees = flying ? animation.flyingFlapDegrees() : animation.idleFlapDegrees();
        float animationAge = CosmeticPreviewTime.ageInTicks(state.ageInTicks);
        float flap = Mth.sin(animationAge * speed) * flapDegrees;
        boolean fullBright = cosmeticState.minetogether$fullBright();
        int renderLight = fullBright ? LightCoordsUtil.FULL_BRIGHT : lightCoords;

        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        WingPlacement placement = wing.placement();
        ModelPlacement transform = placement.transform();
        poseStack.translate(transform.xPixels() / 16.0F, transform.yPixels() / 16.0F, transform.zPixels() / 16.0F);
        poseStack.scale(transform.scale(), transform.scale(), transform.scale());

        renderWingSide(poseStack, submitNodeCollector, renderLight, fullBright, wing, placement, false, animation.baseSpreadDegrees() + flap * animation.flapScale());
        renderWingSide(poseStack, submitNodeCollector, renderLight, fullBright, wing, placement, true, animation.baseSpreadDegrees() + flap * animation.flapScale());

        poseStack.popPose();
    }

    private void renderWingSide(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int renderLight, boolean fullBright, Wing wing, WingPlacement placement, boolean mirrored, float flapAngle) {
        poseStack.pushPose();
        poseStack.translate((mirrored ? -placement.hingeXPixels() : placement.hingeXPixels()) / 16.0F, 0.0F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(mirrored ? flapAngle : -flapAngle));
        poseStack.mulPose(Axis.ZP.rotationDegrees(mirrored ? -placement.restTiltDegrees() : placement.restTiltDegrees()));
        if (mirrored) {
            poseStack.scale(-1.0F, 1.0F, 1.0F);
        }
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityCutout(wing.texture()),
                (pose, buffer) -> wing.model().render(pose, buffer, renderLight, TailPose.none(), fullBright)
        );
        poseStack.popPose();
    }
}
