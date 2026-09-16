package net.creeperhost.minetogethercommunity.connect;

import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.config.Config;
import net.creeperhost.minetogethercommunity.connect.gui.ConnectPackSelectionScreen;
import net.creeperhost.minetogethercommunity.connect.gui.GuiShareToFriends;
import net.creeperhost.polylib.client.screen.ButtonHelper;
import net.creeperhost.polylib.event.events.client.PolyScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Comparator;

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

        if (!(screen instanceof PauseScreen pauseScreen) || !pauseScreen.showsPauseMenu()) return;

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

        AbstractWidget reportBugs = ButtonHelper.findButton("menu.reportBugs", screen);
        if (!Config.instance().moveButtonsOnPauseMenu || reportBugs == null || reportBugs.getWidth() != reportBugs.getHeight()) {
            // Preserve the corner placement when opted out or using a custom pause menu.
            Button openToFriends = Button.builder(buttonText, action)
                    .bounds(screen.width - 105, 25, 100, 20)
                    .build();
            screen.addRenderableWidget(openToFriends);
            return;
        }

        // Include other mods' square buttons in the existing row when recentering it.
        List<AbstractWidget> iconButtons = children.stream()
                .filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast)
                .filter(widget -> widget.getY() == reportBugs.getY()
                        && widget.getHeight() == reportBugs.getHeight()
                        && widget.getWidth() == widget.getHeight())
                .sorted(Comparator.comparingInt(AbstractWidget::getX))
                .toList();
        AbstractWidget lastIcon = iconButtons.getLast();
        SpriteIconButton openToFriends = SpriteIconButton.builder(buttonText, action, true)
                .size(reportBugs.getWidth(), reportBugs.getHeight())
                .sprite(Identifier.fromNamespaceAndPath(MineTogether.MOD_ID,
                        isConnectPublished ? "pause_menu/close_to_friends" : "pause_menu/open_to_friends"), 16, 16)
                .withTootip()
                .build();
        int gap = 4;
        int rowWidth = iconButtons.stream().mapToInt(AbstractWidget::getWidth).sum()
                + openToFriends.getWidth() + gap * iconButtons.size();
        int nextX = (screen.width - rowWidth) / 2;
        for (AbstractWidget icon : iconButtons) {
            icon.setX(nextX);
            nextX += icon.getWidth() + gap;
        }
        openToFriends.setX(nextX);
        openToFriends.setY(reportBugs.getY());
        screen.addRenderableWidget(openToFriends);

        // Keep keyboard/controller narration order aligned with the visual row order.
        moveAfter(children, openToFriends, lastIcon);
        moveAfter(renderables, openToFriends, (Renderable) lastIcon);
        moveAfter(narratables, openToFriends, (NarratableEntry) lastIcon);
    }

    private static <T> void moveAfter(List<T> list, T value, T after) {
        if (!list.remove(value)) {
            return;
        }
        int index = list.indexOf(after);
        if (index >= 0) {
            list.add(index + 1, value);
        } else {
            list.add(value);
        }
    }
}
