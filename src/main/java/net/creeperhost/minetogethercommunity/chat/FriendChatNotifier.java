package net.creeperhost.minetogethercommunity.chat;

import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogether.lib.chat.profile.ProfileManager;
import net.creeperhost.minetogether.lib.chat.irc.IrcUser;
import net.creeperhost.minetogethercommunity.config.LocalConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class FriendChatNotifier {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Chat");
    private static final Map<Profile, Integer> LAST_MESSAGE_COUNT = new HashMap<>();
    private static final Map<Profile, Integer> UNREAD_MESSAGE_COUNT = new HashMap<>();
    private static Object listener;
    private static Profile activeChat;
    private static int tick;

    private FriendChatNotifier() {
    }

    public static void attach() {
        if (listener != null || MineTogetherChat.CHAT_STATE == null) return;
        listener = MineTogetherChat.CHAT_STATE.profileManager.addListener(FriendChatNotifier.class,
                new net.creeperhost.minetogether.lib.util.WeakListener<Class<FriendChatNotifier>, ProfileManager.ProfileManagerEvent>() {
                    @Override
                    public void fire(Class<FriendChatNotifier> owner, ProfileManager.ProfileManagerEvent event) {
                        handle(event);
                    }
                });
    }

    private static void handle(final ProfileManager.ProfileManagerEvent event) {
        if (event == null || event.type == null) return;
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                switch (event.type) {
                    case FRIEND_REQUEST_ADDED:
                        ProfileManager.FriendRequest request = (ProfileManager.FriendRequest) event.data;
                        notifyEvent("minetogether.toast.friend_request_received", MineTogetherChat.displayName(request.user));
                        break;
                    case FRIEND_REQUEST_ACCEPTED:
                        notifyEvent("minetogether.toast.friend_request_accepted", MineTogetherChat.displayName((Profile) event.data));
                        break;
                    case FRIEND_ONLINE:
                        if (LocalConfig.instance().friendNotifications) {
                            notifyEvent("minetogether.toast.user_online", MineTogetherChat.displayName((Profile) event.data));
                        }
                        break;
                    case FRIEND_OFFLINE:
                        if (LocalConfig.instance().friendNotifications) {
                            notifyEvent("minetogether.toast.user_offline", MineTogetherChat.displayName((Profile) event.data));
                        }
                        break;
                    case GROUP_INVITE_RECEIVED:
                        ProfileManager.PrivateGroup group = (ProfileManager.PrivateGroup) event.data;
                        Profile sender = group == null || group.ownerHash == null || MineTogetherChat.CHAT_STATE == null
                                ? null
                                : MineTogetherChat.CHAT_STATE.profileManager.lookupProfile(group.ownerHash);
                        notifyEvent("minetogether.toast.group_invite_received", MineTogetherChat.displayName(sender));
                        break;
                    case LEFT_GROUP:
                        if (MineTogetherChat.getTarget() == ChatTarget.GROUP) {
                            MineTogetherChat.setTarget(ChatTarget.VANILLA);
                        }
                        notifyComponent(new TextComponentTranslation("minetogether.toast.left_group")
                                .appendText(": ")
                                .appendSibling(new TextComponentTranslation("minetogether.toast.left_group." + String.valueOf(event.data))));
                        break;
                    default:
                        break;
                }
            }
        });
    }

    public static void tick() {
        if (MineTogetherChat.CHAT_STATE == null || MineTogetherChat.CHAT_STATE.profileManager == null || MineTogetherChat.CHAT_STATE.ircClient == null) {
            LAST_MESSAGE_COUNT.clear();
            UNREAD_MESSAGE_COUNT.clear();
            activeChat = null;
            return;
        }

        if (++tick % 20 != 0) return;

        ProfileManager manager = MineTogetherChat.CHAT_STATE.profileManager;
        for (Profile profile : manager.getKnownProfiles()) {
            if (!profile.isFriend() || !profile.isOnline()) continue;

            IrcUser user = MineTogetherChat.CHAT_STATE.ircClient.getUser(profile);
            if (user == null || user.getChannel() == null) continue;

            int count = user.getChannel().getMessages().size();
            int previous = LAST_MESSAGE_COUNT.containsKey(profile) ? LAST_MESSAGE_COUNT.get(profile) : count;
            LAST_MESSAGE_COUNT.put(profile, count);
            if (count <= previous) continue;

            if (profile == activeChat) {
                UNREAD_MESSAGE_COUNT.put(profile, 0);
                continue;
            }

            int unread = getUnreadMessageCount(profile) + count - previous;
            int before = getUnreadMessageCount(profile);
            UNREAD_MESSAGE_COUNT.put(profile, unread);
            if (before == 0 && unread > 0 && LocalConfig.instance().friendNotifications) {
                notifyFriendMessage(profile);
            }
        }

        LAST_MESSAGE_COUNT.entrySet().removeIf(entry -> !entry.getKey().isFriend());
        UNREAD_MESSAGE_COUNT.entrySet().removeIf(entry -> !entry.getKey().isFriend());
    }

    public static int getUnreadMessageCount(Profile profile) {
        Integer unread = UNREAD_MESSAGE_COUNT.get(profile);
        return unread == null ? 0 : unread;
    }

    public static void resetUnreadMessageCount(Profile profile) {
        if (profile != null) UNREAD_MESSAGE_COUNT.put(profile, 0);
    }

    public static void setActiveChat(Profile profile) {
        activeChat = profile;
        resetUnreadMessageCount(profile);
    }

    private static void notifyEvent(String key, Object... args) {
        notifyComponent(new TextComponentTranslation(key, args));
    }

    private static void notifyFriendMessage(Profile profile) {
        ITextComponent message = new TextComponentTranslation("minetogether.toast.friend_message", MineTogetherChat.displayName(profile));
        if (profile.hasFullHash()) {
            Style style = message.getStyle();
            style.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/minetogether_friend_chat " + profile.getFullHash()));
            style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentTranslation("minetogether.chat.friend_message.info")));
        }
        notifyComponent(message);
    }

    private static void notifyComponent(ITextComponent component) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.ingameGUI != null) {
            mc.ingameGUI.getChatGUI().printChatMessage(component);
        } else {
            LOGGER.info(component.getUnformattedText());
        }
    }
}
