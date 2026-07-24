package net.creeperhost.minetogethercommunity.chat;

import dev.architectury.hooks.client.screen.ScreenHooks;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.platform.Platform;
import net.creeperhost.minetogether.lib.chat.ChatState;
import net.creeperhost.minetogether.lib.chat.MutedUserList;
import net.creeperhost.minetogether.lib.chat.irc.IrcChannel;
import net.creeperhost.minetogether.lib.chat.irc.IrcClient;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogether.lib.chat.profile.ProfileManager;
import net.creeperhost.minetogethercommunity.Constants;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.chat.gui.FriendChatGui;
import net.creeperhost.minetogethercommunity.chat.gui.PublicChatGui;
import net.creeperhost.minetogethercommunity.chat.ingame.MTChatComponent;
import net.creeperhost.minetogethercommunity.config.Config;
import net.creeperhost.minetogethercommunity.config.LocalConfig;
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

import java.util.Locale;

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

    public static void init() {
        CHAT_STATE.logChatToConsole = Config.instance().logChatToConsole | Config.instance().debugMode;

        if (Config.instance().debugMode) {
            System.setProperty("net.covers1624.pircbot.logging.info", "INFO");
            System.setProperty("net.covers1624.pircbot.logging.debug", "INFO");
            System.setProperty("net.covers1624.pircbot.logging.very_verbose", "true");
        }

        ClientTickEvent.CLIENT_POST.register(MineTogetherChat::refreshMinecraftChatAvailability);
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
