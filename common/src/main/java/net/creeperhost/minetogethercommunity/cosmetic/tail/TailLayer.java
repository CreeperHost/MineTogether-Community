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
        poseStack.translate(-8.0F / 16.0F, 4.0F / 16.0F, 2.0F / 16.0F);

        TailPose tailPose = createTailPose(player, partialTicks, ageInTicks);
        tail.model().render(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(tail.texture())), packedLight, tailPose);

        poseStack.popPose();
    }

    private TailPose createTailPose(T player, float partialTicks, float ageInTicks) {
        float seed = ageInTicks * (float) (Math.PI * 2.0D) / 120.0F;
        float xSeed = ageInTicks * (float) (Math.PI * 2.0D) / 240.0F;

        float xAngleOffset = 0.0F;
        float yAngleMultiplier = 1.0F;
        if (player.isPassenger()) {
            xAngleOffset = (float) Math.toRadians(13.0F);
            yAngleMultiplier = 0.25F;
        } else {
            float bodyYaw = Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot);
            double motionX = Mth.lerp(partialTicks, player.xo, player.getX()) - player.xo;
            double motionZ = Mth.lerp(partialTicks, player.zo, player.getZ()) - player.zo;
            float sin = Mth.sin(bodyYaw * (float) (Math.PI / 180.0D));
            float cos = Mth.cos(bodyYaw * (float) (Math.PI / 180.0D));
            float forwardMotion = (float) (motionX * sin - motionZ * cos);
            xAngleOffset = Mth.clamp(-forwardMotion * 1.75F, -0.12F, 0.18F);
            yAngleMultiplier = Mth.clamp(1.0F - Math.abs(xAngleOffset) * 4.0F, 0.35F, 1.0F);
        }

        float[] x = new float[6];
        float[] y = new float[6];
        float[] z = new float[6];
        x[0] = xAngleOffset * 0.35F;
        x[1] = xAngleOffset * 0.35F;
        x[2] = xAngleOffset * 0.30F;
        x[3] = -xAngleOffset * 0.25F + Mth.cos(xSeed - 4.0F) / 16.0F;
        x[4] = -xAngleOffset * 0.30F + Mth.cos(xSeed - 5.0F) / 18.0F;
        x[5] = -xAngleOffset * 0.35F + Mth.cos(xSeed - 6.0F) / 18.0F;

        for (int i = 0; i < y.length; i++) {
            y[i] = Mth.cos(seed - (i + 1.0F)) / 14.0F * yAngleMultiplier;
        }
        z[2] = Mth.cos(xSeed - 3.0F) / 28.0F;
        z[3] = Mth.cos(xSeed - 4.0F) / 20.0F;
        z[4] = Mth.cos(xSeed - 5.0F) / 20.0F;
        z[5] = Mth.cos(xSeed - 6.0F) / 20.0F;

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
