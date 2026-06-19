package net.creeperhost.minetogethercommunity.cosmetic.tail;

import com.mojang.blaze3d.vertex.PoseStack;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.creeperhost.minetogethercommunity.cosmetic.PlayerCosmeticCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TailLayer<T extends AbstractClientPlayer> extends RenderLayer<T, PlayerModel<T>> {

    private static final Logger LOGGER = LogManager.getLogger();

    public TailLayer(RenderLayerParent<T, PlayerModel<T>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T player,
                       float limbSwing, float limbSwingAmount, float partialTicks,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        String tailId;
        if (player == Minecraft.getInstance().player) {
            tailId = CosmeticSelections.instance().selectedTailId;
        } else {
            CosmeticSelections cs = PlayerCosmeticCache.get(player.getUUID());
            if (cs == null) return;
            tailId = cs.selectedTailId;
        }
        if (tailId == null || tailId.isEmpty()) return;

        Tail tail = TailRegistry.getLoaded(tailId);
        if (tail == null) {
            LOGGER.debug("[TailLayer] tailId='{}' not yet loaded", tailId);
            return;
        }

        LOGGER.debug("[TailLayer] rendering tail='{}' elements={}", tailId, tail.elements().size());
        poseStack.pushPose();

        // Position in body-local space: centered on x, at hip level on y,
        // starting at the back face on z.  All offsets in model-pixel units.
        // Block-model origin (8, 8, 0) maps to body-local (0, 12, 2).
        getParentModel().body.translateAndRotate(poseStack);
        // All values in model-pixel units (same scale as entity model coords).
        // x: centre tail at block-model x=8 → -8; y: map block-model y=8 to body y=12 → 12-8=4;
        // z: start tail at body back face z=2 → +2.
        poseStack.translate(-8.0F / 16.0F, 2.0F / 16.0F, 2.0F / 16.0F);

        TailPose tailPose = createTailPose(player, partialTicks, ageInTicks);
        tail.model().render(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(tail.texture())), packedLight, tailPose);

        poseStack.popPose();
    }

    private TailPose createTailPose(T player, float partialTicks, float ageInTicks) {
        float idleSeed = ageInTicks * (float) (Math.PI * 2.0D) / 140.0F;
        float walk = Mth.lerp(partialTicks, player.walkDistO, player.walkDist);
        float bob = Mth.lerp(partialTicks, player.oBob, player.bob);
        float walkPhase = walk * 6.0F;
        float walkWave = Mth.sin(walkPhase) * bob;
        float walkCounterWave = Mth.cos(walkPhase) * bob;

        float lift = 0.0F;
        float sideLag = 0.0F;
        if (player.isPassenger()) {
            lift = (float) Math.toRadians(8.0F);
        } else {
            double d0 = Mth.lerp((double) partialTicks, player.xCloakO, player.xCloak)
                    - Mth.lerp((double) partialTicks, player.xo, player.getX());
            double d1 = Mth.lerp((double) partialTicks, player.yCloakO, player.yCloak)
                    - Mth.lerp((double) partialTicks, player.yo, player.getY());
            double d2 = Mth.lerp((double) partialTicks, player.zCloakO, player.zCloak)
                    - Mth.lerp((double) partialTicks, player.zo, player.getZ());
            float bodyYaw = Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot);
            float sin = Mth.sin(bodyYaw * (float) (Math.PI / 180.0D));
            float back = -Mth.cos(bodyYaw * (float) (Math.PI / 180.0D));
            float verticalLag = Mth.clamp((float) d1 * 10.0F, -6.0F, 20.0F);
            float backwardLag = Mth.clamp((float) (d0 * sin + d2 * back) * 100.0F, 0.0F, 40.0F);
            float sidewaysLag = Mth.clamp((float) (d0 * back - d2 * sin) * 100.0F, -16.0F, 16.0F);
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
