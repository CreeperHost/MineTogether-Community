package net.creeperhost.minetogethercommunity;

import com.mojang.blaze3d.platform.InputConstants;
import net.creeperhost.minetogethercommunity.chat.gui.FriendChatGui;
import net.creeperhost.minetogethercommunity.chat.gui.PublicChatGui;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteFavorites;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteRadialScreen;
import net.creeperhost.minetogethercommunity.gui.SettingGui;
import net.creeperhost.polylib.event.events.client.PolyClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Created by brandon3055 on 05/09/2024
 */
public class Keybindings {

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MineTogether.MOD_ID, "main"));

    public static final KeyMapping OPEN_FRIEND_CHAT = new KeyMapping("minetogether:keybind.friend_chat", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping OPEN_GLOBAL_CHAT = new KeyMapping("minetogether:keybind.global_chat", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping("minetogether:keybind.settings", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping OPEN_EMOTES = new KeyMapping("minetogether:keybind.emotes", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping[] PLAY_FAVORITE_EMOTES = createFavoriteEmoteMappings();

    public static void init() {
        MineTogetherPlatform.registerKeyMapping(OPEN_FRIEND_CHAT);
        MineTogetherPlatform.registerKeyMapping(OPEN_GLOBAL_CHAT);
        MineTogetherPlatform.registerKeyMapping(OPEN_SETTINGS);
        MineTogetherPlatform.registerKeyMapping(OPEN_EMOTES);
        for (KeyMapping mapping : PLAY_FAVORITE_EMOTES) {
            MineTogetherPlatform.registerKeyMapping(mapping);
        }
        PolyClientTickEvents.CLIENT_TICK_END.register(Keybindings::clientTick);
    }

    private static void clientTick(Minecraft mc) {
        if (OPEN_FRIEND_CHAT.consumeClick()) {
            mc.gui.setScreen(new FriendChatGui.Screen(null));
        } else if (OPEN_GLOBAL_CHAT.consumeClick()) {
            mc.gui.setScreen(new PublicChatGui.Screen(null));
        } else if (OPEN_SETTINGS.consumeClick()) {
            mc.gui.setScreen(new SettingGui.Screen(null));
        } else if (OPEN_EMOTES.consumeClick()) {
            mc.gui.setScreen(new EmoteRadialScreen());
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
