package net.creeperhost.minetogethercommunity.cosmetic;

public enum CosmeticTypes {
    HAT("hat", "minetogether.gui.cosmetics.tab.hat"),
    CAPE("cape", "minetogether.gui.cosmetics.tab.cape"),
    TAIL("tail", "minetogether.gui.cosmetics.tab.tail"),
    WINGS("wing", "minetogether.gui.cosmetics.tab.wings");

    private final String slotName;
    private final String translationKey;

    CosmeticTypes(String slotName, String translationKey) {
        this.slotName = slotName;
        this.translationKey = translationKey;
    }

    public String slotName() {
        return slotName;
    }

    public String translationKey() {
        return translationKey;
    }
}
