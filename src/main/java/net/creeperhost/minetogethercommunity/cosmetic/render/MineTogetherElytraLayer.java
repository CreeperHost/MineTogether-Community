package net.creeperhost.minetogethercommunity.cosmetic.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelElytra;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public class MineTogetherElytraLayer implements LayerRenderer<EntityLivingBase> {

    private static final ResourceLocation TEXTURE_ELYTRA = new ResourceLocation("textures/entity/elytra.png");

    private final RenderLivingBase<?> renderer;
    private final ModelElytra modelElytra = new ModelElytra();

    public MineTogetherElytraLayer(RenderLivingBase<?> renderer) {
        this.renderer = renderer;
    }

    @Override
    public void doRenderLayer(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
                              float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        ItemStack chest = entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        if (chest.getItem() != Items.ELYTRA) return;

        Minecraft.getMinecraft().getTextureManager().bindTexture(textureFor(entity));
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, 0.0F, 0.125F);
        modelElytra.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entity);
        modelElytra.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        if (chest.isItemEnchanted()) {
            LayerArmorBase.renderEnchantedGlint(renderer, entity, modelElytra, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale);
        }
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }

    private ResourceLocation textureFor(EntityLivingBase entity) {
        if (!(entity instanceof AbstractClientPlayer)) return TEXTURE_ELYTRA;

        AbstractClientPlayer player = (AbstractClientPlayer) entity;
        ResourceLocation customCape = MineTogetherCapeLayer.customCapeTexture(player);
        if (customCape != null) return customCape;
        if (player.isPlayerInfoSet() && player.getLocationElytra() != null) return player.getLocationElytra();
        if (player.hasPlayerInfo() && player.getLocationCape() != null && player.isWearing(EnumPlayerModelParts.CAPE)) {
            return player.getLocationCape();
        }
        return TEXTURE_ELYTRA;
    }
}
