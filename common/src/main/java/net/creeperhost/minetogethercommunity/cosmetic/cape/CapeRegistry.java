package net.creeperhost.minetogethercommunity.cosmetic.cape;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CapeRegistry {

    // -- Catalog (lightweight metadata) -----------------------------------------

    /** Returns the ordered list of all known cape metadata entries from the catalog. */
    public static List<CosmeticItem> catalog() {
        return CosmeticDownloader.instance().getCapeCatalog();
    }

    /** Returns the {@link CosmeticItem} for the given id, or {@code null} if not in catalog. */
    public static @Nullable CosmeticItem getCatalogEntry(String id) {
        return catalog().stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }

    // -- Loaded assets ----------------------------------------------------------

    /**
     * Returns the fully loaded {@link Cape} (texture registered) for the given id, or
     * {@code null} if the asset has not been downloaded yet.
     */
    public static @Nullable Cape getLoaded(String id) {
        return CosmeticDownloader.instance().getLoadedCape(id);
    }
}
