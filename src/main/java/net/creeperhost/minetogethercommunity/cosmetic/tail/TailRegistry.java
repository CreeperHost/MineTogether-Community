package net.creeperhost.minetogethercommunity.cosmetic.tail;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticItem;

import java.util.List;

public final class TailRegistry {

    private TailRegistry() {
    }

    public static List<CosmeticItem> catalog() {
        return CosmeticDownloader.instance().getTailCatalog();
    }

    public static Tail getLoaded(String id) {
        return CosmeticDownloader.instance().getLoadedTail(id);
    }
}
