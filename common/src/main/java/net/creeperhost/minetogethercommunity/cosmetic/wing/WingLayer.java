package net.creeperhost.minetogethercommunity.cosmetic.wing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
        float flap = Mth.sin(state.ageInTicks * speed) * flapDegrees;
        boolean fullBright = cosmeticState.minetogether$fullBright();
        int renderLight = fullBright ? LightCoordsUtil.FULL_BRIGHT : lightCoords;

        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        poseStack.translate(0.0F, -7.0F / 16.0F, 4.2F / 16.0F);
        poseStack.scale(0.58F, 0.58F, 0.58F);

        renderWingSide(poseStack, submitNodeCollector, renderLight, fullBright, wing, false, animation.baseSpreadDegrees() + flap * animation.flapScale());
        renderWingSide(poseStack, submitNodeCollector, renderLight, fullBright, wing, true, animation.baseSpreadDegrees() + flap * animation.flapScale());

        poseStack.popPose();
    }

    private void renderWingSide(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int renderLight, boolean fullBright, Wing wing, boolean mirrored, float flapAngle) {
        poseStack.pushPose();
        poseStack.translate(mirrored ? -2.4F / 16.0F : 2.4F / 16.0F, 0.0F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(mirrored ? flapAngle : -flapAngle));
        poseStack.mulPose(Axis.ZP.rotationDegrees(mirrored ? -27.0F : 27.0F));
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
