package net.creeperhost.minetogethercommunity.cosmetic.hat;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HatRegistry {

    private static final Map<String, HatModel> MODEL_CACHE = new HashMap<>();

    public static List<Hat> all() {
        return CosmeticDownloader.instance().getHats();
    }

    public static Hat get(String id) {
        return all().stream().filter(h -> h.id().equals(id)).findFirst().orElse(null);
    }

    public static HatModel getModel(Hat hat) {
        return MODEL_CACHE.computeIfAbsent(hat.id(), k -> new HatModel(hat));
    }

    public static void clearModelCache() {
        MODEL_CACHE.clear();
    }
}
