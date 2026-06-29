package net.creeperhost.minetogethercommunity.connect.gui;

import net.creeperhost.minetogethercommunity.chat.gui.MTStyle;
import net.creeperhost.minetogethercommunity.connect.ConnectPackResolver;
import net.creeperhost.minetogethercommunity.connect.RemoteServer;
import net.creeperhost.polylib.client.modulargui.ModularGui;
import net.creeperhost.polylib.client.modulargui.ModularGuiScreen;
import net.creeperhost.polylib.client.modulargui.elements.GuiElement;
import net.creeperhost.polylib.client.modulargui.elements.GuiText;
import net.creeperhost.polylib.client.modulargui.lib.Constraints;
import net.creeperhost.polylib.client.modulargui.lib.GuiProvider;
import net.minecraft.client.server.LanServer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import static net.creeperhost.polylib.client.modulargui.lib.geometry.Constraint.*;
import static net.creeperhost.polylib.client.modulargui.lib.geometry.GeoParam.*;

public class ConnectPackWarningScreen implements GuiProvider {

    private final @Nullable net.minecraft.client.gui.screens.Screen parent;
    private final RemoteServer server;
    private final LanServer serverData;

    private ConnectPackWarningScreen(@Nullable net.minecraft.client.gui.screens.Screen parent, RemoteServer server, LanServer serverData) {
        this.parent = parent;
        this.server = server;
        this.serverData = serverData;
    }

    @Override
    public GuiElement<?> createRootElement(ModularGui gui) {
        return MTStyle.Flat.background(gui);
    }

    @Override
    public void buildGui(ModularGui gui) {
        ConnectPackResolver.prefetch(server.modpackKey);

        gui.renderScreenBackground(false);
        gui.initFullscreenGui();
        gui.setGuiTitle(Component.translatable("minetogether.connect.pack_warning.title"));

        GuiElement<?> root = gui.getRoot();
        GuiElement<?> bounds = new GuiElement<>(root);
        Constraints.size(bounds, 330, 112);
        Constraints.center(bounds, root);

        GuiText title = new GuiText(root, gui.getGuiTitle())
                .constrain(TOP, match(bounds.get(TOP)))
                .constrain(LEFT, match(bounds.get(LEFT)))
                .constrain(RIGHT, match(bounds.get(RIGHT)))
                .constrain(HEIGHT, literal(10));

        GuiText message = new GuiText(root, Component.empty())
                .setTextSupplier(this::message)
                .setWrap(true)
                .autoHeight()
                .constrain(TOP, relative(title.get(BOTTOM), 12))
                .constrain(LEFT, match(bounds.get(LEFT)))
                .constrain(RIGHT, match(bounds.get(RIGHT)));

        MTStyle.Flat.buttonPrimary(root, Component.translatable("minetogether.connect.pack_warning.proceed"))
                .onPress(() -> FriendConnectScreen.startConnecting(parent, gui.mc(), server, serverData))
                .constrain(BOTTOM, match(bounds.get(BOTTOM)))
                .constrain(LEFT, match(bounds.get(LEFT)))
                .constrain(RIGHT, midPoint(bounds.get(LEFT), bounds.get(RIGHT), -3))
                .constrain(HEIGHT, literal(16));

        MTStyle.Flat.button(root, Component.translatable("minetogether:gui.button.cancel"))
                .onPress(() -> gui.mc().setScreen(parent))
                .constrain(BOTTOM, match(bounds.get(BOTTOM)))
                .constrain(LEFT, midPoint(bounds.get(LEFT), bounds.get(RIGHT), 3))
                .constrain(RIGHT, match(bounds.get(RIGHT)))
                .constrain(HEIGHT, literal(16));
    }

    private Component message() {
        return server.compatibility == RemoteServer.PackCompatibility.DIFFERENT
                ? Component.translatable("minetogether.connect.pack_warning.different", ConnectPackResolver.displayName(server.modpackKey))
                : Component.translatable("minetogether.connect.pack_warning.unknown");
    }

    public static class Screen extends ModularGuiScreen {
        public Screen(@Nullable net.minecraft.client.gui.screens.Screen parentScreen, RemoteServer server, LanServer serverData) {
            super(new ConnectPackWarningScreen(parentScreen, server, serverData), parentScreen);
        }
    }
}
