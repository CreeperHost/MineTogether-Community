package net.creeperhost.minetogethercommunity.modulargui;

import net.minecraft.util.ResourceLocation;

public class Material {

    private final ResourceLocation texture;

    public Material(ResourceLocation texture) {
        this.texture = texture;
    }

    public ResourceLocation texture() {
        return texture;
    }
}
