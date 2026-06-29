package net.creeperhost.minetogethercommunity.cosmetic.cape;

import com.mojang.blaze3d.vertex.PoseStack;
import net.creeperhost.minetogethercommunity.cosmetic.renderstate.MineTogetherCosmeticRenderState;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.Items;

public class CapeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private final PlayerCapeModel model;

    public CapeLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityModelSet modelSet) {
        super(renderer);
        this.model = new PlayerCapeModel(modelSet.bakeLayer(ModelLayers.PLAYER_CAPE));
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible || state.chestEquipment.is(Items.ELYTRA)) return;

        String capeId = ((MineTogetherCosmeticRenderState) state).minetogether$capeId();
        if (capeId.isEmpty()) return;

        Cape cape = CapeRegistry.getLoaded(capeId);
        if (cape == null) return;

        submitNodeCollector.submitModel(
                this.model,
                state,
                poseStack,
                RenderTypes.entityTranslucent(cape.texture()),
                lightCoords,
                OverlayTexture.NO_OVERLAY,
                state.outlineColor,
                null
        );
    }
}
