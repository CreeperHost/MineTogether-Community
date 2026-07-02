package net.creeperhost.minetogethercommunity.cosmetic.tail;

import com.mojang.blaze3d.vertex.PoseStack;
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
        poseStack.translate(-8.0F / 16.0F, 2.0F / 16.0F, 2.0F / 16.0F);

        TailPose tailPose = tail.animation().pose(state);
        TailElementPose elementPose = tail.animation().elementPose(state.ageInTicks);
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
