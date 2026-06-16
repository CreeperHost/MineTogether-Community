package net.creeperhost.minetogethercommunity;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.hooks.client.screen.ScreenAccess;
import dev.architectury.platform.Platform;
import net.creeperhost.minetogether.session.MineTogetherSession;
import net.creeperhost.minetogethercommunity.chat.FriendChatNotifier;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.chat.gui.ChatScreenInjection;
import net.creeperhost.minetogethercommunity.compat.MTPartners;
import net.creeperhost.minetogethercommunity.config.Config;
import net.creeperhost.minetogethercommunity.connect.MineTogetherConnect;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticApiClient;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.creeperhost.minetogethercommunity.gui.SettingGui;
import net.creeperhost.minetogethercommunity.orderform.OrderGui;
import net.creeperhost.minetogethercommunity.util.MTSessionProvider;
import net.creeperhost.polylib.client.modulargui.ModularGui;
import net.creeperhost.polylib.client.modulargui.ModularGuiInjector;
import net.creeperhost.polylib.client.screen.ButtonHelper;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Initialize on a client.
 * <p>
 * Created by covers1624 on 20/6/22.
 */
public class MineTogetherClient {

    private static final Logger LOGGER = LogManager.getLogger();

    public static void init() {
        LOGGER.info("Initializing MineTogetherCommunityClient!");

        MineTogetherSession.getDefault().setProvider(new MTSessionProvider());
        MineTogetherSession.getDefault().onTokenRefreshed(token -> {
            MineTogether.AUTH.setHeader("Authorization", "Bearer " + token);
        });
        // Trigger session validation and set auth header.
        MineTogetherSession.getDefault().getTokenAsync();

        MineTogetherChat.init();
        MineTogetherConnect.init();
        FriendChatNotifier.init();
        Keybindings.init();

        ModularGuiInjector.registerInjection(e -> e instanceof ChatScreen, e -> new ChatScreenInjection());

        ClientGuiEvent.INIT_POST.register(MineTogetherClient::onScreenOpen);
        ClientCommandRegistrationEvent.EVENT.register(MineTogetherClient::registerClientCommands);

        // Kick off cosmetic catalog download and profile fetch as soon as the player enters a world,
        // so the data is ready (or already cached) by the time they open the cosmetics GUI.
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> {
            CosmeticDownloader.instance().startDownload();
            CosmeticApiClient.fetchProfileAsync();
        });

        // Clear the in-memory selections when the player leaves so stale data doesn't linger
        // if a different account logs in during the same game session.
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            CosmeticSelections cs = CosmeticSelections.instance();
            cs.selectedHatId = "";
            cs.selectedCapeId = "";
        });
    }

    private static void registerClientCommands(CommandDispatcher<ClientCommandRegistrationEvent.ClientCommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(LiteralArgumentBuilder.<ClientCommandRegistrationEvent.ClientCommandSourceStack>literal("minetogether_settings")
                .executes(c -> {
                    Minecraft.getInstance().setScreen(new SettingGui.Screen(null));
                    return 0;
                })
        );
    }

    public static void openOrderUI(ModularGui gui) {
        if (Platform.isModLoaded("minetogetherpartners")) {
            LOGGER.info("minetogetherpartners loaded, Using minetogetherpartners order form");
            MTPartners.openOrderUI(gui);
            return;
        }
        LOGGER.info("using minetogethercommunity order form");
        gui.mc().setScreen(new OrderGui.Screen(gui.getScreen(), true));
    }

    private static void onScreenOpen(Screen screen, ScreenAccess screenAccess) {
        if (screen instanceof PauseScreen) {
            @SuppressWarnings ("unchecked")
            List<GuiEventListener> children = (List<GuiEventListener>) screen.children();
            List<Renderable> renderables = screenAccess.getRenderables();
            List<NarratableEntry> narratables = screenAccess.getNarratables();

            // Replace bugs button with our own button.
            AbstractWidget bugs = ButtonHelper.findButton("menu.reportBugs", screen);
            if (bugs != null && Config.instance().issueTrackerUrl != null) {
                Button ourBugsButton = Button.builder(Component.translatable("menu.reportBugs"), (button) -> {
                            String s = Config.instance().issueTrackerUrl;
                            Minecraft.getInstance().setScreen(new ConfirmLinkScreen((p_213069_2_) -> {
                                if (p_213069_2_) {
                                    Util.getPlatform().openUri(s);
                                }

                                Minecraft.getInstance().setScreen(screen);
                            }, s, true));
                        })
                        .bounds(bugs.getX(), bugs.getY(), bugs.getWidth(), bugs.getHeight())
                        .build();
                // We have to keep these indexes the same and remove the old button due to how Mod Menu works...
                children.set(children.indexOf(bugs), ourBugsButton);
                renderables.set(renderables.indexOf(bugs), ourBugsButton);
                narratables.set(narratables.indexOf(bugs), ourBugsButton);
                bugs = ourBugsButton;
            }
        }
    }
}
