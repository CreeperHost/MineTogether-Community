package net.creeperhost.minetogethercommunity.cosmetic.hat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.creeperhost.minetogethercommunity.cosmetic.PlayerCosmeticCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

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
            // Local player — read from the singleton kept in sync with the GUI
            hatId = CosmeticSelections.instance().selectedHatId;
        } else {
            // Remote player — look up the per-player cache (null = profile not fetched yet)
            CosmeticSelections cs = PlayerCosmeticCache.get(player.getUUID());
            if (cs == null) return;
            hatId = cs.selectedHatId;
        }
        if (hatId == null || hatId.isEmpty()) return;

        Hat hat = HatRegistry.get(hatId);
        if (hat == null) return;

        poseStack.pushPose();
        getParentModel().head.translateAndRotate(poseStack);
        poseStack.scale(1.01f, 1.01f, 1.01f);
        poseStack.translate(0.0D, -1.5D, 0.0D);
        HatModel model = HatRegistry.getModel(hat);
        model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(hat.texture())), packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
