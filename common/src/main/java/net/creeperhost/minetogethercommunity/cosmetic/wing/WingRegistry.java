package net.creeperhost.minetogethercommunity.cosmetic.wing;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WingRegistry {

    public static List<CosmeticItem> catalog() {
        return CosmeticDownloader.instance().getWingCatalog();
    }

    public static @Nullable CosmeticItem getCatalogEntry(String id) {
        return catalog().stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }

    public static @Nullable Wing getLoaded(String id) {
        return CosmeticDownloader.instance().getLoadedWing(id);
    }
}
