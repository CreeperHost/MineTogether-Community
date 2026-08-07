package net.creeperhost.minetogethercommunity.chat;

import dev.architectury.hooks.client.screen.ScreenHooks;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.platform.Platform;
import net.covers1624.quack.net.httpapi.AbstractEngineRequest;
import net.covers1624.quack.net.httpapi.EngineRequest;
import net.covers1624.quack.net.httpapi.EngineResponse;
import net.covers1624.quack.net.httpapi.HeaderList;
import net.covers1624.quack.net.httpapi.HttpEngine;
import net.covers1624.quack.net.httpapi.WebBody;
import net.creeperhost.minetogether.lib.chat.ChatState;
import net.creeperhost.minetogether.lib.chat.ChatAuth;
import net.creeperhost.minetogether.lib.chat.MutedUserList;
import net.creeperhost.minetogether.lib.chat.irc.IrcChannel;
import net.creeperhost.minetogether.lib.chat.irc.IrcClient;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogether.lib.chat.profile.ProfileManager;
import net.creeperhost.minetogether.session.JWebToken;
import net.creeperhost.minetogethercommunity.Constants;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.chat.gui.ChatScreenTickHandler;
import net.creeperhost.minetogethercommunity.chat.gui.FriendChatGui;
import net.creeperhost.minetogethercommunity.chat.gui.PublicChatGui;
import net.creeperhost.minetogethercommunity.chat.ingame.MTChatComponent;
import net.creeperhost.minetogethercommunity.config.Config;
import net.creeperhost.minetogethercommunity.config.LocalConfig;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticApiClient;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteNetworking;
import net.creeperhost.minetogethercommunity.gui.SettingGui;
import net.creeperhost.minetogethercommunity.polylib.gui.IconButton;
import net.creeperhost.minetogethercommunity.util.ModPackInfo;
import net.creeperhost.polylib.client.modulargui.ModularGuiScreen;
import net.creeperhost.polylib.client.toast.SimpleToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * @author covers1624
 */
public class MineTogetherChat {

    private static final Logger LOGGER = LogManager.getLogger();

    public static final ChatAuthImpl CHAT_AUTH = new ChatAuthImpl(Minecraft.getInstance());
    private static final MutedUserList MUTED_USER_LIST = new MutedUserList(
            Platform.getGameFolder().resolve("local/minetogether/mutedusers.json")
    );

    public static ChatState CHAT_STATE = new ChatState(MineTogether.API, CHAT_AUTH, MUTED_USER_LIST, () -> ModPackInfo.getInfo().realName, false);

    public static ChatComponent vanillaChat;
    public static MTChatComponent publicChat;
    public static MTChatComponent groupChat;
    private static boolean hasHitLoadingScreen = false;
    private static boolean minecraftChatAllowed = true;

    /** Replaces external chat discovery for the isolated offline runtime test. */
    public static void configureLocalChatForTesting(int port, String hash, UUID uuid, Path mutedUsersFile) {
        if (!Boolean.getBoolean("minetogether.ci.localChat")) {
            throw new IllegalStateException("The local chat override is only available to the CI runtime probe");
        }
        ChatAuth auth = new ChatAuth() {
            @Override public String getSignature() { return "ci-offline-signature"; }
            @Override public UUID getUUID() { return uuid; }
            @Override public String getHash() { return hash; }
            @Override public void resetSessionToken() { }
            @Override public CompletableFuture<@Nullable JWebToken> getSessionTokenAsync() {
                return CompletableFuture.completedFuture(null);
            }
        };
        String serverResponse = "{\"status\":\"success\",\"channel\":\"#minetogether-ci\","
                + "\"server\":{\"address\":\"127.0.0.1\",\"port\":" + port + ",\"ssl\":false}}";
        HttpEngine engine = () -> new LocalChatRequest(serverResponse);
        CHAT_STATE = new ChatState(
                net.creeperhost.minetogether.lib.web.ApiClient.builder()
                        .httpEngine(engine)
                        .addUserAgentSegment("MineTogether-CI")
                        .build(),
                auth,
                new MutedUserList(mutedUsersFile),
                () -> "MineTogether CI",
                true
        );
    }

    private static final class LocalChatRequest extends AbstractEngineRequest {
        private static final String ERROR_RESPONSE =
                "{\"status\":\"error\",\"message\":\"Profile request already ongoing (CI mock)\"}";
        private final String serverResponse;

        private LocalChatRequest(String serverResponse) {
            this.serverResponse = serverResponse;
        }

        @Override
        public EngineRequest method(String method, @Nullable WebBody body) {
            assertState();
            return this;
        }

        @Override
        public EngineResponse execute() {
            assertState();
            executed = true;
            String json = getUrl().endsWith("/minetogether/chatserver") ? serverResponse : ERROR_RESPONSE;
            WebBody body = WebBody.string(json, "application/json; charset=utf-8");
            return new EngineResponse() {
                private final HeaderList headers = new HeaderList();
                @Override public EngineRequest request() { return LocalChatRequest.this; }
                @Override public int statusCode() { return 200; }
                @Override public String message() { return "OK"; }
                @Override public HeaderList headers() { return headers; }
                @Override public WebBody body() { return body; }
                @Override public void close() throws IOException { }
            };
        }
    }

