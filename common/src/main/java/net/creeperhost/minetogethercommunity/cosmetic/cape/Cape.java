package net.creeperhost.minetogethercommunity.cosmetic.cape;

import net.minecraft.resources.ResourceLocation;

public record Cape(String id, String displayName, String author, String mod, boolean locked, String howToUnlock, ResourceLocation texture, int texWidth, int texHeight) {
}
