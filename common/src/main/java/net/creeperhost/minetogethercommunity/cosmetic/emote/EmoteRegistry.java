package net.creeperhost.minetogethercommunity.cosmetic.emote;

import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;

public class EmoteRegistry {

    public static Emote getLoaded(String id) {
        if (id == null || id.isEmpty()) return null;
        Emote emote = CosmeticDownloader.instance().getLoadedEmote(id);
        if (emote == null) {
            CosmeticDownloader.instance().ensureAssetLoaded("emote", id);
        }
        return emote;
    }
}