    public static void init() {
        CHAT_STATE.ircClient.addCTCPListener((user, request) ->
                user != null && EmoteNetworking.handleCtcp(user.getProfile(), request)
        );
        CHAT_STATE.logChatToConsole = Config.instance().logChatToConsole | Config.instance().debugMode;

        if (Config.instance().debugMode) {
            System.setProperty("net.covers1624.pircbot.logging.info", "INFO");
            System.setProperty("net.covers1624.pircbot.logging.debug", "INFO");
            System.setProperty("net.covers1624.pircbot.logging.very_verbose", "true");
        }

        ClientTickEvent.CLIENT_POST.register(MineTogetherChat::refreshMinecraftChatAvailability);
        ClientTickEvent.CLIENT_POST.register(mc -> {
            if (mc.screen instanceof ChatScreenTickHandler handler) {
                handler.minetogethercommunity$tickChatState();
            }
        });
        ChatStatistics.pollStats();
    }

    public static void initChat(Gui gui) {
        Minecraft mc = Minecraft.getInstance();
        vanillaChat = gui.chat;
        publicChat = new MTChatComponent(ChatTarget.PUBLIC, mc);
        groupChat = new MTChatComponent(ChatTarget.GROUP, mc);
        refreshMinecraftChatAvailability(mc);
        if (isChatEnabled()) {
            CHAT_STATE.ircClient.start();
        }
        CHAT_STATE.ircClient.addChannelListener(new IrcClient.ChannelListener() {
            @Override
            public void channelJoin(IrcChannel channel) {
                if (CHAT_STATE.ircClient.getPrimaryChannel() == null) return;
                if (channel == CHAT_STATE.ircClient.getPrimaryChannel()) {
                    publicChat.attach(channel);

                    Screen screen = Minecraft.getInstance().screen;

                    // If we have the ChatScreen open. Attach to main chat.
                    if (screen instanceof ModularGuiScreen mgui && mgui.getModularGui().getProvider() instanceof PublicChatGui chat) {
                        chat.chatMonitor.attach(channel);
                    }
                }
                ProfileManager.PrivateGroup group = CHAT_STATE.profileManager.getPrivateGroup();
                if (group != null && group.channelName.equals(channel.getName())) {
                    groupChat.attach(channel);
                }
            }

            @Override
            public void channelLeave(IrcChannel channel) {

            }
        });

        CHAT_STATE.profileManager.addListener(mc, (m, e) -> m.submit(() -> {
            if (e.type == ProfileManager.EventType.FRIEND_REQUEST_ADDED) {
                ProfileManager.FriendRequest fr = (ProfileManager.FriendRequest) e.data;
                simpleToast(Component.translatable("minetogether:toast.fiend_request_received", displayName(fr.user)));
            } else if (e.type == ProfileManager.EventType.FRIEND_REQUEST_ACCEPTED) {
                Profile fr = (Profile) e.data;
                simpleToast(Component.translatable("minetogether:toast.fiend_request_accepted", displayName(fr)));
            } else if (e.type == ProfileManager.EventType.FRIEND_ONLINE && LocalConfig.instance().friendNotifications) {
                Profile fr = (Profile) e.data;
                simpleToast(Component.translatable("minetogether:toast.user_online", displayName(fr)));
            } else if (e.type == ProfileManager.EventType.FRIEND_OFFLINE && LocalConfig.instance().friendNotifications) {
                Profile fr = (Profile) e.data;
                simpleToast(Component.translatable("minetogether:toast.user_offline", displayName(fr)));
            } else if (e.type == ProfileManager.EventType.GROUP_INVITE_RECEIVED) {
                ProfileManager.PrivateGroup group = (ProfileManager.PrivateGroup) e.data;
                if (group != null && group.ownerHash != null) {
                    Profile sender = CHAT_STATE.profileManager.lookupProfile(group.ownerHash);
                    simpleToast(Component.translatable("minetogether:toast.group_invite_received", displayName(sender)));
                }
            } else if (e.type == ProfileManager.EventType.LEFT_GROUP) {
                if (getTarget() == ChatTarget.GROUP) {
                    setTarget(ChatTarget.VANILLA);//Switch to vanilla rather than public to avoid situations where a user starts sending private messages without realizing they have left the group.
                }
                simpleToast(Component.translatable("minetogether:toast.left_group"), Component.translatable("minetogether:toast.left_group." + e.data));
            } else if (e.type == ProfileManager.EventType.PROFILE_EXPIRE) {
                if (Minecraft.getInstance().player != null && e.data instanceof Profile pr) {
                    if (pr.hasFullHash()) {
                        CosmeticApiClient.fetchProfileForHashAsync(pr.getFullHash());
                    }
                }
            }
        }));

        // If the user has an account. Set firstConnect just incase.
        String lowerHash = CHAT_AUTH.getHash().toLowerCase(Locale.ROOT);
        if (!LocalConfig.instance().firstConnect.contains(lowerHash) && CHAT_STATE.profileManager.getOwnProfile().hasAccount()) {
            LocalConfig.instance().firstConnect.add(lowerHash);
            LocalConfig.save();
        }
    }

