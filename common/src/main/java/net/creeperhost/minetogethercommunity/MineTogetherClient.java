package net.creeperhost.minetogethercommunity;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.creeperhost.minetogether.session.MineTogetherSession;
import net.creeperhost.minetogethercommunity.activity.ActivityTelemetry;
import net.creeperhost.minetogethercommunity.chat.FriendChatNotifier;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.chat.gui.ChatScreenInjection;
import net.creeperhost.minetogethercommunity.compat.Integration;
import net.creeperhost.minetogethercommunity.compat.MTPartners;
import net.creeperhost.minetogethercommunity.compat.ftbquests.FTBQuestsCompat;
import net.creeperhost.minetogethercommunity.compat.quests.BountifulCompat;
import net.creeperhost.minetogethercommunity.compat.quests.HQMCompat;
import net.creeperhost.minetogethercommunity.compat.quests.HeraclesCompat;
import net.creeperhost.minetogethercommunity.config.Config;
import net.creeperhost.minetogethercommunity.connect.MineTogetherConnect;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticApiClient;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.creeperhost.minetogethercommunity.cosmetic.PlayerCosmeticCache;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import net.creeperhost.minetogethercommunity.gui.SettingGui;
import net.creeperhost.minetogethercommunity.orderform.OrderGui;
import net.creeperhost.minetogethercommunity.util.MTSessionProvider;
import net.creeperhost.polylib.client.modulargui.ModularGui;
import net.creeperhost.polylib.client.modulargui.ModularGuiInjector;
import net.creeperhost.polylib.client.screen.ButtonHelper;
import net.creeperhost.polylib.event.events.client.PolyClientEntityEvents;
import net.creeperhost.polylib.event.events.client.PolyClientLifecycleEvents;
import net.creeperhost.polylib.event.events.client.PolyClientPlayerEvents;
import net.creeperhost.polylib.event.events.client.PolyScreenEvents;
import net.minecraft.util.Util;
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
import net.minecraft.client.player.AbstractClientPlayer;
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
    private static boolean started;

    public static void init() {
        LOGGER.info("Initializing MineTogetherCommunityClient!");

        MineTogetherSession.getDefault().setProvider(new MTSessionProvider());
        MineTogetherSession.getDefault().onTokenRefreshed(token -> {
            MineTogether.AUTH.setHeader("Authorization", "Bearer " + token);
            ActivityTelemetry.authChanged(token);
        });
        Keybindings.init();

        ModularGuiInjector.registerInjection(e -> e instanceof ChatScreen, e -> new ChatScreenInjection());

        PolyClientLifecycleEvents.CLIENT_STARTED.register(MineTogetherClient::onClientStarted);
        PolyScreenEvents.SCREEN_OPENED.register(MineTogetherClient::onScreenOpen);

        // Kick off cosmetic catalog download and profile fetch as soon as the player enters a world,
        // so the data is ready (or already cached) by the time they open the cosmetics GUI.
        PolyClientPlayerEvents.CLIENT_LOGIN.register(player -> {
            CosmeticDownloader.instance().startCatalogFetch();
            CosmeticApiClient.fetchProfileAsync();
        });

        // Clear the in-memory selections when the player leaves so stale data doesn't linger
        // if a different account logs in during the same game session.
        PolyClientPlayerEvents.LOGOUT.register(player -> {
            CosmeticSelections cs = CosmeticSelections.instance();
            cs.selectedHatId = "";
            cs.selectedCapeId = "";
            cs.selectedTailId = "";
            cs.selectedWingId = "";
            // Also drop all cached remote-player profiles so they're re-fetched on next join
            PlayerCosmeticCache.clearAll();
            EmotePlayer.clearAll();
        });

        // Fetch remote player cosmetics once their client-side player entity exists.
        PolyClientEntityEvents.CLIENT_ENTITY_LOAD.register((entity, level) -> {
            if (!(entity instanceof AbstractClientPlayer player)) return;
            if (player == Minecraft.getInstance().player) return;
            // markFetching returns false if a fetch is already in progress for this UUID,
            // preventing concurrent duplicate requests.
            if (PlayerCosmeticCache.markFetching(player.getUUID())) {
                CosmeticApiClient.fetchProfileForPlayerAsync(player.getUUID());
            }
        });
    }

    private static void onClientStarted(Minecraft client) {
        if (started) {
            return;
        }
        started = true;

        // Trigger session validation and set auth header after Minecraft has a user.
        MineTogetherSession.getDefault().getTokenAsync();

        MineTogetherChat.init();
        MineTogetherConnect.init();
        FriendChatNotifier.init();
        ActivityTelemetry.init();
        Integration.runOptional("ftbquests", () -> () -> {
            FTBQuestsCompat.registerFabricEvents();
            FTBQuestsCompat.registerNeoForgeEvents();
        });
        Integration.runOptional("bountiful", () -> BountifulCompat::register);
        Integration.runOptional("hardcorequesting", () -> HQMCompat::register);
        Integration.runOptional("heracles", () -> HeraclesCompat::register);
    }

    public static <S> void registerClientCommands(CommandDispatcher<S> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal("minetogether_settings")
                .executes(c -> {
                    Minecraft.getInstance().setScreen(new SettingGui.Screen(null));
                    return 0;
                })
        );
    }

    public static void openOrderUI(ModularGui gui) {
        if (MineTogetherPlatform.isModLoaded("minetogetherpartners")) {
            LOGGER.info("minetogetherpartners loaded, Using minetogetherpartners order form");
            MTPartners.openOrderUI(gui);
            return;
        }
        LOGGER.info("using minetogethercommunity order form");
        gui.mc().setScreen(new OrderGui.Screen(gui.getScreen(), true));
    }

    private static void onScreenOpen(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (screen instanceof PauseScreen) {
            @SuppressWarnings ("unchecked")
            List<GuiEventListener> children = (List<GuiEventListener>) screen.children();
            List<Renderable> renderables = screen.renderables;
            List<NarratableEntry> narratables = screen.narratables;

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
