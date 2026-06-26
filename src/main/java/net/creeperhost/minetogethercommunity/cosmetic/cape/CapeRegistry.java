package net.creeperhost.minetogethercommunity.cosmetic.cape;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticItem;

import java.util.List;

public final class CapeRegistry {

    private CapeRegistry() {
    }

    public static List<CosmeticItem> catalog() {
        return CosmeticDownloader.instance().getCapeCatalog();
    }

    public static CosmeticItem getCatalogEntry(String id) {
        for (CosmeticItem item : catalog()) {
            if (item.id().equals(id)) return item;
        }
        return null;
    }

    public static Cape getLoaded(String id) {
        return CosmeticDownloader.instance().getLoadedCape(id);
    }
}
