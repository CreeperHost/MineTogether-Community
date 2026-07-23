package net.creeperhost.minetogethercommunity.cosmetic.tail;

import net.minecraft.util.ResourceLocation;
import net.creeperhost.minetogethercommunity.cosmetic.ModelPlacement;

import java.util.List;

public class Tail {

    private final String id;
    private final String displayName;
    private final String author;
    private final String mod;
    private final boolean locked;
    private final String howToUnlock;
    private final ResourceLocation texture;
    private final int texWidth;
    private final int texHeight;
    private final List<TailElement> elements;
    private final TailModel model;
    private final TailAnimation animation;
    private final ModelPlacement placement;

    public Tail(String id, String displayName, String author, String mod, boolean locked, String howToUnlock,
                ResourceLocation texture, int texWidth, int texHeight, List<TailElement> elements, TailModel model) {
        this(id, displayName, author, mod, locked, howToUnlock, texture, texWidth, texHeight, elements, model, TailAnimation.NONE);
    }

    public Tail(String id, String displayName, String author, String mod, boolean locked, String howToUnlock,
                ResourceLocation texture, int texWidth, int texHeight, List<TailElement> elements, TailModel model,
                TailAnimation animation) {
        this(id, displayName, author, mod, locked, howToUnlock, texture, texWidth, texHeight, elements, model,
                animation, new ModelPlacement(-8.0F, 2.0F, 2.0F, 1.0F));
    }

    public Tail(String id, String displayName, String author, String mod, boolean locked, String howToUnlock,
                ResourceLocation texture, int texWidth, int texHeight, List<TailElement> elements, TailModel model,
                TailAnimation animation, ModelPlacement placement) {
        this.id = id;
        this.displayName = displayName;
        this.author = author;
        this.mod = mod;
        this.locked = locked;
        this.howToUnlock = howToUnlock;
        this.texture = texture;
        this.texWidth = texWidth;
        this.texHeight = texHeight;
        this.elements = elements;
        this.model = model;
        this.animation = animation == null ? TailAnimation.NONE : animation;
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
    public List<TailElement> elements() { return elements; }
    public TailModel model() { return model; }
    public TailAnimation animation() { return animation; }
    public ModelPlacement placement() { return placement; }
}
