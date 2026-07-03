package net.creeperhost.minetogethercommunity.cosmetic.render;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticPreviewTime;
import net.creeperhost.minetogethercommunity.cosmetic.PlayerCosmeticCache;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import net.creeperhost.minetogethercommunity.cosmetic.hat.Hat;
import net.creeperhost.minetogethercommunity.cosmetic.hat.HatRegistry;
import net.creeperhost.minetogethercommunity.cosmetic.tail.Tail;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailElementPose;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailPose;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailRegistry;
import net.creeperhost.minetogethercommunity.cosmetic.wing.Wing;
import net.creeperhost.minetogethercommunity.cosmetic.wing.WingAnimation;
import net.creeperhost.minetogethercommunity.cosmetic.wing.WingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.util.math.MathHelper;

public class CosmeticLayer<T extends AbstractClientPlayer> implements LayerRenderer<T> {

    private final RenderPlayer renderer;

    public CosmeticLayer(RenderPlayer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void doRenderLayer(T player, float limbSwing, float limbSwingAmount, float partialTicks,
                              float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        boolean fullBrightPreview = CosmeticSelections.instance().fullBrightPreview;
        float previousLightX = OpenGlHelper.lastBrightnessX;
        float previousLightY = OpenGlHelper.lastBrightnessY;
        if (fullBrightPreview) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        }
        EmotePlayer.ModelState modelState = EmotePlayer.applyToModel(renderer.getMainModel(), player, ageInTicks);
        float translateY = EmotePlayer.renderTranslateY(player, ageInTicks);
        float pitch = EmotePlayer.renderPitch(player, ageInTicks);
        GlStateManager.pushMatrix();
        try {
            if (translateY != 0.0F) {
                GlStateManager.translate(0.0F, translateY, 0.0F);
            }
            if (pitch != 0.0F) {
                GlStateManager.translate(0.0F, -1.25F, 0.0F);
                GlStateManager.rotate(pitch * 180.0F / (float) Math.PI, 1.0F, 0.0F, 0.0F);
                GlStateManager.translate(0.0F, 1.25F, 0.0F);
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            renderHat(player, ageInTicks, scale);
            renderTail(player, partialTicks, ageInTicks, scale);
            renderWing(player, ageInTicks, scale);
        } finally {
            GlStateManager.popMatrix();
            if (modelState != null) {
                modelState.restore(renderer.getMainModel());
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            if (fullBrightPreview) {
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, previousLightX, previousLightY);
            }
        }
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }

    private void renderHat(T player, float ageInTicks, float scale) {
        String id = selectionsFor(player).selectedHatId;
        if (id == null || id.isEmpty()) return;
        Hat hat = HatRegistry.getLoaded(id);
        if (hat == null) return;

        GlStateManager.pushMatrix();
        if (player.isSneaking()) {
            GlStateManager.translate(0.0F, 0.2F, 0.0F);
        }
        renderer.getMainModel().bipedHead.postRender(scale);
        Minecraft.getMinecraft().getTextureManager().bindTexture(hat.texture());
        if (hat.isJsonModel() && hat.jsonModel() != null) {
            float animationAge = CosmeticPreviewTime.ageInTicks(ageInTicks);
            GlStateManager.scale(1.01F, 1.01F, 1.01F);
            GlStateManager.translate(-8.0F / 16.0F, -16.0F / 16.0F, -8.0F / 16.0F);
            hat.jsonModel().render(hat.animation().pose(animationAge));
        } else {
            GlStateManager.scale(1.01F, 1.01F, 1.01F);
            GlStateManager.translate(0.0F, -1.5F, 0.0F);
            HatRegistry.getModel(hat).render(scale);
        }
        GlStateManager.popMatrix();
    }

    private void renderTail(T player, float partialTicks, float ageInTicks, float scale) {
        String id = selectionsFor(player).selectedTailId;
        if (id == null || id.isEmpty()) return;
        Tail tail = TailRegistry.getLoaded(id);
        if (tail == null) return;

        Minecraft.getMinecraft().getTextureManager().bindTexture(tail.texture());
        GlStateManager.pushMatrix();
        renderer.getMainModel().bipedBody.postRender(scale);
        GlStateManager.translate(-8.0F / 16.0F, 2.0F / 16.0F, 2.0F / 16.0F);
        float animationAge = CosmeticPreviewTime.ageInTicks(ageInTicks);
        TailPose tailPose = tail.animation().enabled()
                ? tail.animation().pose(player, partialTicks, animationAge)
                : createTailPose(player, partialTicks, animationAge);
        TailElementPose elementPose = tail.animation().elementPose(animationAge);
        tail.model().render(tailPose, elementPose);
        GlStateManager.popMatrix();
    }

    private void renderWing(T player, float ageInTicks, float scale) {
        String id = selectionsFor(player).selectedWingId;
        if (id == null || id.isEmpty()) return;
        Wing wing = WingRegistry.getLoaded(id);
        if (wing == null) return;

        boolean flying = player.capabilities.isFlying || player.fallDistance > 0.0F;
        WingAnimation animation = wing.animation();
        float speed = flying ? animation.flyingSpeed() : animation.idleSpeed();
        float flapDegrees = flying ? animation.flyingFlapDegrees() : animation.idleFlapDegrees();
        float animationAge = CosmeticPreviewTime.ageInTicks(ageInTicks);
        float flap = MathHelper.sin(animationAge * speed) * flapDegrees;
        float spread = animation.baseSpreadDegrees() + flap * animation.flapScale();

        Minecraft.getMinecraft().getTextureManager().bindTexture(wing.texture());
        GlStateManager.pushMatrix();
        renderer.getMainModel().bipedBody.postRender(scale);
        GlStateManager.translate(0.0F, -7.0F / 16.0F, 4.2F / 16.0F);
        GlStateManager.scale(0.58F, 0.58F, 0.58F);
        renderWingSide(wing, false, spread);
        renderWingSide(wing, true, spread);
        GlStateManager.popMatrix();
    }

    private void renderWingSide(Wing wing, boolean mirrored, float flapAngle) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(mirrored ? -2.4F / 16.0F : 2.4F / 16.0F, 0.0F, 0.0F);
        GlStateManager.rotate(mirrored ? flapAngle : -flapAngle, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mirrored ? -27.0F : 27.0F, 0.0F, 0.0F, 1.0F);
        if (mirrored) {
            GlStateManager.scale(-1.0F, 1.0F, 1.0F);
        }
        wing.model().render();
        GlStateManager.popMatrix();
    }

