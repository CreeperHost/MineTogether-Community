package net.creeperhost.minetogethercommunity.cosmetic.hat;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticItem;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HatRegistry {

    private static final Map<String, HatModel> MODEL_CACHE = new HashMap<>();

    // -- Catalog (lightweight metadata) -----------------------------------------

    /** Returns the ordered list of all known hat metadata entries from the catalog. */
    public static List<CosmeticItem> catalog() {
        return CosmeticDownloader.instance().getHatCatalog();
    }

    /** Returns the {@link CosmeticItem} for the given id, or {@code null} if not in catalog. */
    public static @Nullable CosmeticItem getCatalogEntry(String id) {
        return catalog().stream().filter(h -> h.id().equals(id)).findFirst().orElse(null);
    }

    // -- Loaded assets ----------------------------------------------------------

    /**
     * Returns the fully loaded {@link Hat} (model geometry + texture registered) for the
     * given id, or {@code null} if the asset has not been downloaded yet.
     * <p>
     * Render layers should return early when this is {@code null} - the asset will appear
     * once {@link CosmeticDownloader#ensureAssetLoaded(String, String)} completes.
     */
    public static @Nullable Hat getLoaded(String id) {
        return CosmeticDownloader.instance().getLoadedHat(id);
    }

    // -- Model cache ------------------------------------------------------------

    /**
     * Returns the {@link HatModel} for the given hat, creating it on first access.
     * Called from the render thread; the model is cached for the lifetime of the session.
     */
    public static HatModel getModel(Hat hat) {
        return MODEL_CACHE.computeIfAbsent(hat.id(), k -> new HatModel(hat));
    }

    public static void clearModelCache() {
        MODEL_CACHE.clear();
    }
}
