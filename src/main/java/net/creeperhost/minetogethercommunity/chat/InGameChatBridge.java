package net.creeperhost.minetogethercommunity.chat;

import net.creeperhost.minetogethercommunity.util.DiagnosticLog;

import net.creeperhost.minetogether.lib.chat.irc.IrcChannel;
import net.creeperhost.minetogether.lib.chat.message.Message;
import net.creeperhost.minetogether.lib.chat.profile.ProfileManager;
import net.creeperhost.minetogethercommunity.gui.PreviewElement;
import net.creeperhost.minetogethercommunity.util.MessageFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.event.ClickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.net.MalformedURLException;
import java.net.URL;

public class InGameChatBridge {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Chat");
    private static final int MAX_HISTORY_PER_TARGET = 150;
    private static final Map<ChatTarget, List<PendingMessage>> PENDING = new EnumMap<ChatTarget, List<PendingMessage>>(ChatTarget.class);
    private static final Map<ChatTarget, List<HistoryEntry>> MT_HISTORY = new EnumMap<ChatTarget, List<HistoryEntry>>(ChatTarget.class);
    private static final Map<ChatTarget, List<Message>> SEEN_MESSAGES = new EnumMap<ChatTarget, List<Message>>(ChatTarget.class);
    private static final List<IChatComponent> VANILLA_HISTORY = new ArrayList<IChatComponent>();
    private static final Field DRAWN_CHAT_LINES = findField(GuiNewChat.class, "drawnChatLines", "field_146253_i", "i");
    private static final Field CHAT_LINES = findField(GuiNewChat.class, "chatLines", "field_146252_h", "h");

    private static IrcChannel publicChannel;
    private static IrcChannel groupChannel;
    private static IrcChannel.ChatListener publicListener;
    private static IrcChannel.ChatListener groupListener;
    private static String groupChannelName;
    private static ChatTarget renderedTarget = ChatTarget.VANILLA;
    private static GuiNewChat renderedChat;
    private static ChatTarget deferredRenderTarget;
    private static boolean replaying;
    private static int nextMessageId = 1;

    private InGameChatBridge() {
    }

