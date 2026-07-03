package net.creeperhost.minetogethercommunity;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.creeperhost.minetogethercommunity.chat.gui.FriendChatGui;
import net.creeperhost.minetogethercommunity.chat.gui.PublicChatGui;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteFavorites;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteRadialScreen;
import net.creeperhost.minetogethercommunity.gui.SettingGui;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Created by brandon3055 on 05/09/2024
 */
public class Keybindings {

    private static final String CATEGORY = "key.categories.minetogether";

    public static final KeyMapping OPEN_FRIEND_CHAT = new KeyMapping("key.minetogether.friend_chat", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping OPEN_GLOBAL_CHAT = new KeyMapping("key.minetogether.global_chat", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping("key.minetogether.settings", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping OPEN_EMOTES = new KeyMapping("key.minetogether.emotes", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping[] PLAY_FAVORITE_EMOTES = createFavoriteEmoteMappings();

    public static void init() {
        KeyMappingRegistry.register(OPEN_FRIEND_CHAT);
        KeyMappingRegistry.register(OPEN_GLOBAL_CHAT);
        KeyMappingRegistry.register(OPEN_SETTINGS);
        KeyMappingRegistry.register(OPEN_EMOTES);
        for (KeyMapping mapping : PLAY_FAVORITE_EMOTES) {
            KeyMappingRegistry.register(mapping);
        }
        ClientTickEvent.CLIENT_POST.register(Keybindings::clientTick);
    }

    private static void clientTick(Minecraft mc) {
        if (OPEN_FRIEND_CHAT.consumeClick()) {
            mc.setScreen(new FriendChatGui.Screen(null));
        } else if (OPEN_GLOBAL_CHAT.consumeClick()) {
            mc.setScreen(new PublicChatGui.Screen(null));
        } else if (OPEN_SETTINGS.consumeClick()) {
            mc.setScreen(new SettingGui.Screen(null));
        } else if (OPEN_EMOTES.consumeClick()) {
            mc.setScreen(new EmoteRadialScreen());
        }
        for (int i = 0; i < PLAY_FAVORITE_EMOTES.length; i++) {
            if (PLAY_FAVORITE_EMOTES[i].consumeClick()) {
                playFavoriteEmote(i);
            }
        }
    }

    private static KeyMapping[] createFavoriteEmoteMappings() {
        KeyMapping[] mappings = new KeyMapping[EmoteFavorites.MAX_FAVORITES];
        for (int i = 0; i < mappings.length; i++) {
            mappings[i] = new KeyMapping("key.minetogether.emote_favorite_" + (i + 1), InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
        }
        return mappings;
    }

    private static void playFavoriteEmote(int index) {
        List<String> favorites = EmoteFavorites.ids();
        if (index < 0 || index >= favorites.size()) return;
        String emoteId = favorites.get(index);
        if (emoteId == null || emoteId.isEmpty()) return;
        CosmeticDownloader.instance().ensureAssetLoaded("emote", emoteId);
        EmotePlayer.playLocal(emoteId);
    }
}
