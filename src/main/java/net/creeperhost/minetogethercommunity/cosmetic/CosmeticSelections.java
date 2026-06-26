package net.creeperhost.minetogethercommunity.cosmetic;

public class CosmeticSelections {

    private static volatile CosmeticSelections instance;

    public volatile String selectedHatId = "";
    public volatile String selectedCapeId = "";
    public volatile String selectedTailId = "";
    public volatile String selectedWingId = "";
    public volatile boolean suppressVanillaCapeForPreview;
    public volatile boolean fullBrightPreview;

    public static CosmeticSelections instance() {
        if (instance == null) {
            synchronized (CosmeticSelections.class) {
                if (instance == null) {
                    instance = new CosmeticSelections();
                }
            }
        }
        return instance;
    }

    public void clear() {
        selectedHatId = "";
        selectedCapeId = "";
        selectedTailId = "";
        selectedWingId = "";
        suppressVanillaCapeForPreview = false;
        fullBrightPreview = false;
    }

    public String get(CosmeticTypes type) {
        switch (type) {
            case HAT:
                return selectedHatId;
            case CAPE:
                return selectedCapeId;
            case TAIL:
                return selectedTailId;
            case WINGS:
                return selectedWingId;
            default:
                return "";
        }
    }

    public void set(CosmeticTypes type, String id) {
        String safeId = id == null || "none".equals(id) ? "" : id;
        switch (type) {
            case HAT:
                selectedHatId = safeId;
                break;
            case CAPE:
                selectedCapeId = safeId;
                break;
            case TAIL:
                selectedTailId = safeId;
                break;
            case WINGS:
                selectedWingId = safeId;
                break;
            default:
                break;
        }
    }
}