    private TailPose createTailPose(T player, float partialTicks, float ageInTicks) {
        float idleSeed = ageInTicks * (float) (Math.PI * 2.0D) / 140.0F;
        float walk = player.prevDistanceWalkedModified + (player.distanceWalkedModified - player.prevDistanceWalkedModified) * partialTicks;
        float bob = player.prevCameraYaw + (player.cameraYaw - player.prevCameraYaw) * partialTicks;
        float walkPhase = walk * 6.0F;
        float walkWave = MathHelper.sin(walkPhase) * bob;
        float walkCounterWave = MathHelper.cos(walkPhase) * bob;

        float lift = 0.0F;
        float sideLag = 0.0F;
        if (player.isRiding()) {
            lift = (float) Math.toRadians(8.0F);
        } else {
            double cloakX = interpolate(player.prevChasingPosX, player.chasingPosX, partialTicks) - interpolate(player.prevPosX, player.posX, partialTicks);
            double cloakY = interpolate(player.prevChasingPosY, player.chasingPosY, partialTicks) - interpolate(player.prevPosY, player.posY, partialTicks);
            double cloakZ = interpolate(player.prevChasingPosZ, player.chasingPosZ, partialTicks) - interpolate(player.prevPosZ, player.posZ, partialTicks);
            float bodyYaw = player.prevRenderYawOffset + (player.renderYawOffset - player.prevRenderYawOffset) * partialTicks;
            float sin = MathHelper.sin(bodyYaw * 0.017453292F);
            float back = -MathHelper.cos(bodyYaw * 0.017453292F);
            float verticalLag = MathHelper.clamp((float) cloakY * 10.0F, -6.0F, 20.0F);
            float backwardLag = MathHelper.clamp((float) (cloakX * sin + cloakZ * back) * 100.0F, 0.0F, 40.0F);
            float sidewaysLag = MathHelper.clamp((float) (cloakX * back - cloakZ * sin) * 100.0F, -16.0F, 16.0F);
            lift = MathHelper.clamp(backwardLag / 260.0F + verticalLag / 500.0F, -0.05F, 0.16F);
            sideLag = sidewaysLag / 220.0F;
        }

        float[] x = new float[6];
        float[] y = new float[6];
        float[] z = new float[6];
        x[0] = lift * 0.28F + walkWave * 0.012F;
        x[1] = lift * 0.24F + walkWave * 0.010F;
        x[2] = lift * 0.18F + MathHelper.cos(idleSeed - 2.0F) / 110.0F;
        x[3] = -lift * 0.08F + MathHelper.cos(idleSeed - 3.0F) / 95.0F;
        x[4] = -lift * 0.10F + MathHelper.cos(idleSeed - 4.0F) / 95.0F;
        x[5] = -lift * 0.12F + MathHelper.cos(idleSeed - 5.0F) / 95.0F;

        for (int i = 0; i < y.length; i++) {
            float delay = i * 0.65F;
            y[i] = sideLag * (0.12F + i * 0.025F)
                    + MathHelper.cos(idleSeed - delay) / 80.0F
                    + MathHelper.sin(walkPhase - delay) * bob * 0.022F;
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

    private CosmeticSelections selectionsFor(T player) {
        if (player == Minecraft.getMinecraft().player) {
            return CosmeticSelections.instance();
        }
        CosmeticSelections selections = PlayerCosmeticCache.get(player.getUniqueID());
        return selections == null ? new CosmeticSelections() : selections;
    }

    private static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }
}
