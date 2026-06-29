package net.creeperhost.minetogethercommunity;

import com.mojang.blaze3d.platform.InputConstants;
import net.creeperhost.minetogethercommunity.chat.gui.FriendChatGui;
import net.creeperhost.minetogethercommunity.chat.gui.PublicChatGui;
import net.creeperhost.minetogethercommunity.gui.SettingGui;
import net.creeperhost.polylib.event.events.client.PolyClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Created by brandon3055 on 05/09/2024
 */
public class Keybindings {

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MineTogether.MOD_ID, "main"));

    public static final KeyMapping OPEN_FRIEND_CHAT = new KeyMapping("minetogether:keybind.friend_chat", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping OPEN_GLOBAL_CHAT = new KeyMapping("minetogether:keybind.global_chat", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping("minetogether:keybind.settings", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);

    public static void init() {
        MineTogetherPlatform.registerKeyMapping(OPEN_FRIEND_CHAT);
        MineTogetherPlatform.registerKeyMapping(OPEN_GLOBAL_CHAT);
        MineTogetherPlatform.registerKeyMapping(OPEN_SETTINGS);
        PolyClientTickEvents.CLIENT_TICK_END.register(Keybindings::clientTick);
    }

    private static void clientTick(Minecraft mc) {
        if (OPEN_FRIEND_CHAT.consumeClick()) {
            mc.setScreen(new FriendChatGui.Screen(null));
        } else if (OPEN_GLOBAL_CHAT.consumeClick()) {
            mc.setScreen(new PublicChatGui.Screen(null));
        } else if (OPEN_SETTINGS.consumeClick()) {
            mc.setScreen(new SettingGui.Screen(null));
        }
    }
}
