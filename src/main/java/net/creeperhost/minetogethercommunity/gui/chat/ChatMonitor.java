package net.creeperhost.minetogethercommunity.gui.chat;

import net.creeperhost.minetogether.lib.chat.irc.IrcChannel;
import net.creeperhost.minetogether.lib.chat.message.Message;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ChatMonitor {

    private IrcChannel channel;
    private IrcChannel.ChatListener listener;
    private final List<Message> pendingMessages = new LinkedList<>();
    private final List<Message> messages = new LinkedList<>();

    public void attach(IrcChannel channel) {
        if (this.channel == channel) return;

        if (this.channel != null && listener != null) {
            this.channel.removeListener(listener);
        }

        this.channel = channel;
        listener = null;
        synchronized (pendingMessages) {
            pendingMessages.clear();
        }
        messages.clear();

        if (channel != null) {
            synchronized (pendingMessages) {
                pendingMessages.addAll(channel.getMessages());
            }
            listener = channel.addListener(new IrcChannel.ChatListener() {
                @Override
                public void newMessage(Message message) {
                    synchronized (pendingMessages) {
                        pendingMessages.add(message);
                    }
                }
            });
        }
    }

    public IrcChannel getChannel() {
        return channel;
    }

    public List<Message> getMessages() {
        return new ArrayList<>(messages);
    }

    public void tick() {
        synchronized (pendingMessages) {
            if (!pendingMessages.isEmpty()) {
                messages.addAll(pendingMessages);
                pendingMessages.clear();
            }
        }
    }

    public void close() {
        if (channel != null && listener != null) {
            channel.removeListener(listener);
        }
        channel = null;
        listener = null;
        synchronized (pendingMessages) {
            pendingMessages.clear();
        }
        messages.clear();
    }
}
