package net.creeperhost.minetogethercommunity.client;

import net.creeperhost.minetogethercommunity.gui.SettingGui;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticsGui;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteFavorites;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteRadialScreen;
import net.creeperhost.minetogethercommunity.gui.chat.FriendChatGui;
import net.creeperhost.minetogethercommunity.gui.chat.PublicChatGui;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import cpw.mods.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class Keybindings {

    private static final String CATEGORY = "key.categories.minetogethercommunity";
    public static final KeyBinding OPEN_FRIEND_CHAT = new KeyBinding("key.minetogethercommunity.friends", Keyboard.KEY_NONE, "key.categories.minetogethercommunity");
    public static final KeyBinding OPEN_GLOBAL_CHAT = new KeyBinding("key.minetogethercommunity.chat", Keyboard.KEY_NONE, "key.categories.minetogethercommunity");
    public static final KeyBinding OPEN_SETTINGS = new KeyBinding("key.minetogethercommunity.settings", Keyboard.KEY_NONE, "key.categories.minetogethercommunity");
    public static final KeyBinding OPEN_COSMETICS = new KeyBinding("key.minetogethercommunity.cosmetics", Keyboard.KEY_C, CATEGORY);
    public static final KeyBinding OPEN_EMOTES = new KeyBinding("key.minetogethercommunity.emotes", Keyboard.KEY_NONE, CATEGORY);
    public static final KeyBinding[] PLAY_FAVORITE_EMOTES = createFavoriteEmoteMappings();
    private static boolean wasAttackDown;
    private static boolean wasUseDown;
    private static boolean wasPickDown;

    public static void init() {
        ClientRegistry.registerKeyBinding(OPEN_FRIEND_CHAT);
        ClientRegistry.registerKeyBinding(OPEN_GLOBAL_CHAT);
        ClientRegistry.registerKeyBinding(OPEN_SETTINGS);
        ClientRegistry.registerKeyBinding(OPEN_COSMETICS);
        ClientRegistry.registerKeyBinding(OPEN_EMOTES);
        for (KeyBinding mapping : PLAY_FAVORITE_EMOTES) {
            ClientRegistry.registerKeyBinding(mapping);
        }
    }

    public static void handleInput() {
        Minecraft mc = Minecraft.getMinecraft();
        EmotePlayer.clientTick(mc);
        stopEmoteOnWorldInteraction(mc);
        if (OPEN_FRIEND_CHAT.isPressed() && MineTogetherChat.isChatEnabled()) {
            mc.displayGuiScreen(new FriendChatGui.Screen(mc.currentScreen));
        }
        if (OPEN_GLOBAL_CHAT.isPressed() && MineTogetherChat.isChatEnabled()) {
            mc.displayGuiScreen(new PublicChatGui.Screen(mc.currentScreen));
        }
        if (OPEN_SETTINGS.isPressed()) {
            mc.displayGuiScreen(new SettingGui.Screen(mc.currentScreen));
        }
        if (OPEN_COSMETICS.isPressed()) {
            mc.displayGuiScreen(new CosmeticsGui.Screen(mc.currentScreen));
        }
        if (OPEN_EMOTES.isPressed()) {
            mc.displayGuiScreen(new EmoteRadialScreen());
        }
        for (int i = 0; i < PLAY_FAVORITE_EMOTES.length; i++) {
            if (PLAY_FAVORITE_EMOTES[i].isPressed()) {
                playFavoriteEmote(i);
            }
        }
    }

    private static void stopEmoteOnWorldInteraction(Minecraft mc) {
        if (mc.thePlayer == null || mc.currentScreen != null) {
            wasAttackDown = false;
            wasUseDown = false;
            wasPickDown = false;
            return;
        }
        boolean attackDown = mc.gameSettings.keyBindAttack.getIsKeyPressed();
        boolean useDown = mc.gameSettings.keyBindUseItem.getIsKeyPressed();
        boolean pickDown = mc.gameSettings.keyBindPickBlock.getIsKeyPressed();
        if ((attackDown && !wasAttackDown) || (useDown && !wasUseDown) || (pickDown && !wasPickDown)) {
            EmotePlayer.stopLocal();
        }
        wasAttackDown = attackDown;
        wasUseDown = useDown;
        wasPickDown = pickDown;
    }

    private static KeyBinding[] createFavoriteEmoteMappings() {
        KeyBinding[] mappings = new KeyBinding[EmoteFavorites.MAX_FAVORITES];
        for (int i = 0; i < mappings.length; i++) {
            mappings[i] = new KeyBinding("key.minetogethercommunity.emote_favorite_" + (i + 1), Keyboard.KEY_NONE, CATEGORY);
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
