package net.creeperhost.minetogethercommunity.cosmetic.emote;

public record Emote(
        String id,
        String displayName,
        String author,
        String mod,
        boolean locked,
        String howToUnlock,
        EmoteType type,
        boolean toggle,
        boolean allowMovement,
        float previewFrame,
        float previewHeight,
        EmoteAnimation animation
) {
}
