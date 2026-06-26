package net.creeperhost.minetogethercommunity.cosmetic.wing;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticItem;

import java.util.List;

public final class WingRegistry {

    private WingRegistry() {
    }

    public static List<CosmeticItem> catalog() {
        return CosmeticDownloader.instance().getWingCatalog();
    }

    public static Wing getLoaded(String id) {
        return CosmeticDownloader.instance().getLoadedWing(id);
    }
}
