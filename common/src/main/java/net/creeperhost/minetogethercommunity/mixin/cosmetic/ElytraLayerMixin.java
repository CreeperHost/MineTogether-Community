package net.creeperhost.minetogethercommunity.mixin.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.creeperhost.minetogethercommunity.cosmetic.PlayerCosmeticCache;
import net.creeperhost.minetogethercommunity.cosmetic.cape.Cape;
import net.creeperhost.minetogethercommunity.cosmetic.cape.CapeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.entity.layers.ElytraLayer")
public abstract class ElytraLayerMixin<T extends LivingEntity, M extends EntityModel<T>> {

    @Shadow
    private ElytraModel<T> elytraModel;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void mtc$renderWithMTCape(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            T entity, float limbSwing, float limbSwingAmount, float partialTicks,
            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) return;

        ItemStack itemStack = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (!itemStack.is(Items.ELYTRA)) return;

        String capeId;
        if (player == Minecraft.getInstance().player) {
            capeId = CosmeticSelections.instance().selectedCapeId;
        } else {
            CosmeticSelections cs = PlayerCosmeticCache.get(player.getUUID());
            if (cs == null) return;
            capeId = cs.selectedCapeId;
        }
        if (capeId == null || capeId.isEmpty()) return;

        Cape cape = CapeRegistry.getLoaded(capeId);
        if (cape == null) return;

        //noinspection rawtypes
        EntityModel parentModel = ((RenderLayer) (Object) this).getParentModel();
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, 0.125F);
        //noinspection unchecked
        parentModel.copyPropertiesTo(this.elytraModel);
        this.elytraModel.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        VertexConsumer vertexConsumer = ItemRenderer.getArmorFoilBuffer(bufferSource,
                RenderType.armorCutoutNoCull(cape.texture()), itemStack.hasFoil());
        this.elytraModel.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        ci.cancel();
    }
}
