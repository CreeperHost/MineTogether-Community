package net.creeperhost.minetogethercommunity.mixin.cosmetic;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;

/**
 * In MC 1.21.3+ PlayerModel no longer has a 'cloak' ModelPart.
 * This interface is kept as a no-op stub for compilation compatibility.
 * Cape rendering in 1.21.3+ is handled by a dedicated cape model in CapeLayer.
 */
@Mixin(targets = "net.minecraft.client.model.PlayerModel")
public interface PlayerModelAccess {
    // No-op: 'cloak' field was removed from PlayerModel in MC 1.21.3
}
