package net.creeperhost.minetogethercommunity.mixin.cosmetic;

import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

/**
 * Injects a UUID field into PlayerRenderState so cosmetic render layers can look up
 * per-player cosmetic profiles. MC 1.21.3+ removed direct entity access from render layers.
 */
@Mixin(PlayerRenderState.class)
public class PlayerRenderStateMixin {
    @Unique
    public UUID minetogether$playerUUID;
}
