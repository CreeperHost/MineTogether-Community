package net.creeperhost.minetogethercommunity.mixin.cosmetic;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.creeperhost.minetogethercommunity.cosmetic.PlayerCosmeticCache;
import net.creeperhost.minetogethercommunity.cosmetic.cape.CapeLayer;
import net.creeperhost.minetogethercommunity.cosmetic.hat.HatLayer;
import net.creeperhost.minetogethercommunity.cosmetic.renderstate.MineTogetherCosmeticRenderState;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailLayer;
import net.creeperhost.minetogethercommunity.cosmetic.wing.WingLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class PlayerRendererMixin {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(at = @At("TAIL"), method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V")
    private void addCosmeticLayers(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) this;
        LivingEntityRenderer renderer = (LivingEntityRenderer) (Object) this;
        renderer.addLayer(new HatLayer(parent));
        renderer.addLayer(new CapeLayer(parent, ctx.getModelSet()));
        renderer.addLayer(new TailLayer(parent));
        renderer.addLayer(new WingLayer(parent));
    }

    @Inject(at = @At("TAIL"), method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V")
    private void extractCosmeticState(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        CosmeticSelections selections;
        LocalPlayer minecraftPlayer = Minecraft.getInstance().player;
        boolean localPlayer = minecraftPlayer != null && entity.getUUID().equals(minecraftPlayer.getUUID());
        if (localPlayer) {
            selections = CosmeticSelections.instance();
        } else {
            selections = PlayerCosmeticCache.get(entity.getUUID());
        }

        MineTogetherCosmeticRenderState cosmeticState = (MineTogetherCosmeticRenderState) state;
        if (selections == null) {
            cosmeticState.minetogether$setCosmetics("", "", "", "", false, false);
            return;
        }

        cosmeticState.minetogether$setCosmetics(
                selections.selectedHatId,
                selections.selectedCapeId,
                selections.selectedTailId,
                selections.selectedWingId,
                localPlayer && selections.suppressVanillaCapeForPreview,
                localPlayer && selections.fullBrightPreview
        );
    }
}
