package net.creeperhost.minetogethercommunity.compat.kubejs;

import net.creeperhost.minetogethercommunity.chat.gui.FriendChatGui;
import net.creeperhost.minetogethercommunity.chat.gui.PublicChatGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;

public interface KubeJSUIIntegration {

    Consumer<Screen> CHAT = screen -> Minecraft.getInstance().gui.setScreen(new PublicChatGui.Screen(screen));
    Consumer<Screen> FRIENDS_LIST = screen -> Minecraft.getInstance().gui.setScreen(new FriendChatGui.Screen(screen));
}
