package net.creeperhost.minetogethercommunity.connect.gui;

import net.creeperhost.minetogethercommunity.connect.ConnectPackResolver;
import net.creeperhost.minetogethercommunity.connect.RemoteServer;
import net.creeperhost.minetogethercommunity.gui.chat.MTStyle;
import net.creeperhost.minetogethercommunity.modulargui.GuiButton;
import net.creeperhost.minetogethercommunity.modulargui.GuiElement;
import net.creeperhost.minetogethercommunity.modulargui.GuiProvider;
import net.creeperhost.minetogethercommunity.modulargui.GuiRectangle;
import net.creeperhost.minetogethercommunity.modulargui.GuiText;
import net.creeperhost.minetogethercommunity.modulargui.ModularGui;
import net.creeperhost.minetogethercommunity.modulargui.ModularGuiScreen;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentString;

public class ConnectPackWarningScreen implements GuiProvider {

    private final GuiScreen parent;
    private final RemoteServer server;

    private ConnectPackWarningScreen(GuiScreen parent, RemoteServer server) {
        this.parent = parent;
        this.server = server;
    }

    @Override
    public GuiElement<?> createRootElement(final ModularGui gui) {
        ConnectPackResolver.prefetch(server.getModpackKey());

        gui.setPauseScreen(false);
        gui.renderScreenBackground(false);
        gui.setGuiTitle(new TextComponentString(I18n.format("minetogether.connect.pack_warning.title")));

        int screenWidth = gui.getScreen().width;
        int screenHeight = gui.getScreen().height;
        GuiElement<?> root = new GuiElement<>(gui);
        new GuiRectangle(root, MTStyle.Flat.BACKGROUND).setBounds(0, 0, screenWidth, screenHeight);

        int width = Math.min(330, screenWidth - 20);
        int left = screenWidth / 2 - width / 2;
        int top = screenHeight / 2 - 56;

        new GuiText(root, () -> I18n.format("minetogether.connect.pack_warning.title"))
                .centered()
                .setBounds(left, top, width, 10);
        new GuiText(root, this::message)
                .setWrap(true)
                .setBounds(left, top + 22, width, 48);

        new GuiButton(root, () -> I18n.format("minetogether.connect.pack_warning.proceed"))
                .primary()
                .setBounds(left, top + 88, width / 2 - 3, 16)
                .onPress(() -> gui.mc().displayGuiScreen(new FriendConnectScreen(parent, server)));
        new GuiButton(root, () -> I18n.format("minetogether.gui.button.cancel"))
                .setBounds(left + width / 2 + 3, top + 88, width - (width / 2 + 3), 16)
                .onPress(() -> gui.mc().displayGuiScreen(parent));

        return root;
    }

    private String message() {
        return server.getCompatibility() == RemoteServer.PackCompatibility.DIFFERENT
                ? I18n.format("minetogether.connect.pack_warning.different", ConnectPackResolver.displayName(server.getModpackKey()))
                : I18n.format("minetogether.connect.pack_warning.unknown");
    }

    public static class Screen extends ModularGuiScreen {
        public Screen(GuiScreen parentScreen, RemoteServer server) {
            super(new ConnectPackWarningScreen(parentScreen, server), parentScreen);
        }
    }
}
