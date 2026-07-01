package net.creeperhost.minetogethercommunity.mixin.cosmetic;

import net.creeperhost.minetogethercommunity.cosmetic.PlayerRenderStateAccess;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

/**
 * Injects a UUID field into PlayerRenderState so cosmetic render layers can look up
 * per-player cosmetic profiles. MC 1.21.3+ removed direct entity access from render layers.
 */
@Mixin(PlayerRenderState.class)
public class PlayerRenderStateMixin implements PlayerRenderStateAccess {
    @Unique
    private UUID minetogether$playerUUID;

    @Override
    public UUID minetogether$getPlayerUUID() {
        return minetogether$playerUUID;
    }

    @Override
    public void minetogether$setPlayerUUID(UUID uuid) {
        this.minetogether$playerUUID = uuid;
    }
}
