package net.creeperhost.minetogethercommunity.cosmetic;

public class CosmeticItem {

    private final String id;
    private final String displayName;
    private final String author;
    private final String mod;
    private final boolean locked;
    private final String howToUnlock;

    public CosmeticItem(String id, String displayName, String author, String mod, boolean locked, String howToUnlock) {
        this.id = id == null ? "" : id;
        this.displayName = displayName == null || displayName.isEmpty() ? this.id : displayName;
        this.author = author == null ? "" : author;
        this.mod = mod == null ? "" : mod;
        this.locked = locked;
        this.howToUnlock = howToUnlock;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String author() {
        return author;
    }

    public String mod() {
        return mod;
    }

    public boolean locked() {
        return locked;
    }

    public String howToUnlock() {
        return howToUnlock;
    }
}
