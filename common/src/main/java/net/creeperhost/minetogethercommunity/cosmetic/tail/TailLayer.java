package net.creeperhost.minetogethercommunity.cosmetic.tail;

import com.mojang.blaze3d.vertex.PoseStack;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticPreviewTime;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.creeperhost.minetogethercommunity.cosmetic.ModelPlacement;
import net.creeperhost.minetogethercommunity.cosmetic.PlayerCosmeticCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public class TailLayer<T extends AbstractClientPlayer> extends RenderLayer<T, PlayerModel<T>> {

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
        if (tail == null) return;

        poseStack.pushPose();

        // Position in body-local space: centered on x, at hip level on y,
        // starting at the back face on z.  All offsets in model-pixel units.
        // Block-model origin (8, 8, 0) maps to body-local (0, 12, 2).
        getParentModel().body.translateAndRotate(poseStack);
        // All values in model-pixel units (same scale as entity model coords).
        // x: centre tail at block-model x=8 → -8; y: map block-model y=8 to body y=12 → 12-8=4;
        // z: start tail at body back face z=2 → +2.
        ModelPlacement placement = tail.placement();
        poseStack.translate(placement.xPixels() / 16.0F, placement.yPixels() / 16.0F, placement.zPixels() / 16.0F);
        poseStack.scale(placement.scale(), placement.scale(), placement.scale());

        float animationAge = CosmeticPreviewTime.ageInTicks(ageInTicks);
        TailPose tailPose = tail.animation().pose(player, partialTicks, animationAge);
        TailElementPose elementPose = tail.animation().elementPose(animationAge);
        int renderLight = CosmeticSelections.instance().fullBrightPreview ? LightTexture.FULL_BRIGHT : packedLight;
        tail.model().render(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(tail.texture())), renderLight, tailPose, elementPose);

        poseStack.popPose();
    }
}
