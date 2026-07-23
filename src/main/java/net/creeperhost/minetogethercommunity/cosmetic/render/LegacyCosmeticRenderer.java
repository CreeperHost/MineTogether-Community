package net.creeperhost.minetogethercommunity.cosmetic.render;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticPreviewTime;
import net.creeperhost.minetogethercommunity.cosmetic.PlayerCosmeticCache;
import net.creeperhost.minetogethercommunity.cosmetic.ModelPlacement;
import net.creeperhost.minetogethercommunity.cosmetic.cape.Cape;
import net.creeperhost.minetogethercommunity.cosmetic.cape.CapeRegistry;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import net.creeperhost.minetogethercommunity.cosmetic.hat.Hat;
import net.creeperhost.minetogethercommunity.cosmetic.hat.HatRegistry;
import net.creeperhost.minetogethercommunity.cosmetic.tail.Tail;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailElementPose;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailPose;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailRegistry;
import net.creeperhost.minetogethercommunity.util.CompatMath;
import net.creeperhost.minetogethercommunity.cosmetic.wing.Wing;
import net.creeperhost.minetogethercommunity.cosmetic.wing.WingAnimation;
import net.creeperhost.minetogethercommunity.cosmetic.wing.WingRegistry;
import net.creeperhost.minetogethercommunity.cosmetic.wing.WingPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderPlayerEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class LegacyCosmeticRenderer {

    private static final float SCALE = 0.0625F;

    @SubscribeEvent
    public void onRenderPlayerSpecials(RenderPlayerEvent.Specials.Pre event) {
        if (!(event.entityPlayer instanceof AbstractClientPlayer)) return;
        AbstractClientPlayer player = (AbstractClientPlayer) event.entityPlayer;
        RenderPlayer renderer = event.renderer;
        float ageInTicks = player.ticksExisted + event.partialRenderTick;

        boolean fullBrightPreview = CosmeticSelections.instance().fullBrightPreview;
        float previousLightX = OpenGlHelper.lastBrightnessX;
        float previousLightY = OpenGlHelper.lastBrightnessY;
        if (fullBrightPreview) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        }

        try {
            renderCape(event, player, renderer, event.partialRenderTick);
            EmotePlayer.ModelState modelState = EmotePlayer.applyToModel(renderer.modelBipedMain, player, ageInTicks);
            float translateY = EmotePlayer.renderTranslateY(player, ageInTicks);
            float pitch = EmotePlayer.renderPitch(player, ageInTicks);
            float yaw = EmotePlayer.renderYaw(player, ageInTicks);
            float roll = EmotePlayer.renderRoll(player, ageInTicks);
            GlStateManager.pushMatrix();
            try {
                if (translateY != 0.0F) {
                    GlStateManager.translate(0.0F, translateY, 0.0F);
                }
                if (yaw != 0.0F) {
                    GlStateManager.rotate(yaw * 180.0F / (float) Math.PI, 0.0F, 1.0F, 0.0F);
                }
                if (roll != 0.0F) {
                    GlStateManager.translate(0.0F, -1.25F, 0.0F);
                    GlStateManager.rotate(roll * 180.0F / (float) Math.PI, 0.0F, 0.0F, 1.0F);
                    GlStateManager.translate(0.0F, 1.25F, 0.0F);
                }
                if (pitch != 0.0F) {
                    GlStateManager.translate(0.0F, -1.25F, 0.0F);
                    GlStateManager.rotate(pitch * 180.0F / (float) Math.PI, 1.0F, 0.0F, 0.0F);
                    GlStateManager.translate(0.0F, 1.25F, 0.0F);
                }
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                renderHat(player, renderer, ageInTicks);
                renderTail(player, renderer, event.partialRenderTick, ageInTicks);
                renderWing(player, renderer, ageInTicks);
            } finally {
                GlStateManager.popMatrix();
                if (modelState != null) {
                    modelState.restore(renderer.modelBipedMain);
                }
            }
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            if (fullBrightPreview) {
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, previousLightX, previousLightY);
            }
        }
    }

    private void renderCape(RenderPlayerEvent.Specials.Pre event, AbstractClientPlayer player, RenderPlayer renderer, float partialTicks) {
        ResourceLocation texture = customCapeTexture(player);
        if (texture == null) {
            if (suppressesVanillaCape(player)) {
                event.renderCape = false;
            }
            return;
        }

        event.renderCape = false;
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        renderCapeModel(player, renderer, partialTicks);
    }

    private void renderHat(AbstractClientPlayer player, RenderPlayer renderer, float ageInTicks) {
        String id = selectionsFor(player).selectedHatId;
        if (id == null || id.isEmpty()) return;
        Hat hat = HatRegistry.getLoaded(id);
        if (hat == null) return;

        GlStateManager.pushMatrix();
        if (player.isSneaking()) {
            GlStateManager.translate(0.0F, 0.2F, 0.0F);
        }
        renderer.modelBipedMain.bipedHead.postRender(SCALE);
        Minecraft.getMinecraft().getTextureManager().bindTexture(hat.texture());
        if (hat.isJsonModel() && hat.jsonModel() != null) {
            float animationAge = CosmeticPreviewTime.ageInTicks(ageInTicks);
            ModelPlacement placement = hat.placement();
            GlStateManager.scale(placement.scale(), placement.scale(), placement.scale());
            GlStateManager.translate(placement.xPixels() / 16.0F, placement.yPixels() / 16.0F, placement.zPixels() / 16.0F);
            hat.jsonModel().render(hat.animation().pose(animationAge));
        } else {
            GlStateManager.scale(1.01F, 1.01F, 1.01F);
            GlStateManager.translate(0.0F, -1.5F, 0.0F);
            HatRegistry.getModel(hat).render(SCALE);
        }
        GlStateManager.popMatrix();
    }

    private void renderTail(AbstractClientPlayer player, RenderPlayer renderer, float partialTicks, float ageInTicks) {
        String id = selectionsFor(player).selectedTailId;
        if (id == null || id.isEmpty()) return;
        Tail tail = TailRegistry.getLoaded(id);
        if (tail == null) return;

        Minecraft.getMinecraft().getTextureManager().bindTexture(tail.texture());
        GlStateManager.pushMatrix();
        renderer.modelBipedMain.bipedBody.postRender(SCALE);
        ModelPlacement placement = tail.placement();
        GlStateManager.translate(placement.xPixels() / 16.0F, placement.yPixels() / 16.0F, placement.zPixels() / 16.0F);
        GlStateManager.scale(placement.scale(), placement.scale(), placement.scale());
        float animationAge = CosmeticPreviewTime.ageInTicks(ageInTicks);
        TailPose tailPose = tail.animation().enabled()
                ? tail.animation().pose(player, partialTicks, animationAge)
                : createTailPose(player, partialTicks, animationAge);
        TailElementPose elementPose = tail.animation().elementPose(animationAge);
        tail.model().render(tailPose, elementPose);
        GlStateManager.popMatrix();
    }

    private void renderWing(AbstractClientPlayer player, RenderPlayer renderer, float ageInTicks) {
        String id = selectionsFor(player).selectedWingId;
        if (id == null || id.isEmpty()) return;
        Wing wing = WingRegistry.getLoaded(id);
        if (wing == null) return;

        boolean flying = player.capabilities.isFlying || player.fallDistance > 0.0F;
        WingAnimation animation = wing.animation();
        float speed = flying ? animation.flyingSpeed() : animation.idleSpeed();
        float flapDegrees = flying ? animation.flyingFlapDegrees() : animation.idleFlapDegrees();
        float animationAge = CosmeticPreviewTime.ageInTicks(ageInTicks);
        float flap = CompatMath.sin(animationAge * speed) * flapDegrees;
        float spread = animation.baseSpreadDegrees() + flap * animation.flapScale();

        Minecraft.getMinecraft().getTextureManager().bindTexture(wing.texture());
        GlStateManager.pushMatrix();
        renderer.modelBipedMain.bipedBody.postRender(SCALE);
        WingPlacement placement = wing.placement();
        ModelPlacement transform = placement.transform();
        GlStateManager.translate(transform.xPixels() / 16.0F, transform.yPixels() / 16.0F, transform.zPixels() / 16.0F);
        GlStateManager.scale(transform.scale(), transform.scale(), transform.scale());
        renderWingSide(wing, placement, false, spread);
        renderWingSide(wing, placement, true, spread);
        GlStateManager.popMatrix();
    }

    private void renderWingSide(Wing wing, WingPlacement placement, boolean mirrored, float flapAngle) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((mirrored ? -placement.hingeXPixels() : placement.hingeXPixels()) / 16.0F, 0.0F, 0.0F);
        GlStateManager.rotate(mirrored ? flapAngle : -flapAngle, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mirrored ? -placement.restTiltDegrees() : placement.restTiltDegrees(), 0.0F, 0.0F, 1.0F);
        if (mirrored) {
            GlStateManager.scale(-1.0F, 1.0F, 1.0F);
        }
        wing.model().render();
        GlStateManager.popMatrix();
    }

    private void renderCapeModel(AbstractClientPlayer player, RenderPlayer renderer, float partialTicks) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, 0.0F, 0.125F);

        double cloakX = interpolate(player.field_71091_bM, player.field_71094_bP, partialTicks) - interpolate(player.prevPosX, player.posX, partialTicks);
        double cloakY = interpolate(player.field_71096_bN, player.field_71095_bQ, partialTicks) - interpolate(player.prevPosY, player.posY, partialTicks);
        double cloakZ = interpolate(player.field_71097_bO, player.field_71085_bR, partialTicks) - interpolate(player.prevPosZ, player.posZ, partialTicks);
        float bodyYaw = player.prevRenderYawOffset + (player.renderYawOffset - player.prevRenderYawOffset) * partialTicks;
        double sin = CompatMath.sin(bodyYaw * 0.017453292F);
        double cos = -CompatMath.cos(bodyYaw * 0.017453292F);
        float vertical = CompatMath.clamp((float) cloakY * 10.0F, -6.0F, 32.0F);
        float forward = (float) (cloakX * sin + cloakZ * cos) * 100.0F;
        forward = Math.max(forward, 0.0F);
        float side = (float) (cloakX * cos - cloakZ * sin) * 100.0F;
        float camera = player.prevCameraYaw + (player.cameraYaw - player.prevCameraYaw) * partialTicks;
        vertical += CompatMath.sin((player.prevDistanceWalkedModified + (player.distanceWalkedModified - player.prevDistanceWalkedModified) * partialTicks) * 6.0F) * 32.0F * camera;

        if (player.isSneaking()) {
            vertical += 25.0F;
        }

        GlStateManager.rotate(6.0F + forward / 2.0F + vertical, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(side / 2.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(-side / 2.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
        renderer.modelBipedMain.renderCloak(SCALE);
        GlStateManager.popMatrix();
    }

    private TailPose createTailPose(AbstractClientPlayer player, float partialTicks, float ageInTicks) {
        float idleSeed = ageInTicks * (float) (Math.PI * 2.0D) / 140.0F;
        float walk = player.prevDistanceWalkedModified + (player.distanceWalkedModified - player.prevDistanceWalkedModified) * partialTicks;
        float bob = player.prevCameraYaw + (player.cameraYaw - player.prevCameraYaw) * partialTicks;
        float walkPhase = walk * 6.0F;
        float walkWave = CompatMath.sin(walkPhase) * bob;
        float walkCounterWave = CompatMath.cos(walkPhase) * bob;

        float lift;
        float sideLag = 0.0F;
        if (player.isRiding()) {
            lift = (float) Math.toRadians(8.0F);
        } else {
            double cloakX = interpolate(player.field_71091_bM, player.field_71094_bP, partialTicks) - interpolate(player.prevPosX, player.posX, partialTicks);
            double cloakY = interpolate(player.field_71096_bN, player.field_71095_bQ, partialTicks) - interpolate(player.prevPosY, player.posY, partialTicks);
            double cloakZ = interpolate(player.field_71097_bO, player.field_71085_bR, partialTicks) - interpolate(player.prevPosZ, player.posZ, partialTicks);
            float bodyYaw = player.prevRenderYawOffset + (player.renderYawOffset - player.prevRenderYawOffset) * partialTicks;
            float sin = CompatMath.sin(bodyYaw * 0.017453292F);
            float back = -CompatMath.cos(bodyYaw * 0.017453292F);
            float verticalLag = CompatMath.clamp((float) cloakY * 10.0F, -6.0F, 20.0F);
            float backwardLag = CompatMath.clamp((float) (cloakX * sin + cloakZ * back) * 100.0F, 0.0F, 40.0F);
            float sidewaysLag = CompatMath.clamp((float) (cloakX * back - cloakZ * sin) * 100.0F, -16.0F, 16.0F);
            lift = CompatMath.clamp(backwardLag / 260.0F + verticalLag / 500.0F, -0.05F, 0.16F);
            sideLag = sidewaysLag / 220.0F;
        }

        float[] x = new float[6];
        float[] y = new float[6];
        float[] z = new float[6];
        x[0] = lift * 0.28F + walkWave * 0.012F;
        x[1] = lift * 0.24F + walkWave * 0.010F;
        x[2] = lift * 0.18F + CompatMath.cos(idleSeed - 2.0F) / 110.0F;
        x[3] = -lift * 0.08F + CompatMath.cos(idleSeed - 3.0F) / 95.0F;
        x[4] = -lift * 0.10F + CompatMath.cos(idleSeed - 4.0F) / 95.0F;
        x[5] = -lift * 0.12F + CompatMath.cos(idleSeed - 5.0F) / 95.0F;

        for (int i = 0; i < y.length; i++) {
            float delay = i * 0.65F;
            y[i] = sideLag * (0.12F + i * 0.025F)
                    + CompatMath.cos(idleSeed - delay) / 80.0F
                    + CompatMath.sin(walkPhase - delay) * bob * 0.022F;
        }
        z[3] = walkCounterWave * 0.004F;
        z[4] = walkCounterWave * 0.005F;
        z[5] = walkCounterWave * 0.006F;

        return new TailPose(cumulative(x), cumulative(y), cumulative(z));
    }

    private float[] cumulative(float[] values) {
        float[] cumulative = new float[values.length];
        float total = 0.0F;
        for (int i = 0; i < values.length; i++) {
            total += values[i];
            cumulative[i] = total;
        }
        return cumulative;
    }

    private static ResourceLocation customCapeTexture(AbstractClientPlayer player) {
        String id = customCapeId(player);
        if (id == null || id.isEmpty()) return null;
        Cape cape = CapeRegistry.getLoaded(id);
        return cape == null ? null : cape.texture();
    }

    private static String customCapeId(AbstractClientPlayer player) {
        if (player == Minecraft.getMinecraft().thePlayer) {
            return CosmeticSelections.instance().selectedCapeId;
        }
        CosmeticSelections selections = PlayerCosmeticCache.get(player.getUniqueID());
        return selections == null ? null : selections.selectedCapeId;
    }

    private static boolean suppressesVanillaCape(AbstractClientPlayer player) {
        return player == Minecraft.getMinecraft().thePlayer && CosmeticSelections.instance().suppressVanillaCapeForPreview;
    }

    private CosmeticSelections selectionsFor(AbstractClientPlayer player) {
        if (player == Minecraft.getMinecraft().thePlayer) {
            return CosmeticSelections.instance();
        }
        CosmeticSelections selections = PlayerCosmeticCache.get(player.getUniqueID());
        return selections == null ? new CosmeticSelections() : selections;
    }

    private static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }
}
