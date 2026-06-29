package net.creeperhost.minetogethercommunity.mixin.cosmetic;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.client.renderer.entity.layers.WingsLayer")
public abstract class ElytraLayerMixin {
    // TODO: Re-port MineTogether cape textures onto 26.1 WingsLayer render states.
}
