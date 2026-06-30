package net.creeperhost.minetogethercommunity.connect;

import net.creeperhost.minetogethercommunity.config.Config;
import net.creeperhost.minetogethercommunity.connect.gui.ConnectPackSelectionScreen;
import net.creeperhost.minetogethercommunity.connect.gui.GuiShareToFriends;
import net.creeperhost.polylib.client.screen.ButtonHelper;
import net.creeperhost.polylib.event.events.client.PolyScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

import java.util.List;

public class MineTogetherConnect {

    public static boolean isInitted = false;

    public static void init() {
        isInitted = true;
        ConnectHandler.init();

        PolyScreenEvents.SCREEN_OPENED.register(MineTogetherConnect::onScreenOpen);
    }

    private static void onScreenOpen(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (screen instanceof TitleScreen) {
            ConnectPackSelectionScreen.promptIfNeeded(screen);
            return;
        }

        if (!(screen instanceof PauseScreen)) return;

        IntegratedServer integratedServer = Minecraft.getInstance().getSingleplayerServer();
        if (integratedServer == null) return;

        boolean isConnectPublished = ConnectHandler.isPublished();
        Component buttonText = isConnectPublished ? Component.translatable("minetogether.connect.close") : Component.translatable("minetogether.connect.open");
        Button.OnPress action = button -> {
            if (isConnectPublished) {
                ConnectHandler.unPublish();
                Minecraft.getInstance().gui.setScreen(new PauseScreen(true));
            } else {
                Minecraft.getInstance().gui.setScreen(new GuiShareToFriends.Screen(screen));
            }
        };

        @SuppressWarnings ("unchecked")
        List<GuiEventListener> children = (List<GuiEventListener>) screen.children();
        List<Renderable> renderables = screen.renderables;
        List<NarratableEntry> narratables = screen.narratables;

        AbstractWidget options = ButtonHelper.findButton("menu.options", screen);
        AbstractWidget openToLan = ButtonHelper.findButton("menu.multiplayerOptions.button", screen);
        AbstractWidget quitToTitle = ButtonHelper.findButton("menu.returnToMenu", screen);
        if (!Config.instance().moveButtonsOnPauseMenu || options == null || openToLan == null || quitToTitle == null) {
            // Just add the button bellow the FriendsList button in the corner.
            // We either didn't find the vanilla pause buttons, or moving these buttons was disabled in our config.
            Button openToFriends = Button.builder(buttonText, action)
                    .bounds(screen.width - 105, 25, 100, 20)
                    .build();
            screen.addRenderableWidget(openToFriends);
            return;
        }

        int left = options.getX();
        int fullWidth = Math.max(quitToTitle.getWidth(), options.getWidth());
        int halfWidth = openToLan.getWidth();
        int gap = Math.max(0, fullWidth - (halfWidth * 2));
        int buttonRowStep = quitToTitle.getY() - options.getY();
        if (buttonRowStep <= 0) {
            buttonRowStep = options.getHeight() + 4;
        }

        int connectRowY = quitToTitle.getY();

        // Make Options a full-width row.
        options.setX(left);
        options.setWidth(fullWidth);

        // Open To Friends and vanilla Open To LAN share the next row.
        Button openToFriends = Button.builder(buttonText, action)
                .bounds(left, connectRowY, halfWidth, openToLan.getHeight())
                .build();
        screen.addRenderableWidget(openToFriends);
        openToLan.setX(left + halfWidth + gap);
        openToLan.setY(connectRowY);

        // Move Save and Quit to Title down to make room for the new Connect/LAN row.
        quitToTitle.setX(left);
        quitToTitle.setWidth(fullWidth);
        quitToTitle.setY(quitToTitle.getY() + buttonRowStep);

        // Keep keyboard/controller narration order aligned with the visual row order.
        moveBefore(children, openToFriends, openToLan);
        moveBefore(renderables, openToFriends, (Renderable) openToLan);
        moveBefore(narratables, openToFriends, (NarratableEntry) openToLan);
    }

    private static <T> void moveBefore(List<T> list, T value, T before) {
        if (!list.remove(value)) {
            return;
        }
        int index = list.indexOf(before);
        if (index >= 0) {
            list.add(index, value);
        } else {
            list.add(value);
        }
    }
}
