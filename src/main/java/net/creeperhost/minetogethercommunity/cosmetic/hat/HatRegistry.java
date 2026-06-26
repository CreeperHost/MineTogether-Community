package net.creeperhost.minetogethercommunity.cosmetic.hat;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HatRegistry {

    private static final Map<String, HatModel> MODEL_CACHE = new HashMap<String, HatModel>();

    private HatRegistry() {
    }

    public static List<CosmeticItem> catalog() {
        return CosmeticDownloader.instance().getHatCatalog();
    }

    public static CosmeticItem getCatalogEntry(String id) {
        for (CosmeticItem item : catalog()) {
            if (item.id().equals(id)) return item;
        }
        return null;
    }

    public static Hat getLoaded(String id) {
        return CosmeticDownloader.instance().getLoadedHat(id);
    }

    public static HatModel getModel(Hat hat) {
        HatModel model = MODEL_CACHE.get(hat.id());
        if (model == null) {
            model = new HatModel(hat);
            MODEL_CACHE.put(hat.id(), model);
        }
        return model;
    }

    public static void clearModelCache() {
        MODEL_CACHE.clear();
    }
}
