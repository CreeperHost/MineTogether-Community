package net.creeperhost.minetogethercommunity.cosmetic.emote;

public class Emote {

    private final String id;
    private final String displayName;
    private final String author;
    private final String mod;
    private final boolean locked;
    private final String howToUnlock;
    private final EmoteType type;
    private final boolean toggle;
    private final boolean allowMovement;
    private final boolean requiresMovement;
    private final float previewFrame;
    private final float previewHeight;
    private final EmoteAnimation animation;

    public Emote(String id, String displayName, String author, String mod, boolean locked, String howToUnlock,
                 EmoteType type, boolean toggle, boolean allowMovement, boolean requiresMovement, float previewFrame, float previewHeight,
                 EmoteAnimation animation) {
        this.id = id;
        this.displayName = displayName;
        this.author = author;
        this.mod = mod;
        this.locked = locked;
        this.howToUnlock = howToUnlock;
        this.type = type == null ? EmoteType.SIMPLE : type;
        this.toggle = toggle;
        this.allowMovement = allowMovement;
        this.requiresMovement = requiresMovement;
        this.previewFrame = previewFrame;
        this.previewHeight = previewHeight;
        this.animation = animation == null ? EmoteAnimation.WAVE : animation;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String author() { return author; }
    public String mod() { return mod; }
    public boolean locked() { return locked; }
    public String howToUnlock() { return howToUnlock; }
    public EmoteType type() { return type; }
    public boolean toggle() { return toggle; }
    public boolean allowMovement() { return allowMovement; }
    public boolean requiresMovement() { return requiresMovement; }
    public float previewFrame() { return previewFrame; }
    public float previewHeight() { return previewHeight; }
    public EmoteAnimation animation() { return animation; }
}
