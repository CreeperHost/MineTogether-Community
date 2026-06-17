package net.creeperhost.minetogethercommunity.cosmetic.cape;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.util.Mth;

public class CapeLayer<T extends AbstractClientPlayer> extends RenderLayer<T, PlayerModel<T>> {

    public CapeLayer(RenderLayerParent<T, PlayerModel<T>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T player,
                       float limbSwing, float limbSwingAmount, float partialTicks,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        String capeId;
        if (player == Minecraft.getInstance().player) {
            // Local player — read from the singleton kept in sync with the GUI
            capeId = CosmeticSelections.instance().selectedCapeId;
        } else {
            // Remote player — look up the per-player cache (null = profile not fetched yet)
            CosmeticSelections cs = PlayerCosmeticCache.get(player.getUUID());
            if (cs == null) return;
            capeId = cs.selectedCapeId;
        }
        if (capeId == null || capeId.isEmpty()) return;
        if (player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) return;

        Cape cape = CapeRegistry.getLoaded(capeId);
        if (cape == null) return;

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, 0.125F);

        double d0 = Mth.lerp((double) partialTicks, player.xCloakO, player.xCloak)
                  - Mth.lerp((double) partialTicks, player.xo, player.getX());
        double d1 = Mth.lerp((double) partialTicks, player.yCloakO, player.yCloak)
                  - Mth.lerp((double) partialTicks, player.yo, player.getY());
        double d2 = Mth.lerp((double) partialTicks, player.zCloakO, player.zCloak)
                  - Mth.lerp((double) partialTicks, player.zo, player.getZ());

        float f = Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot);
        double d3 = (double) Mth.sin(f * (float) (Math.PI / 180.0));
        double d4 = (double) (-Mth.cos(f * (float) (Math.PI / 180.0)));

        float f1 = (float) d1 * 10.0F;
        f1 = Mth.clamp(f1, -6.0F, 32.0F);

        float f2 = (float) (d0 * d3 + d2 * d4) * 100.0F;
        f2 = Mth.clamp(f2, 0.0F, 150.0F);
        if (f2 < 0.0F) f2 = 0.0F;

        float f3 = (float) (d0 * d4 - d2 * d3) * 100.0F;
        f3 = Mth.clamp(f3, -20.0F, 20.0F);

        float f4 = Mth.lerp(partialTicks, player.oBob, player.bob);
        f1 += Mth.sin(Mth.lerp(partialTicks, player.walkDistO, player.walkDist) * 6.0F) * 32.0F * f4;

        if (player.isCrouching()) {
            f1 += 25.0F;
        }

        poseStack.mulPose(Axis.XP.rotationDegrees(6.0F + f2 / 2.0F + f1));
        poseStack.mulPose(Axis.ZP.rotationDegrees(f3 / 2.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - f3 / 2.0F));

        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(cape.texture()));
        getParentModel().renderCloak(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
    }
}
