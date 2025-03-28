package net.creeperhost.minetogethercommunity.neoforge.compat.pausemenu;

import dev.ftb.mods.pmapi.api.PauseMenuApi;
import dev.ftb.mods.pmapi.api.menu.*;
import dev.ftb.mods.pmapi.api.menu.PauseItemProvider;
import net.creeperhost.minetogethercommunity.Constants;
import net.creeperhost.minetogethercommunity.chat.gui.FriendChatGui;
import net.creeperhost.minetogethercommunity.chat.gui.PublicChatGui;
import net.creeperhost.minetogethercommunity.config.LocalConfig;
import net.creeperhost.minetogethercommunity.connect.ConnectHandler;
import net.creeperhost.minetogethercommunity.connect.gui.GuiShareToFriends;
import net.creeperhost.minetogethercommunity.gui.SettingGui;
import net.creeperhost.minetogethercommunity.polylib.gui.IconButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Created by brandon3055 on 23/02/2025
 */
public class PauseMenuIntegration {


    public static void init() {
        PauseMenuApi.get().registerPauseItem(MenuLocation.TOP_RIGHT, new MenuButtons());
    }

    public static class MenuButtons implements PauseItemProvider {

        @Override
        public @Nullable ScreenWidgetCollection init(MenuLocation target, ScreenHolder screen, int x, int y) {
            IntegratedServer integratedServer = Minecraft.getInstance().getSingleplayerServer();
            var collection = ScreenWidgetCollection.create();

            int xOffset = 0;
            if (integratedServer != null) {
                boolean isConnectPublished = ConnectHandler.isPublished();
                Component buttonText = isConnectPublished ? Component.translatable("minetogether.connect.close") : Component.translatable("minetogether.connect.open");
                Button.OnPress action = button -> {
                    if (isConnectPublished) {
                        ConnectHandler.unPublish();
                        Minecraft.getInstance().setScreen(new PauseScreen(true));
                    } else {
                        Minecraft.getInstance().setScreen(new GuiShareToFriends.Screen(screen.unsafeScreenAccess()));
                    }
                };

                xOffset += 98;
                collection.addRenderableWidget(Button.builder(buttonText, action).bounds(x - xOffset, y, 98, 20).build());
            }

            xOffset += 22;

            var newSettingsBtn = new IconButton(x - xOffset, y, 3, Constants.WIDGETS_SHEET, e -> Minecraft.getInstance().setScreen(new SettingGui.Screen(screen.unsafeScreenAccess())));
            newSettingsBtn.setTooltip(Tooltip.create(Component.translatable("minetogether:gui.button.settings.info")));
            collection.addRenderableWidget(newSettingsBtn);

            var newFriendChatBtn = new IconButton(x - xOffset - 22, y, 7, Constants.WIDGETS_SHEET, e -> Minecraft.getInstance().setScreen(new FriendChatGui.Screen(screen.unsafeScreenAccess())));
            newFriendChatBtn.setTooltip(Tooltip.create(Component.translatable("minetogether:gui.button.friends.info")));
            collection.addRenderableWidget(newFriendChatBtn);

            if (LocalConfig.instance().chatEnabled) {
                var newPublicChatBtn = new IconButton(x - xOffset - 44, y, 1, Constants.WIDGETS_SHEET, e -> Minecraft.getInstance().setScreen(new PublicChatGui.Screen(screen.unsafeScreenAccess())));
                newPublicChatBtn.setTooltip(Tooltip.create(Component.translatable("minetogether:gui.button.global_chat.info")));
                collection.addRenderableWidget(newPublicChatBtn);
            }

            return collection;
        }
    }

}