    public static void simpleToast(Component toastText) {
        addToast(new SimpleToast(
                toastText,
                Component.empty(),
                Constants.MINETOGETHER_LOGO_SOLID
        ));
    }

    public static void simpleToast(Component toastTitle, Component toastText) {
        addToast(new SimpleToast(
                toastTitle,
                toastText,
                Constants.MINETOGETHER_LOGO_SOLID
        ));
    }

    private static void addToast(Toast toast) {
        if (hasHitLoadingScreen) {
            Minecraft.getInstance().getToasts().addToast(toast);
        } else {
            // YEET, too bad.
        }
    }

    public static Profile getOurProfile() {
        return CHAT_STATE.profileManager.getOwnProfile();
    }

    public static void onScreenPostInit(Screen screen) {
        if (screen instanceof TitleScreen) {
            if (!hasHitLoadingScreen) {
                hasHitLoadingScreen = true;
            }
            if (LocalConfig.instance().mainMenuButtons) {
                addMenuButtons(screen);
            }
        } else if (screen instanceof PauseScreen) {
            if (Config.instance().pauseScreenButtons) {
                addMenuButtons(screen);
            }
        }
    }

    private static void addMenuButtons(Screen screen) {
        int buttonPos = 4;
        IconButton settings = new IconButton(screen.width - (buttonPos += 21), 5, 3, Constants.WIDGETS_SHEET, e -> Minecraft.getInstance().setScreen(new SettingGui.Screen(screen)));
        settings.setTooltip(Tooltip.create(Component.translatable("minetogether:gui.button.settings.info")));
        ScreenHooks.addRenderableWidget(screen, settings);

        if (isChatEnabled()) {
            IconButton friendChat = new IconButton(screen.width - (buttonPos += 21), 5, 7, Constants.WIDGETS_SHEET, e -> Minecraft.getInstance().setScreen(new FriendChatGui.Screen(screen)));
            friendChat.setTooltip(Tooltip.create(Component.translatable("minetogether:gui.button.friends.info")));
            ScreenHooks.addRenderableWidget(screen, friendChat);

            IconButton publicChat = new IconButton(screen.width - (buttonPos += 21), 5, 1, Constants.WIDGETS_SHEET, e -> Minecraft.getInstance().setScreen(new PublicChatGui.Screen(screen)));
            publicChat.setTooltip(Tooltip.create(Component.translatable("minetogether:gui.button.global_chat.info")));
            ScreenHooks.addRenderableWidget(screen, publicChat);
        }
    }

    public static boolean isNewUser() {
        if (MineTogetherChat.getOurProfile().hasAccount()) return false;

        return !LocalConfig.instance().firstConnect.contains(CHAT_AUTH.getHash().toLowerCase(Locale.ROOT));
    }

    public static void setNewUserResponded() {
        LocalConfig.instance().firstConnect.add(CHAT_AUTH.getHash().toLowerCase(Locale.ROOT));
        LocalConfig.save();
    }

    public static void disableChat() {
        CHAT_STATE.ircClient.stop();
    }

    public static void enableChat() {
        if (isChatEnabled()) {
            CHAT_STATE.ircClient.start();
        }
    }

    public static void setTarget(ChatTarget target) {
        LocalConfig.instance().selectedTab = target;
        LocalConfig.save();
    }

    public static ChatTarget getTarget() {
        return isChatEnabled() ? LocalConfig.instance().selectedTab : ChatTarget.VANILLA;
    }

    /** Whether MineTogether chat is both enabled by the player and permitted by Minecraft. */
    public static boolean isChatEnabled() {
        return LocalConfig.instance().chatEnabled && isMinecraftChatAllowed(Minecraft.getInstance());
    }

    private static boolean isMinecraftChatAllowed(Minecraft mc) {
        Minecraft.ChatStatus status = mc.getChatStatus();
        return mc.options.chatVisibility().get() == ChatVisiblity.FULL
                && status != Minecraft.ChatStatus.DISABLED_BY_PROFILE
                && status != Minecraft.ChatStatus.DISABLED_BY_LAUNCHER
                && status != Minecraft.ChatStatus.DISABLED_BY_OPTIONS;
    }

    private static void refreshMinecraftChatAvailability(Minecraft mc) {
        boolean allowed = isMinecraftChatAllowed(mc);
        if (minecraftChatAllowed == allowed) return;

        minecraftChatAllowed = allowed;
        if (!allowed) {
            CHAT_STATE.ircClient.stop();
        } else if (LocalConfig.instance().chatEnabled) {
            CHAT_STATE.ircClient.start();
        }
    }

    public static String displayName(@Nullable Profile profile) {
        return profile == null ? "" : profile.isFriend() && profile.hasFriendName() ? profile.getFriendName() : profile.getDisplayName();
    }
}
