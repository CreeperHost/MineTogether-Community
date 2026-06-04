package net.creeperhost.minetogethercommunity.cosmetic.hat;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record Hat(String id, String displayName, ResourceLocation texture, int texWidth, int texHeight, List<HatCuboid> cuboids) {
}
