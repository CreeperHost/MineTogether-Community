package net.creeperhost.minetogethercommunity.cosmetic.cape;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticRenderHelper;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.Items;

/**
 * Custom cape render layer for MC 1.21.3+.
 * Since PlayerModel no longer has a 'cloak' ModelPart, we build our own cape mesh.
 */
public class CapeLayer extends RenderLayer<PlayerRenderState, PlayerModel> {

    private static final ModelPart CAPE_MODEL;
    static {
        // Build a simple cape quad: 10x16x1 box matching vanilla cape dimensions
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("cape",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, 0.0F, -1.0F, 10.0F, 16.0F, 1.0F),
                PartPose.ZERO);
        CAPE_MODEL = LayerDefinition.create(mesh, 64, 32).bakeRoot().getChild("cape");
    }

    public CapeLayer(RenderLayerParent<PlayerRenderState, ?> renderer) {
        //noinspection unchecked
        super((RenderLayerParent<PlayerRenderState, PlayerModel>) renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       PlayerRenderState state, float yRot, float xRot) {
        CosmeticSelections cs = CosmeticRenderHelper.getSelectionsForState(state);
        if (cs == null) return;
        String capeId = cs.selectedCapeId;
        if (capeId == null || capeId.isEmpty()) return;

        // Don't render cape if wearing elytra
        if (state.chestEquipment != null && state.chestEquipment.is(Items.ELYTRA)) return;

        Cape cape = CapeRegistry.getLoaded(capeId);
        if (cape == null) return;

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, 0.125F);

        // Use pre-computed cape physics from PlayerRenderState
        float capeFlap = state.capeFlap;
        float capeLean = state.capeLean;
        float capeLean2 = state.capeLean2;

        poseStack.mulPose(Axis.XP.rotationDegrees(6.0F + capeLean / 2.0F + capeFlap));
        poseStack.mulPose(Axis.ZP.rotationDegrees(capeLean2 / 2.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - capeLean2 / 2.0F));

        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(cape.texture()));
        CAPE_MODEL.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
    }
}
