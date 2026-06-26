package net.creeperhost.minetogethercommunity.client;

import net.creeperhost.minetogethercommunity.gui.SettingGui;
import net.creeperhost.minetogethercommunity.gui.chat.FriendChatGui;
import net.creeperhost.minetogethercommunity.gui.chat.PublicChatGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import cpw.mods.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;

public class Keybindings {

    public static final KeyBinding OPEN_FRIEND_CHAT = new KeyBinding("key.minetogethercommunity.friends", Keyboard.KEY_NONE, "key.categories.minetogethercommunity");
    public static final KeyBinding OPEN_GLOBAL_CHAT = new KeyBinding("key.minetogethercommunity.chat", Keyboard.KEY_NONE, "key.categories.minetogethercommunity");
    public static final KeyBinding OPEN_SETTINGS = new KeyBinding("key.minetogethercommunity.settings", Keyboard.KEY_NONE, "key.categories.minetogethercommunity");

    public static void init() {
        ClientRegistry.registerKeyBinding(OPEN_FRIEND_CHAT);
        ClientRegistry.registerKeyBinding(OPEN_GLOBAL_CHAT);
        ClientRegistry.registerKeyBinding(OPEN_SETTINGS);
    }

    public static void handleInput() {
        Minecraft mc = Minecraft.getMinecraft();
        if (OPEN_FRIEND_CHAT.isPressed()) {
            mc.displayGuiScreen(new FriendChatGui.Screen(mc.currentScreen));
        }
        if (OPEN_GLOBAL_CHAT.isPressed()) {
            mc.displayGuiScreen(new PublicChatGui.Screen(mc.currentScreen));
        }
        if (OPEN_SETTINGS.isPressed()) {
            mc.displayGuiScreen(new SettingGui.Screen(mc.currentScreen));
        }
    }
}
