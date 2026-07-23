package net.creeperhost.minetogethercommunity.cosmetic.hat;

import net.minecraft.util.ResourceLocation;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailElement;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailModel;
import net.creeperhost.minetogethercommunity.cosmetic.ModelPlacement;

import java.util.Collections;
import java.util.List;

public class Hat {

    private final String id;
    private final String displayName;
    private final String author;
    private final String mod;
    private final boolean locked;
    private final String howToUnlock;
    private final ResourceLocation texture;
    private final int texWidth;
    private final int texHeight;
    private final HatModelType type;
    private final List<HatCuboid> cuboids;
    private final List<TailElement> jsonElements;
    private final TailModel jsonModel;
    private final HatAnimation animation;
    private final ModelPlacement placement;

    public Hat(String id, String displayName, String author, String mod, boolean locked, String howToUnlock,
               ResourceLocation texture, int texWidth, int texHeight, HatModelType type, List<HatCuboid> cuboids) {
        this(id, displayName, author, mod, locked, howToUnlock, texture, texWidth, texHeight, type, cuboids,
                Collections.<TailElement>emptyList(), null, HatAnimation.NONE);
    }

    public Hat(String id, String displayName, String author, String mod, boolean locked, String howToUnlock,
               ResourceLocation texture, int texWidth, int texHeight, HatModelType type, List<HatCuboid> cuboids,
               List<TailElement> jsonElements, TailModel jsonModel) {
        this(id, displayName, author, mod, locked, howToUnlock, texture, texWidth, texHeight, type, cuboids,
                jsonElements, jsonModel, HatAnimation.NONE);
    }

    public Hat(String id, String displayName, String author, String mod, boolean locked, String howToUnlock,
               ResourceLocation texture, int texWidth, int texHeight, HatModelType type, List<HatCuboid> cuboids,
               List<TailElement> jsonElements, TailModel jsonModel, HatAnimation animation) {
        this(id, displayName, author, mod, locked, howToUnlock, texture, texWidth, texHeight, type, cuboids,
                jsonElements, jsonModel, animation, new ModelPlacement(-8.0F, -16.0F, -8.0F, 1.01F));
    }

    public Hat(String id, String displayName, String author, String mod, boolean locked, String howToUnlock,
               ResourceLocation texture, int texWidth, int texHeight, HatModelType type, List<HatCuboid> cuboids,
               List<TailElement> jsonElements, TailModel jsonModel, HatAnimation animation, ModelPlacement placement) {
        this.id = id;
        this.displayName = displayName;
        this.author = author;
        this.mod = mod;
        this.locked = locked;
        this.howToUnlock = howToUnlock;
        this.texture = texture;
        this.texWidth = texWidth;
        this.texHeight = texHeight;
        this.type = type;
        this.cuboids = cuboids == null ? Collections.<HatCuboid>emptyList() : cuboids;
        this.jsonElements = jsonElements == null ? Collections.<TailElement>emptyList() : jsonElements;
        this.jsonModel = jsonModel;
        this.animation = animation == null ? HatAnimation.NONE : animation;
        this.placement = placement == null ? ModelPlacement.NONE : placement;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String author() { return author; }
    public String mod() { return mod; }
    public boolean locked() { return locked; }
    public String howToUnlock() { return howToUnlock; }
    public ResourceLocation texture() { return texture; }
    public int texWidth() { return texWidth; }
    public int texHeight() { return texHeight; }
    public HatModelType type() { return type; }
    public List<HatCuboid> cuboids() { return cuboids; }
    public List<TailElement> jsonElements() { return jsonElements; }
    public TailModel jsonModel() { return jsonModel; }
    public HatAnimation animation() { return animation; }
    public ModelPlacement placement() { return placement; }

    public boolean isJsonModel() {
        return type == HatModelType.JSON;
    }
}
