package net.creeperhost.minetogethercommunity.chat;

import net.creeperhost.minetogether.lib.chat.ChatState;
import net.creeperhost.minetogether.lib.chat.MutedUserList;
import net.creeperhost.minetogether.lib.chat.irc.IrcChannel;
import net.creeperhost.minetogether.lib.chat.irc.IrcClient;
import net.creeperhost.minetogether.lib.chat.irc.IrcState;
import net.creeperhost.minetogether.lib.chat.profile.ProfileManager;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.config.Config;
import net.creeperhost.minetogethercommunity.config.LocalConfig;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticApiClient;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.creeperhost.minetogethercommunity.cosmetic.PlayerCosmeticCache;
import net.creeperhost.minetogethercommunity.gui.chat.PlayerIconElement;
import net.creeperhost.minetogethercommunity.util.ModPackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Locale;

public class MineTogetherChat {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Chat");

    public static ChatAuthImpl CHAT_AUTH;
    public static ChatState CHAT_STATE;
    private static boolean attached;
    private static Object profileListener;

    public static void init() {
        CHAT_AUTH = new ChatAuthImpl(Minecraft.getMinecraft());
        LOGGER.info("MineTogether chat auth hash {} using fingerprint {}.", mask(CHAT_AUTH.getHash()), mask(CHAT_AUTH.getSignature()));
        File muted = new File(new File(MineTogether.getGameDir(), "local/minetogether"), "mutedusers.json");
        CHAT_STATE = new ChatState(MineTogether.API, CHAT_AUTH, new MutedUserList(muted.toPath()), () -> ModPackInfo.getInfo().realName, false);
        CHAT_STATE.logChatToConsole = Config.instance().logChatToConsole || Config.instance().debugMode;
        if (Config.instance().debugMode) {
            System.setProperty("net.covers1624.pircbot.logging.info", "INFO");
            System.setProperty("net.covers1624.pircbot.logging.debug", "INFO");
            System.setProperty("net.covers1624.pircbot.logging.very_verbose", "true");
        }
        attachChatListeners();
        FriendChatNotifier.attach();
        markAccountFirstConnect();
        ChatStatistics.pollStats();
        InGameChatBridge.tick();
        if (LocalConfig.instance().chatEnabled) {
            enableChat();
        }
    }

    public static void enableChat() {
        if (CHAT_STATE != null) {
            CHAT_STATE.ircClient.start();
        }
    }

    public static void disableChat() {
        if (CHAT_STATE != null) {
            CHAT_STATE.ircClient.stop();
        }
        InGameChatBridge.reset();
    }

    public static void setTarget(ChatTarget target) {
        ChatTarget previous = getTarget();
        LocalConfig.instance().selectedTab = target;
        LocalConfig.save();
        if (previous != target) {
            InGameChatBridge.onTargetChanged(target);
        }
    }

    public static ChatTarget getTarget() {
        return LocalConfig.instance().chatEnabled ? LocalConfig.instance().selectedTab : ChatTarget.VANILLA;
    }

    public static boolean isConnected() {
        return CHAT_STATE != null && CHAT_STATE.ircClient.getState().isConnected();
    }

    public static boolean sendMessageToTarget(ChatTarget target, String text) {
        if (target == ChatTarget.PUBLIC) {
            return sendPublicMessage(text);
        }
        if (target == ChatTarget.GROUP) {
            return sendGroupMessage(text);
        }
        return false;
    }

    public static boolean sendPublicMessage(String text) {
        if (CHAT_STATE == null || text.trim().isEmpty()) return false;
        if (isBanned()) {
            localStatus("minetogether.gui.button.banned.info");
            return false;
        }
        if (CHAT_STATE.ircClient.getState() != IrcState.CONNECTED) {
            localStatus("minetogether.gui.chat.not_connected");
            return false;
        }
        IrcChannel channel = CHAT_STATE.ircClient.getPrimaryChannel();
        return sendToChannel(channel, text, "minetogether.gui.chat.not_connected");
    }

    public static boolean canSendPublicMessages() {
        return CHAT_STATE != null && CHAT_STATE.ircClient.getState() == IrcState.CONNECTED && !isBanned();
    }

    public static boolean sendGroupMessage(String text) {
        if (CHAT_STATE == null || text.trim().isEmpty()) return false;
        ProfileManager.PrivateGroup group = CHAT_STATE.profileManager.getPrivateGroup();
        if (group == null || group.channelName == null) {
            localStatus("minetogether.gui.chat.no_group");
            return false;
        }
        IrcChannel channel = CHAT_STATE.ircClient.getChannel(group.channelName);
        return sendToChannel(channel, text, "minetogether.gui.chat.group_not_connected");
    }

    private static boolean sendToChannel(IrcChannel channel, String text, String errorKey) {
        if (channel == null) {
            localStatus(errorKey);
            return false;
        }
        channel.sendMessage(text);
        return true;
    }

