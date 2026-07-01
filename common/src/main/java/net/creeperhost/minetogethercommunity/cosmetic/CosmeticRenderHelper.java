package net.creeperhost.minetogethercommunity.cosmetic;

import net.creeperhost.minetogethercommunity.mixin.cosmetic.PlayerRenderStateMixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Utility for cosmetic render layers on MC 1.21.3+ where render layers receive
 * {@link PlayerRenderState} instead of the entity instance.
 */
public class CosmeticRenderHelper {

    /**
     * Extracts the UUID from a PlayerRenderState (injected via mixin).
     */
    public static @Nullable UUID getPlayerUUID(PlayerRenderState state) {
        return ((PlayerRenderStateMixin) (Object) state).minetogether$playerUUID;
    }

    /**
     * Returns the cosmetic selections for the player represented by the given render state.
     * Returns the local singleton for the local player, or the per-player cache entry for
     * remote players.
     */
    public static @Nullable CosmeticSelections getSelectionsForState(PlayerRenderState state) {
        UUID uuid = getPlayerUUID(state);
        if (uuid == null) return null;

        // Check if this is the local player
        if (Minecraft.getInstance().player != null && uuid.equals(Minecraft.getInstance().player.getUUID())) {
            return CosmeticSelections.instance();
        }
        return PlayerCosmeticCache.get(uuid);
    }
}
