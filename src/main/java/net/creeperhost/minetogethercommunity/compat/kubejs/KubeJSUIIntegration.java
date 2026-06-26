package net.creeperhost.minetogethercommunity.compat.kubejs;

import net.creeperhost.minetogethercommunity.gui.chat.FriendChatGui;
import net.creeperhost.minetogethercommunity.gui.chat.PublicChatGui;
import net.creeperhost.minetogethercommunity.compat.MTPartners;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import java.util.function.Consumer;

public interface KubeJSUIIntegration {

    Consumer<GuiScreen> CHAT = screen -> Minecraft.getMinecraft().displayGuiScreen(new PublicChatGui.Screen(screen));
    Consumer<GuiScreen> FRIENDS_LIST = screen -> Minecraft.getMinecraft().displayGuiScreen(new FriendChatGui.Screen(screen));
    Consumer<GuiScreen> ORDER = screen -> MTPartners.openOrderUI(screen, true);
}
