package net.creeperhost.minetogethercommunity.mixin.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.creeperhost.minetogethercommunity.cosmetic.PlayerCosmeticCache;
import net.creeperhost.minetogethercommunity.cosmetic.cape.CapeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.entity.layers.CapeLayer")
public abstract class VanillaCapeLayerMixin {

    @Inject(at = @At("HEAD"), method = "render", cancellable = true)
    private <T extends AbstractClientPlayer> void suppressForCustomCape(
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T player,
            float limbSwing, float limbSwingAmount, float partialTicks,
            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        String capeId;
        if (player == Minecraft.getInstance().player) {
            capeId = CosmeticSelections.instance().selectedCapeId;
        } else {
            // For remote players: only suppress if we've fetched their profile and they have an MT cape
            CosmeticSelections cs = PlayerCosmeticCache.get(player.getUUID());
            if (cs == null) return; // Profile not yet fetched — let vanilla cape show for now
            capeId = cs.selectedCapeId;
        }
        if (capeId != null && !capeId.isEmpty() && CapeRegistry.get(capeId) != null) {
            ci.cancel();
        }
    }
}
