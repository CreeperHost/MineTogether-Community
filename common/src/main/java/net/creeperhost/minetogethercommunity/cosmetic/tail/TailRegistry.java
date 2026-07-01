package net.creeperhost.minetogethercommunity.cosmetic.tail;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TailRegistry {

    public static List<CosmeticItem> catalog() {
        return CosmeticDownloader.instance().getTailCatalog();
    }

    public static @Nullable CosmeticItem getCatalogEntry(String id) {
        return catalog().stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }

    public static @Nullable Tail getLoaded(String id) {
        return CosmeticDownloader.instance().getLoadedTail(id);
    }
}
