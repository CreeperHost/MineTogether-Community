package net.creeperhost.minetogethercommunity.cosmetic.tail;

import com.mojang.blaze3d.vertex.PoseStack;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticPreviewTime;
import net.creeperhost.minetogethercommunity.cosmetic.ModelPlacement;
import net.creeperhost.minetogethercommunity.cosmetic.renderstate.MineTogetherCosmeticRenderState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.LightCoordsUtil;

public class TailLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public TailLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;

        MineTogetherCosmeticRenderState cosmeticState = (MineTogetherCosmeticRenderState) state;
        String tailId = cosmeticState.minetogether$tailId();
        if (tailId.isEmpty()) return;

        Tail tail = TailRegistry.getLoaded(tailId);
        if (tail == null) return;

        poseStack.pushPose();

        getParentModel().body.translateAndRotate(poseStack);
        // All values in model-pixel units (same scale as entity model coords).
        // x: centre tail at block-model x=8 → -8; y: map block-model y=8 to body y=12 → 12-8=4;
        // z: start tail at body back face z=2 → +2.
        ModelPlacement placement = tail.placement();
        poseStack.translate(placement.xPixels() / 16.0F, placement.yPixels() / 16.0F, placement.zPixels() / 16.0F);
        poseStack.scale(placement.scale(), placement.scale(), placement.scale());

        float animationAge = CosmeticPreviewTime.ageInTicks(state.ageInTicks);
        TailPose tailPose = tail.animation().pose(state, animationAge);
        TailElementPose elementPose = tail.animation().elementPose(animationAge);
        boolean fullBright = cosmeticState.minetogether$fullBright();
        int renderLight = fullBright ? LightCoordsUtil.FULL_BRIGHT : lightCoords;
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityCutout(tail.texture()),
                (pose, buffer) -> tail.model().render(pose, buffer, renderLight, tailPose, elementPose, fullBright)
        );

        poseStack.popPose();
    }
}
