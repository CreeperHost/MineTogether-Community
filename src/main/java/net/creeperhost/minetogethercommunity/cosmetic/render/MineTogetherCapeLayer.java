package net.creeperhost.minetogethercommunity.cosmetic.render;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.creeperhost.minetogethercommunity.cosmetic.PlayerCosmeticCache;
import net.creeperhost.minetogethercommunity.cosmetic.cape.Cape;
import net.creeperhost.minetogethercommunity.cosmetic.cape.CapeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

public class MineTogetherCapeLayer implements LayerRenderer<AbstractClientPlayer> {

    private final RenderPlayer renderer;

    public MineTogetherCapeLayer(RenderPlayer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void doRenderLayer(AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTicks,
                              float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        if (player.isInvisible()) return;
        if (player.getItemStackFromSlot(EntityEquipmentSlot.CHEST).getItem() == Items.ELYTRA) return;

        ResourceLocation texture = customCapeTexture(player);
        if (texture == null) {
            if (suppressesVanillaCape(player)) return;
            if (!hasVisibleVanillaCape(player)) return;
            texture = player.getLocationCape();
        }

        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        renderCapeModel(player, partialTicks, scale);
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }

    static ResourceLocation customCapeTexture(AbstractClientPlayer player) {
        String id = customCapeId(player);
        if (id == null || id.isEmpty()) return null;
        Cape cape = CapeRegistry.getLoaded(id);
        return cape == null ? null : cape.texture();
    }

    private static String customCapeId(AbstractClientPlayer player) {
        if (player == Minecraft.getMinecraft().player) {
            return CosmeticSelections.instance().selectedCapeId;
        }
        CosmeticSelections selections = PlayerCosmeticCache.get(player.getUniqueID());
        return selections == null ? null : selections.selectedCapeId;
    }

    private static boolean suppressesVanillaCape(AbstractClientPlayer player) {
        return player == Minecraft.getMinecraft().player && CosmeticSelections.instance().suppressVanillaCapeForPreview;
    }

    private static boolean hasVisibleVanillaCape(AbstractClientPlayer player) {
        return player.hasPlayerInfo()
                && player.isWearing(EnumPlayerModelParts.CAPE)
                && player.getLocationCape() != null;
    }

    private void renderCapeModel(AbstractClientPlayer player, float partialTicks, float scale) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, 0.0F, 0.125F);

        double cloakX = interpolate(player.prevChasingPosX, player.chasingPosX, partialTicks) - interpolate(player.prevPosX, player.posX, partialTicks);
        double cloakY = interpolate(player.prevChasingPosY, player.chasingPosY, partialTicks) - interpolate(player.prevPosY, player.posY, partialTicks);
        double cloakZ = interpolate(player.prevChasingPosZ, player.chasingPosZ, partialTicks) - interpolate(player.prevPosZ, player.posZ, partialTicks);
        float bodyYaw = player.prevRenderYawOffset + (player.renderYawOffset - player.prevRenderYawOffset) * partialTicks;
        double sin = MathHelper.sin(bodyYaw * 0.017453292F);
        double cos = -MathHelper.cos(bodyYaw * 0.017453292F);
        float vertical = MathHelper.clamp((float) cloakY * 10.0F, -6.0F, 32.0F);
        float forward = (float) (cloakX * sin + cloakZ * cos) * 100.0F;
        forward = Math.max(forward, 0.0F);
        float side = (float) (cloakX * cos - cloakZ * sin) * 100.0F;
        float camera = player.prevCameraYaw + (player.cameraYaw - player.prevCameraYaw) * partialTicks;
        vertical += MathHelper.sin((player.prevDistanceWalkedModified + (player.distanceWalkedModified - player.prevDistanceWalkedModified) * partialTicks) * 6.0F) * 32.0F * camera;

        if (player.isSneaking()) {
            vertical += 25.0F;
        }

        GlStateManager.rotate(6.0F + forward / 2.0F + vertical, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(side / 2.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(-side / 2.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
        renderer.getMainModel().renderCape(scale);
        GlStateManager.popMatrix();
    }

    private static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }
}
