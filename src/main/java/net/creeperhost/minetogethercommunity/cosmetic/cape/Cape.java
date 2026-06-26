package net.creeperhost.minetogethercommunity.cosmetic.cape;

import net.minecraft.util.ResourceLocation;

public class Cape {

    private final String id;
    private final String displayName;
    private final String author;
    private final String mod;
    private final boolean locked;
    private final String howToUnlock;
    private final ResourceLocation texture;
    private final int texWidth;
    private final int texHeight;

    public Cape(String id, String displayName, String author, String mod, boolean locked, String howToUnlock,
                ResourceLocation texture, int texWidth, int texHeight) {
        this.id = id;
        this.displayName = displayName;
        this.author = author;
        this.mod = mod;
        this.locked = locked;
        this.howToUnlock = howToUnlock;
        this.texture = texture;
        this.texWidth = texWidth;
        this.texHeight = texHeight;
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
}
