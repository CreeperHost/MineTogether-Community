package net.creeperhost.minetogethercommunity.chat;

import net.creeperhost.minetogether.lib.chat.irc.IrcChannel;
import net.creeperhost.minetogether.lib.chat.message.Message;
import net.creeperhost.minetogether.lib.chat.profile.ProfileManager;
import net.creeperhost.minetogethercommunity.gui.PreviewElement;
import net.creeperhost.minetogethercommunity.util.MessageFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.event.ClickEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.net.MalformedURLException;
import java.net.URL;

public class InGameChatBridge {

    private static final int MAX_HISTORY_PER_TARGET = 150;
    private static final Map<ChatTarget, List<PendingMessage>> PENDING = new EnumMap<ChatTarget, List<PendingMessage>>(ChatTarget.class);
    private static final Map<ChatTarget, List<HistoryEntry>> MT_HISTORY = new EnumMap<ChatTarget, List<HistoryEntry>>(ChatTarget.class);
    private static final List<IChatComponent> VANILLA_HISTORY = new ArrayList<IChatComponent>();

    private static IrcChannel publicChannel;
    private static IrcChannel groupChannel;
    private static IrcChannel.ChatListener publicListener;
    private static IrcChannel.ChatListener groupListener;
    private static String groupChannelName;
    private static ChatTarget renderedTarget = ChatTarget.VANILLA;
    private static boolean replaying;
    private static int nextMessageId = 1;

    private InGameChatBridge() {
    }

    public static void tick() {
        if (MineTogetherChat.CHAT_STATE == null) return;
        bindPublicChannel();
        bindGroupChannel();
        flushPending();
        ChatTarget target = MineTogetherChat.getTarget();
        if (target != renderedTarget) {
            renderTarget(target);
        }
    }

    public static void reset() {
        if (publicChannel != null && publicListener != null) {
            publicChannel.removeListener(publicListener);
        }
        if (groupChannel != null && groupListener != null) {
            groupChannel.removeListener(groupListener);
        }
        publicChannel = null;
        groupChannel = null;
        publicListener = null;
        groupListener = null;
        groupChannelName = null;
        synchronized (PENDING) {
            PENDING.clear();
        }
        synchronized (MT_HISTORY) {
            MT_HISTORY.clear();
        }
        nextMessageId = 1;
        renderTarget(ChatTarget.VANILLA);
    }

    public static void clearHistories() {
        synchronized (PENDING) {
            PENDING.clear();
        }
        synchronized (MT_HISTORY) {
            MT_HISTORY.clear();
        }
        synchronized (VANILLA_HISTORY) {
            VANILLA_HISTORY.clear();
        }
        nextMessageId = 1;
        renderedTarget = ChatTarget.VANILLA;
    }

    public static void captureVanillaMessage(IChatComponent message) {
        if (message == null || replaying) return;
        synchronized (VANILLA_HISTORY) {
            VANILLA_HISTORY.add(message.createCopy());
            trim(VANILLA_HISTORY);
        }
    }

    public static void onTargetChanged(ChatTarget target) {
        renderTarget(target == null ? ChatTarget.VANILLA : target);
    }

    public static Message getClickedMessage(int mouseX, int mouseY) {
        return getMessageFromComponent(mouseX, mouseY, false);
    }

    public static Message getMessageUnderMouse(int mouseX, int mouseY) {
        return getMessageFromComponent(mouseX, mouseY, true);
    }

