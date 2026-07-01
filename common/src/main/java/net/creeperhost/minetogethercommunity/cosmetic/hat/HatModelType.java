package net.creeperhost.minetogethercommunity.cosmetic.hat;

import java.util.Locale;

public enum HatModelType {
    TC2("tc2"),
    JSON("json");

    private final String metadataValue;

    HatModelType(String metadataValue) {
        this.metadataValue = metadataValue;
    }

    public String metadataValue() {
        return metadataValue;
    }

    public static HatModelType fromMetadata(String value) {
        if (value == null || value.isBlank()) return TC2;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (HatModelType type : values()) {
            if (type.metadataValue.equals(normalized)) return type;
        }
        return TC2;
    }
}
