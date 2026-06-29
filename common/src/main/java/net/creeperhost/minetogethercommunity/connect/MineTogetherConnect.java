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
                Minecraft.getInstance().setScreen(new PauseScreen(true));
            } else {
                Minecraft.getInstance().setScreen(new GuiShareToFriends.Screen(screen));
            }
        };

        @SuppressWarnings ("unchecked")
        List<GuiEventListener> children = (List<GuiEventListener>) screen.children();
        List<Renderable> renderables = screen.renderables;
        List<NarratableEntry> narratables = screen.narratables;

        AbstractWidget feedBack = ButtonHelper.findButton("menu.sendFeedback", screen);
        AbstractWidget options = ButtonHelper.findButton("menu.options", screen);
        if (!Config.instance().moveButtonsOnPauseMenu || feedBack == null || options == null) {
            // Just add the button bellow the FriendsList button in the corner.
            // We either didn't find the Feedback and Options buttons, or moving these buttons was disabled in our config.
            Button openToFriends = Button.builder(buttonText, action)
                    .bounds(screen.width - 105, 25, 100, 20)
                    .build();
            screen.addRenderableWidget(openToFriends);
            return;
        }

        // Open To Friends button goes where the options button was.
        Button openToFriends = Button.builder(buttonText, action)
                .bounds(options.getX(), options.getY(), 98, 20)
                .build();
        screen.addRenderableWidget(openToFriends);

        // Move the options button to where the feedback button was.
        options.setY(feedBack.getY());
        options.setX(feedBack.getX());

        // Again, we have to juggle indexes because of Mod Menu...
        children.remove(options);
        renderables.remove(options);
        narratables.remove(options);
        children.set(children.indexOf(feedBack), options);
        renderables.set(renderables.indexOf(feedBack), options);
        narratables.set(narratables.indexOf(feedBack), options);
    }
}
