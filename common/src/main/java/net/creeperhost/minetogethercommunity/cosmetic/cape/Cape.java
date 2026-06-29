package net.creeperhost.minetogethercommunity.cosmetic.cape;

import net.minecraft.resources.Identifier;

public record Cape(String id, String displayName, String author, String mod, boolean locked, String howToUnlock, Identifier texture, int texWidth, int texHeight) {
}
