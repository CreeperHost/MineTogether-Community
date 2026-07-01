package net.creeperhost.minetogethercommunity.cosmetic.tail;

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
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TailLayer extends RenderLayer<PlayerRenderState, PlayerModel> {

    private static final Logger LOGGER = LogManager.getLogger();

    public TailLayer(RenderLayerParent<PlayerRenderState, ?> renderer) {
        //noinspection unchecked
        super((RenderLayerParent<PlayerRenderState, PlayerModel>) renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       PlayerRenderState state, float yRot, float xRot) {
        CosmeticSelections cs = CosmeticRenderHelper.getSelectionsForState(state);
        if (cs == null) return;
        String tailId = cs.selectedTailId;
        if (tailId == null || tailId.isEmpty()) return;

        Tail tail = TailRegistry.getLoaded(tailId);
        if (tail == null) {
            LOGGER.debug("[TailLayer] tailId='{}' not yet loaded", tailId);
            return;
        }

        LOGGER.debug("[TailLayer] rendering tail='{}' elements={}", tailId, tail.elements().size());
        poseStack.pushPose();

        getParentModel().body.translateAndRotate(poseStack);
        poseStack.translate(-8.0F / 16.0F, 2.0F / 16.0F, 2.0F / 16.0F);

        TailPose tailPose = createTailPose(state);
        int renderLight = CosmeticSelections.instance().fullBrightPreview ? LightTexture.FULL_BRIGHT : packedLight;
        tail.model().render(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(tail.texture())), renderLight, tailPose);

        poseStack.popPose();
    }

    /**
     * Creates tail pose animation from PlayerRenderState.
     * Uses available state fields (walk animation, age, crouching, cape physics)
     * to approximate the original entity-based physics.
     */
    private TailPose createTailPose(PlayerRenderState state) {
        float ageInTicks = state.ageInTicks;
        float idleSeed = ageInTicks * (float) (Math.PI * 2.0D) / 140.0F;
        float walkPos = state.walkAnimationPos;
        float walkSpeed = state.walkAnimationSpeed;
        float walkPhase = walkPos * 6.0F;
        float walkWave = Mth.sin(walkPhase) * walkSpeed;
        float walkCounterWave = Mth.cos(walkPhase) * walkSpeed;

        // Use cape lean as proxy for movement lag (cape physics captures momentum)
        float capeLean = state.capeLean;
        float lift = 0.0F;
        float sideLag = 0.0F;

        if (state.isPassenger) {
            lift = (float) Math.toRadians(8.0F);
        } else {
            // Approximate the velocity-based lift from cape lean data
            lift = Mth.clamp(capeLean / 1500.0F, -0.05F, 0.16F);
            sideLag = state.capeLean2 / 220.0F;
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
                    + Mth.sin(walkPhase - delay) * walkSpeed * 0.022F;
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