    private static Message getMessageFromComponent(int mouseX, int mouseY, boolean allowMessageMarker) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.ingameGUI == null) return null;
        IChatComponent component = mc.ingameGUI.getChatGUI().getChatComponent(mouseX, mouseY);
        if (component == null || component.getChatStyle() == null) return null;
        ClickEvent event = ClickEvent.wrap(component.getChatStyle().getChatClickEvent());
        if (event == null || event.getValue() == null) return null;
        String value = event.getValue();
        String prefix = clickPrefix(value, allowMessageMarker);
        if (prefix == null) return null;
        try {
            int id = Integer.parseInt(value.substring(prefix.length()));
            return findMessage(id);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String clickPrefix(String value, boolean allowMessageMarker) {
        String namePrefix = MessageFormatter.CLICK_NAME + ":";
        if (value.startsWith(namePrefix)) {
            return namePrefix;
        }
        String messagePrefix = MessageFormatter.CLICK_MESSAGE + ":";
        if (allowMessageMarker && value.startsWith(messagePrefix)) {
            return messagePrefix;
        }
        return null;
    }

    public static PreviewElement.URLInfo getUrlUnderMouse(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.ingameGUI == null) return null;
        IChatComponent component = mc.ingameGUI.getChatGUI().getChatComponent(mouseX, mouseY);
        if (component == null || component.getChatStyle() == null) return null;
        ClickEvent event = ClickEvent.wrap(component.getChatStyle().getChatClickEvent());
        if (event == null || event.getAction() != ClickEvent.Action.OPEN_URL) return null;
        try {
            return new PreviewElement.URLInfo(new URL(event.getValue()), false);
        } catch (MalformedURLException ignored) {
            return null;
        }
    }

    private static void bindPublicChannel() {
        IrcChannel channel = MineTogetherChat.CHAT_STATE.ircClient.getPrimaryChannel();
        if (channel == null || channel == publicChannel) return;
        if (publicChannel != null && publicListener != null) {
            publicChannel.removeListener(publicListener);
        }
        publicChannel = channel;
        publicListener = message -> queue(ChatTarget.PUBLIC, message);
        publicChannel.addListener(publicListener);
    }

    private static void bindGroupChannel() {
        ProfileManager.PrivateGroup group = MineTogetherChat.CHAT_STATE.profileManager.getPrivateGroup();
        String newName = group == null ? null : group.channelName;
        if (newName == null || newName.trim().isEmpty()) {
            unbindGroupChannel();
            return;
        }
        if (newName.equals(groupChannelName) && groupChannel != null) return;

        unbindGroupChannel();
        IrcChannel channel = MineTogetherChat.CHAT_STATE.ircClient.getChannel(newName);
        if (channel == null) return;

        groupChannelName = newName;
        groupChannel = channel;
        groupListener = message -> queue(ChatTarget.GROUP, message);
        groupChannel.addListener(groupListener);
    }

    private static void unbindGroupChannel() {
        if (groupChannel != null && groupListener != null) {
            groupChannel.removeListener(groupListener);
        }
        groupChannel = null;
        groupListener = null;
        groupChannelName = null;
    }

    private static void queue(ChatTarget target, Message message) {
        if (message == null || message.sender != null && message.sender.isMuted()) return;
        synchronized (PENDING) {
            List<PendingMessage> pending = PENDING.get(target);
            if (pending == null) {
                pending = new ArrayList<PendingMessage>();
                PENDING.put(target, pending);
            }
            pending.add(new PendingMessage(target, message));
            while (pending.size() > MAX_HISTORY_PER_TARGET) {
                pending.remove(0);
            }
        }
    }

    private static void flushPending() {
        List<PendingMessage> copy = new ArrayList<PendingMessage>();
        synchronized (PENDING) {
            for (List<PendingMessage> pending : PENDING.values()) {
                copy.addAll(pending);
                pending.clear();
            }
        }
        if (copy.isEmpty()) return;

        ChatTarget target = MineTogetherChat.getTarget();

        Minecraft mc = Minecraft.getMinecraft();
        for (PendingMessage pending : copy) {
            HistoryEntry entry = addMtHistory(pending.target, pending.message);
            if (pending.target == target && renderedTarget == target && mc.ingameGUI != null) {
                mc.ingameGUI.getChatGUI().printChatMessage(format(pending.target, entry));
            }
        }
    }

    private static ITextComponent format(ChatTarget target, HistoryEntry entry) {
        return MessageFormatter.formatInGame(target, entry.message, entry.id);
    }

    private static void renderTarget(ChatTarget target) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.ingameGUI == null) {
            renderedTarget = target;
            return;
        }

        GuiNewChat chat = mc.ingameGUI.getChatGUI();
        replaying = true;
        try {
            chat.clearChatMessages();
            if (target == ChatTarget.VANILLA) {
                for (IChatComponent component : vanillaHistorySnapshot()) {
                    chat.printChatMessage(component.createCopy());
                }
            } else {
                for (HistoryEntry entry : mtHistorySnapshot(target)) {
                    chat.printChatMessage(format(target, entry));
                }
            }
            chat.resetScroll();
            renderedTarget = target;
        } finally {
            replaying = false;
        }
    }

    private static List<IChatComponent> vanillaHistorySnapshot() {
        synchronized (VANILLA_HISTORY) {
            return new ArrayList<IChatComponent>(VANILLA_HISTORY);
        }
    }

    private static List<HistoryEntry> mtHistorySnapshot(ChatTarget target) {
        synchronized (MT_HISTORY) {
            List<HistoryEntry> history = MT_HISTORY.get(target);
            return history == null ? new ArrayList<HistoryEntry>() : new ArrayList<HistoryEntry>(history);
        }
    }

    private static HistoryEntry addMtHistory(ChatTarget target, Message message) {
        synchronized (MT_HISTORY) {
            List<HistoryEntry> history = MT_HISTORY.get(target);
            if (history == null) {
                history = new ArrayList<HistoryEntry>();
                MT_HISTORY.put(target, history);
            }
            HistoryEntry entry = new HistoryEntry(nextMessageId++, message);
            history.add(entry);
            trim(history);
            return entry;
        }
    }

    private static Message findMessage(int id) {
        synchronized (MT_HISTORY) {
            for (List<HistoryEntry> history : MT_HISTORY.values()) {
                for (HistoryEntry entry : history) {
                    if (entry.id == id) {
                        return entry.message;
                    }
                }
            }
        }
        return null;
    }

    private static void trim(List<?> list) {
        while (list.size() > MAX_HISTORY_PER_TARGET) {
            list.remove(0);
        }
    }

    private static class PendingMessage {
        private final ChatTarget target;
        private final Message message;

        private PendingMessage(ChatTarget target, Message message) {
            this.target = target;
            this.message = message;
        }
    }

    private static class HistoryEntry {
        private final int id;
        private final Message message;

        private HistoryEntry(int id, Message message) {
            this.id = id;
            this.message = message;
        }
    }
}
