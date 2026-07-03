package net.creeperhost.minetogethercommunity.cosmetic.emote;

import net.creeperhost.minetogethercommunity.MineTogetherPlatform;

import java.util.Locale;

public enum EmoteType {
    SIMPLE("simple", true),
    GECKOLIB("geckolib", false);

    private final String metadataValue;
    private final boolean builtInRuntime;

    EmoteType(String metadataValue, boolean builtInRuntime) {
        this.metadataValue = metadataValue;
        this.builtInRuntime = builtInRuntime;
    }

    public String metadataValue() {
        return metadataValue;
    }

    public boolean isAvailable() {
        return builtInRuntime || MineTogetherPlatform.isModLoaded("geckolib");
    }

    public static EmoteType fromMetadata(String value) {
        if (value == null || value.isBlank()) return SIMPLE;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (EmoteType type : values()) {
            if (type.metadataValue.equals(normalized)) return type;
        }
        return SIMPLE;
    }
}
