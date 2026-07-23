package net.creeperhost.minetogethercommunity.cosmetic.emote;

import dev.architectury.platform.Platform;

import java.util.Locale;

public enum EmoteType {
    SIMPLE("simple", null),
    GECKOLIB("geckolib", "geckolib"),
    UNKNOWN("unknown", null);

    private final String metadataValue;
    private final String requiredMod;

    EmoteType(String metadataValue, String requiredMod) {
        this.metadataValue = metadataValue;
        this.requiredMod = requiredMod;
    }

    public String metadataValue() {
        return metadataValue;
    }

    public boolean isAvailable() {
        if (this == UNKNOWN) return false;
        return requiredMod == null || Platform.isModLoaded(requiredMod);
    }

    public boolean isKnown() {
        return this != UNKNOWN;
    }

    public static EmoteType fromMetadata(String value) {
        if (value == null || value.isBlank()) return SIMPLE;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (EmoteType type : values()) {
            if (type == UNKNOWN) continue;
            if (type.metadataValue.equals(normalized)) return type;
        }
        return UNKNOWN;
    }
}
