package net.creeperhost.minetogethercommunity.util;

import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.oauth.KeycloakOAuth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNoCallback;

import java.net.URL;

/** Opens links originating from chat with the same settings and confirmation used by vanilla chat. */
public final class ChatLinkOpener {

    private ChatLinkOpener() {
    }

    public static void open(final URL url) {
        if (url == null) return;
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null || !mc.gameSettings.chatLinks) return;
        if (!mc.gameSettings.chatLinksPrompt) {
            openNow(url);
            return;
        }

        final GuiScreen parent = mc.currentScreen;
        mc.displayGuiScreen(new GuiConfirmOpenLink(new GuiYesNoCallback() {
            @Override
            public void confirmClicked(boolean result, int id) {
                if (result) {
                    openNow(url);
                }
                mc.displayGuiScreen(parent);
            }
        }, url.toExternalForm(), 0, false));
    }

    private static void openNow(URL url) {
        if (!KeycloakOAuth.openURL(url)) {
            MineTogetherChat.localStatus("minetogether.gui.chat.action.open_failed");
        }
    }
}