    public static void localStatus(String key, Object... args) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new net.minecraft.util.text.TextComponentTranslation(key, args));
        } else if (mc.ingameGUI != null) {
            mc.ingameGUI.getChatGUI().printChatMessage(new net.minecraft.util.text.TextComponentTranslation(key, args));
        }
    }

    public static String displayName(Profile profile) {
        return profile == null ? "" : profile.isFriend() && profile.hasFriendName() ? profile.getFriendName() : profile.getDisplayName();
    }

    public static Profile getOurProfile() {
        if (CHAT_STATE == null) return null;
        return CHAT_STATE.profileManager.getOwnProfile();
    }

    public static boolean isBanned() {
        try {
            Profile profile = getOurProfile();
            return profile != null && profile.isBanned();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isNewUser() {
        if (CHAT_STATE == null) return false;
        if (CHAT_STATE.profileManager.getOwnProfile().hasAccount()) {
            markAccountFirstConnect();
            return false;
        }
        return !LocalConfig.instance().firstConnect.contains(CHAT_AUTH.getHash().toLowerCase(Locale.ROOT));
    }

    public static void setNewUserResponded() {
        LocalConfig.instance().firstConnect.add(CHAT_AUTH.getHash().toLowerCase(Locale.ROOT));
        LocalConfig.save();
    }

    private static void attachChatListeners() {
        if (attached || CHAT_STATE == null) return;
        attached = true;
        CHAT_STATE.ircClient.addChannelListener(new IrcClient.ChannelListener() {
            @Override
            public void channelJoin(IrcChannel channel) {
                LOGGER.info("Joined MineTogether channel {}", channel.getName());
            }

            @Override
            public void channelLeave(IrcChannel channel) {
                LOGGER.info("Left MineTogether channel {}", channel.getName());
            }
        });
        profileListener = CHAT_STATE.profileManager.addListener(MineTogetherChat.class,
                new net.creeperhost.minetogether.lib.util.WeakListener<Class<MineTogetherChat>, ProfileManager.ProfileManagerEvent>() {
                    @Override
                    public void fire(Class<MineTogetherChat> owner, ProfileManager.ProfileManagerEvent event) {
                        handleProfileEvent(event);
                    }
                });
    }

    private static void handleProfileEvent(ProfileManager.ProfileManagerEvent event) {
        if (event == null || event.type == null) return;
        String eventName = event.type.name();
        if (isCosmeticsInvalidationEvent(eventName)) {
            handleCosmeticsInvalidation(event.data);
            return;
        }
        switch (event.type) {
            case PROFILE_EXPIRE:
                if (event.data instanceof Profile) {
                    Profile profile = (Profile) event.data;
                    if (profile.hasFullHash()) {
                        PlayerIconElement.invalidate(profile);
                        PlayerCosmeticCache.invalidateHash(profile.getFullHash());
                        Profile ownProfile = getOurProfile();
                        if (ownProfile == profile || (ownProfile != null && ownProfile.hasFullHash()
                                && ownProfile.getFullHash().equalsIgnoreCase(profile.getFullHash()))) {
                            CosmeticSelections.instance().clear();
                            CosmeticApiClient.fetchProfileAsync();
                        } else {
                            CosmeticApiClient.fetchProfileForHashAsync(profile.getFullHash());
                        }
                    }
                }
                break;
            default:
                break;
        }
    }

    private static boolean isCosmeticsInvalidationEvent(String eventName) {
        if (eventName == null) return false;
        String normalized = eventName.toUpperCase(Locale.ROOT);
        return normalized.contains("COSMETIC")
                && (normalized.contains("EXPIRE")
                || normalized.contains("INVALID")
                || normalized.contains("REFRESH")
                || normalized.contains("RELOAD"));
    }

    private static void handleCosmeticsInvalidation(Object data) {
        LOGGER.info("MineTogether cosmetic invalidation event received; clearing cached cosmetics and refetching profile.");
        if (data instanceof Profile) {
            PlayerIconElement.invalidate((Profile) data);
        }
        PlayerIconElement.invalidateAll();
        CosmeticDownloader.instance().invalidateAndRefetch();
        CosmeticSelections.instance().clear();
        PlayerCosmeticCache.clearAll();
        CosmeticApiClient.fetchProfileAsync();
    }

    private static void markAccountFirstConnect() {
        if (CHAT_AUTH == null || CHAT_STATE == null || CHAT_STATE.profileManager == null) return;
        Profile profile = CHAT_STATE.profileManager.getOwnProfile();
        if (profile == null || !profile.hasAccount()) return;
        String lowerHash = CHAT_AUTH.getHash().toLowerCase(Locale.ROOT);
        if (LocalConfig.instance().firstConnect.add(lowerHash)) {
            LocalConfig.save();
        }
    }

    private static String mask(String value) {
        if (value == null || value.length() < 12) return String.valueOf(value);
        return value.substring(0, 8) + "..." + value.substring(value.length() - 6);
    }
}
