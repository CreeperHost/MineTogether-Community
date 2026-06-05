package net.creeperhost.minetogethercommunity.cosmetic.cape;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;

import java.util.List;

public class CapeRegistry {

    public static List<Cape> all() {
        return CosmeticDownloader.instance().getCapes();
    }

    public static Cape get(String id) {
        return all().stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }
}