    public static void tick() {
        if (MineTogetherChat.CHAT_STATE == null) return;
        bindPublicChannel();
        bindGroupChannel();
        syncChannelHistory(ChatTarget.PUBLIC, publicChannel);
        syncChannelHistory(ChatTarget.GROUP, groupChannel);
        flushPending();
        ChatTarget target = MineTogetherChat.getTarget();
        GuiNewChat activeChat = Minecraft.getMinecraft().ingameGUI == null
                ? null
                : Minecraft.getMinecraft().ingameGUI.getChatGUI();
        if (target != renderedTarget || target == deferredRenderTarget
                || target != ChatTarget.VANILLA && activeChat != null && activeChat != renderedChat) {
            renderTarget(target);
        } else if (target == ChatTarget.VANILLA && activeChat != null) {
            renderedChat = activeChat;
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
        synchronized (SEEN_MESSAGES) {
            SEEN_MESSAGES.clear();
        }
        nextMessageId = 1;
        deferredRenderTarget = null;
        renderTarget(ChatTarget.VANILLA);
    }

    public static void clearHistories() {
        synchronized (PENDING) {
            PENDING.clear();
        }
        synchronized (MT_HISTORY) {
            MT_HISTORY.clear();
        }
        synchronized (SEEN_MESSAGES) {
            SEEN_MESSAGES.clear();
        }
        synchronized (VANILLA_HISTORY) {
            VANILLA_HISTORY.clear();
        }
        nextMessageId = 1;
        deferredRenderTarget = null;
        renderedTarget = ChatTarget.VANILLA;
        renderedChat = null;
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

    public static void onChatOpened() {
        ChatTarget target = MineTogetherChat.getTarget();
        if (target != ChatTarget.VANILLA) {
            renderTarget(target);
        } else if (Minecraft.getMinecraft().ingameGUI != null) {
            renderedChat = Minecraft.getMinecraft().ingameGUI.getChatGUI();
        }
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
        String value = component.getChatStyle() instanceof Style ? ((Style) component.getChatStyle()).getInsertion() : null;
        ClickEvent event = ClickEvent.wrap(component.getChatStyle().getChatClickEvent());
        if (value == null && event != null) {
            value = event.getValue();
        }
        if (value == null) return null;
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

    public static List<ITextComponent> focusedHistoryLines(ChatTarget target, int maxLines) {
        List<HistoryEntry> snapshot = mtHistorySnapshot(target);
        List<ITextComponent> lines = new ArrayList<ITextComponent>();
        for (int i = snapshot.size() - 1; i >= 0 && lines.size() < maxLines; i--) {
            lines.add(format(target, snapshot.get(i)));
        }
        return lines;
    }

    public static int historySize(ChatTarget target) {
        synchronized (MT_HISTORY) {
            List<HistoryEntry> history = MT_HISTORY.get(target);
            return history == null ? 0 : history.size();
        }
    }

    public static ChatTarget renderedTarget() {
        return renderedTarget;
    }

    private static void bindPublicChannel() {
        IrcChannel channel = MineTogetherChat.CHAT_STATE.ircClient.getPrimaryChannel();
        if (channel == null || channel == publicChannel) return;
        if (publicChannel != null && publicListener != null) {
            publicChannel.removeListener(publicListener);
        }
        publicChannel = channel;
        publicListener = message -> queueIfNew(ChatTarget.PUBLIC, message, "listener");
        publicChannel.addListener(publicListener);
        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] bound in-game public chat bridge to channel {} existingMessages={}",
                channel.getName(), Integer.valueOf(channel.getMessages().size()));
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
        groupListener = message -> queueIfNew(ChatTarget.GROUP, message, "listener");
        groupChannel.addListener(groupListener);
        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] bound in-game group chat bridge to channel {} existingMessages={}",
                channel.getName(), Integer.valueOf(channel.getMessages().size()));
    }

    private static void unbindGroupChannel() {
        if (groupChannel != null && groupListener != null) {
            groupChannel.removeListener(groupListener);
        }
        groupChannel = null;
        groupListener = null;
        groupChannelName = null;
    }

    private static void syncChannelHistory(ChatTarget target, IrcChannel channel) {
        if (channel == null) return;
        List<Message> snapshot;
        try {
            snapshot = new ArrayList<Message>(channel.getMessages());
        } catch (RuntimeException ex) {
            DiagnosticLog.debug(LOGGER, "[MT-1710-DIAG] could not snapshot {} chat channel history", target, ex);
            return;
        }

        int queued = 0;
        int first = Math.max(0, snapshot.size() - MAX_HISTORY_PER_TARGET);
        for (int i = first; i < snapshot.size(); i++) {
            if (queueIfNew(target, snapshot.get(i), "history")) {
                queued++;
            }
        }
        if (queued > 0) {
            DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] queued {} {} in-game chat message(s) from channel history", Integer.valueOf(queued), target);
        }
    }

    private static boolean queueIfNew(ChatTarget target, Message message, String source) {
        if (message == null || message.sender != null && message.sender.isMuted()) return false;
        synchronized (SEEN_MESSAGES) {
            List<Message> seen = SEEN_MESSAGES.get(target);
            if (seen == null) {
                seen = new ArrayList<Message>();
                SEEN_MESSAGES.put(target, seen);
            }
            if (containsIdentity(seen, message)) {
                return false;
            }
            seen.add(message);
            trim(seen);
        }
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
        DiagnosticLog.debug(LOGGER, "[MT-1710-DIAG] queued {} in-game chat message from {}", target, source);
        return true;
    }

    private static boolean containsIdentity(List<Message> messages, Message message) {
        for (Message seen : messages) {
            if (seen == message) {
                return true;
            }
        }
        return false;
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
        GuiNewChat activeChat = mc.ingameGUI == null ? null : mc.ingameGUI.getChatGUI();
        int drawnBefore = chatLineCount(activeChat, DRAWN_CHAT_LINES);
        int storedBefore = chatLineCount(activeChat, CHAT_LINES);
        int printed = 0;
        boolean currentTargetNeedsReplay = false;
        for (PendingMessage pending : copy) {
            HistoryEntry entry = addMtHistory(pending.target, pending.message);
            if (pending.target == target && renderedTarget == target && activeChat != null && activeChat == renderedChat) {
                activeChat.printChatMessage(format(pending.target, entry));
                printed++;
            } else if (pending.target == target) {
                currentTargetNeedsReplay = true;
            }
        }
        if (currentTargetNeedsReplay) {
            deferredRenderTarget = target;
        }
        int drawnAfter = chatLineCount(activeChat, DRAWN_CHAT_LINES);
        int storedAfter = chatLineCount(activeChat, CHAT_LINES);
        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] flushed in-game chat pending={} currentTarget={} renderedTarget={} printed={} publicHistory={} groupHistory={} gui={} drawnBefore={} drawnAfter={} storedBefore={} storedAfter={}",
                Integer.valueOf(copy.size()), target, renderedTarget, Integer.valueOf(printed),
                Integer.valueOf(historySize(ChatTarget.PUBLIC)), Integer.valueOf(historySize(ChatTarget.GROUP)),
                activeChat == null ? "<missing>" : "<present>",
                Integer.valueOf(drawnBefore), Integer.valueOf(drawnAfter), Integer.valueOf(storedBefore), Integer.valueOf(storedAfter));
    }

    private static ITextComponent format(ChatTarget target, HistoryEntry entry) {
        return MessageFormatter.formatInGame(target, entry.message, entry.id);
    }

    private static void renderTarget(ChatTarget target) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.ingameGUI == null) {
            if (deferredRenderTarget != target) {
                deferredRenderTarget = target;
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] deferred in-game chat replay target={} because ingameGUI is missing", target);
            }
            return;
        }

        GuiNewChat chat = mc.ingameGUI.getChatGUI();
        int drawnBefore = chatLineCount(chat, DRAWN_CHAT_LINES);
        int storedBefore = chatLineCount(chat, CHAT_LINES);
        replaying = true;
        try {
            clearVisibleChat(chat);
            int drawnAfterClear = chatLineCount(chat, DRAWN_CHAT_LINES);
            int storedAfterClear = chatLineCount(chat, CHAT_LINES);
            int replayed = 0;
            if (target == ChatTarget.VANILLA) {
                for (IChatComponent component : vanillaHistorySnapshot()) {
                    chat.printChatMessage(component.createCopy());
                    replayed++;
                }
            } else {
                for (HistoryEntry entry : mtHistorySnapshot(target)) {
                    chat.printChatMessage(format(target, entry));
                    replayed++;
                }
            }
            chat.resetScroll();
            renderedTarget = target;
            renderedChat = chat;
            deferredRenderTarget = null;
            DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] replayed in-game chat target={} messages={} publicHistory={} groupHistory={} drawnBefore={} storedBefore={} drawnAfterClear={} storedAfterClear={} drawnAfterReplay={} storedAfterReplay={}",
                    target, Integer.valueOf(replayed),
                    Integer.valueOf(historySize(ChatTarget.PUBLIC)), Integer.valueOf(historySize(ChatTarget.GROUP)),
                    Integer.valueOf(drawnBefore), Integer.valueOf(storedBefore),
                    Integer.valueOf(drawnAfterClear), Integer.valueOf(storedAfterClear),
                    Integer.valueOf(chatLineCount(chat, DRAWN_CHAT_LINES)), Integer.valueOf(chatLineCount(chat, CHAT_LINES)));
        } finally {
            replaying = false;
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearVisibleChat(GuiNewChat chat) {
        try {
            ((List<Object>) DRAWN_CHAT_LINES.get(chat)).clear();
            ((List<Object>) CHAT_LINES.get(chat)).clear();
            chat.resetScroll();
        } catch (Throwable ex) {
            DiagnosticLog.warn(LOGGER, "[MT-1710-DIAG] falling back to full chat clear; sent history may be reset", ex);
            chat.clearChatMessages();
        }
    }

    @SuppressWarnings("unchecked")
    private static int chatLineCount(GuiNewChat chat, Field field) {
        if (chat == null || field == null) return -1;
        try {
            return ((List<Object>) field.get(chat)).size();
        } catch (Throwable ex) {
            DiagnosticLog.debug(LOGGER, "[MT-1710-DIAG] could not inspect in-game chat line count", ex);
            return -1;
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

    private static Field findField(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new IllegalStateException("Could not find field on " + owner.getName());
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
