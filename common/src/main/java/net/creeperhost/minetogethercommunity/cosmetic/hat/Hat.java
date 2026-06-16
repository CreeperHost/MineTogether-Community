package net.creeperhost.minetogethercommunity.cosmetic.hat;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record Hat(String id, String displayName, String author, String mod, boolean locked, String howToUnlock, ResourceLocation texture, int texWidth, int texHeight, List<HatCuboid> cuboids) {
}
