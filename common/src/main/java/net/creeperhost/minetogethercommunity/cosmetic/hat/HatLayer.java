package net.creeperhost.minetogethercommunity.cosmetic.hat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.creeperhost.minetogethercommunity.cosmetic.PlayerCosmeticCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

public class HatLayer<T extends AbstractClientPlayer> extends RenderLayer<T, PlayerModel<T>> {

    public HatLayer(RenderLayerParent<T, PlayerModel<T>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T player,
                       float limbSwing, float limbSwingAmount, float partialTicks,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        String hatId;
        if (player == Minecraft.getInstance().player) {
            // Local player - read from the singleton kept in sync with the GUI
            hatId = CosmeticSelections.instance().selectedHatId;
        } else {
            // Remote player - look up the per-player cache (null = profile not fetched yet)
            CosmeticSelections cs = PlayerCosmeticCache.get(player.getUUID());
            if (cs == null) return;
            hatId = cs.selectedHatId;
        }
        if (hatId == null || hatId.isEmpty()) return;

        Hat hat = HatRegistry.getLoaded(hatId);
        if (hat == null) return;
        int renderLight = CosmeticSelections.instance().fullBrightPreview ? LightTexture.FULL_BRIGHT : packedLight;

        poseStack.pushPose();
        getParentModel().head.translateAndRotate(poseStack);
        if (hat.isJsonModel() && hat.jsonModel() != null) {
            poseStack.scale(1.01F, 1.01F, 1.01F);
            poseStack.translate(-8.0F / 16.0F, -16.0F / 16.0F, -8.0F / 16.0F);
            hat.jsonModel().render(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(hat.texture())), renderLight, hat.animation().pose(ageInTicks));
        } else {
            poseStack.scale(1.01f, 1.01f, 1.01f);
            poseStack.translate(0.0D, -1.5D, 0.0D);
            HatModel model = HatRegistry.getModel(hat);
            model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(hat.texture())), renderLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        }
        poseStack.popPose();
    }
}
