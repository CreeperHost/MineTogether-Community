package net.creeperhost.minetogethercommunity.cosmetic.wing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticPreviewTime;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.creeperhost.minetogethercommunity.cosmetic.PlayerCosmeticCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;

public class WingLayer<T extends AbstractClientPlayer> extends RenderLayer<T, PlayerModel<T>> {

    public WingLayer(RenderLayerParent<T, PlayerModel<T>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T player,
                       float limbSwing, float limbSwingAmount, float partialTicks,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        String wingId;
        if (player == Minecraft.getInstance().player) {
            wingId = CosmeticSelections.instance().selectedWingId;
        } else {
            CosmeticSelections selections = PlayerCosmeticCache.get(player.getUUID());
            if (selections == null) return;
            wingId = selections.selectedWingId;
        }
        if (wingId == null || wingId.isEmpty()) return;

        Wing wing = WingRegistry.getLoaded(wingId);
        if (wing == null) return;

        boolean flying = player.getAbilities().flying || player.fallDistance > 0.0F;
        WingAnimation animation = wing.animation();
        float speed = flying ? animation.flyingSpeed() : animation.idleSpeed();
        float flapDegrees = flying ? animation.flyingFlapDegrees() : animation.idleFlapDegrees();
        float animationAge = CosmeticPreviewTime.ageInTicks(ageInTicks);
        float flap = Mth.sin(animationAge * speed) * flapDegrees;
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
