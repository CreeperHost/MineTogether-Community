package net.creeperhost.minetogethercommunity.cosmetic.emote;

import net.creeperhost.minetogethercommunity.config.LocalConfig;

import java.util.ArrayList;
import java.util.List;

public final class EmoteFavorites {

    public static final int MAX_FAVORITES = 8;

    private EmoteFavorites() {
    }

    public static List<String> ids() {
        LocalConfig config = LocalConfig.instance();
        if (config.favoriteEmoteIds == null) {
            config.favoriteEmoteIds = new ArrayList<String>();
            LocalConfig.save();
        }
        if (config.favoriteEmoteIds.size() > MAX_FAVORITES) {
            config.favoriteEmoteIds = new ArrayList<String>(config.favoriteEmoteIds.subList(0, MAX_FAVORITES));
            LocalConfig.save();
        }
        return config.favoriteEmoteIds;
    }

    public static boolean isFavorite(String id) {
        return ids().contains(id);
    }

    public static boolean canAddMore() {
        return ids().size() < MAX_FAVORITES;
    }

    public static boolean toggle(String id) {
        if (id == null || id.isEmpty()) return false;
        List<String> favorites = ids();
        boolean favorite;
        if (favorites.remove(id)) {
            favorite = false;
        } else {
            if (favorites.size() >= MAX_FAVORITES) return false;
            favorites.add(id);
            favorite = true;
        }
        LocalConfig.save();
        return favorite;
    }
}
