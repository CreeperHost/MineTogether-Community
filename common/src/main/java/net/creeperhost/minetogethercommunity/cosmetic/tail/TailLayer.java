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
import net.minecraft.util.Mth;

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

        TailPose tailPose = createTailPose(state);
        boolean fullBright = cosmeticState.minetogether$fullBright();
        int renderLight = fullBright ? LightCoordsUtil.FULL_BRIGHT : lightCoords;
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityCutout(tail.texture()),
                (pose, buffer) -> tail.model().render(pose, buffer, renderLight, tailPose, fullBright)
        );

        poseStack.popPose();
    }

    private TailPose createTailPose(AvatarRenderState state) {
        float idleSeed = state.ageInTicks * (float) (Math.PI * 2.0D) / 140.0F;
        float walk = state.walkAnimationPos;
        float bob = state.walkAnimationSpeed;
        float walkPhase = walk * 6.0F;
        float walkWave = Mth.sin(walkPhase) * bob;
        float walkCounterWave = Mth.cos(walkPhase) * bob;

        float lift;
        float sideLag;
        if (state.isPassenger) {
            lift = (float) Math.toRadians(8.0F);
            sideLag = 0.0F;
        } else {
            float verticalLag = state.capeFlap;
            float backwardLag = Mth.clamp(state.capeLean, 0.0F, 40.0F);
            float sidewaysLag = state.capeLean2;
            lift = Mth.clamp(backwardLag / 260.0F + verticalLag / 500.0F, -0.05F, 0.16F);
            sideLag = sidewaysLag / 220.0F;
        }

        float[] x = new float[6];
        float[] y = new float[6];
        float[] z = new float[6];
        x[0] = lift * 0.28F + walkWave * 0.012F;
        x[1] = lift * 0.24F + walkWave * 0.010F;
        x[2] = lift * 0.18F + Mth.cos(idleSeed - 2.0F) / 110.0F;
        x[3] = -lift * 0.08F + Mth.cos(idleSeed - 3.0F) / 95.0F;
        x[4] = -lift * 0.10F + Mth.cos(idleSeed - 4.0F) / 95.0F;
        x[5] = -lift * 0.12F + Mth.cos(idleSeed - 5.0F) / 95.0F;

        for (int i = 0; i < y.length; i++) {
            float delay = i * 0.65F;
            y[i] = sideLag * (0.12F + i * 0.025F)
                    + Mth.cos(idleSeed - delay) / 80.0F
                    + Mth.sin(walkPhase - delay) * bob * 0.022F;
        }
        z[3] = walkCounterWave * 0.004F;
        z[4] = walkCounterWave * 0.005F;
        z[5] = walkCounterWave * 0.006F;

        return new TailPose(cumulative(x), cumulative(y), cumulative(z));
    }

    private float[] cumulative(float[] values) {
        float[] cumulative = new float[values.length];
        float total = 0.0F;
        for (int i = 0; i < values.length; i++) {
            total += values[i];
            cumulative[i] = total;
        }
        return cumulative;
    }
}
