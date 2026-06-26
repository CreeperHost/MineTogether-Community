package net.creeperhost.minetogethercommunity.chat;

import net.creeperhost.minetogether.lib.chat.irc.IrcState;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ChatConstants {

    public static final Map<IrcState, String> STATE_DESC_LOOKUP;
    public static final Map<IrcState, String> STATE_SUGGESTION_LOOKUP;

    static {
        EnumMap<IrcState, String> descriptions = new EnumMap<>(IrcState.class);
        descriptions.put(IrcState.DISCONNECTED, "Disconnected");
        descriptions.put(IrcState.CONNECTING, "Connecting");
        descriptions.put(IrcState.RECONNECTING, "Reconnecting");
        descriptions.put(IrcState.CONNECTED, "Connected");
        descriptions.put(IrcState.CRASHED, "Engine crashed");
        descriptions.put(IrcState.BANNED, "Banned");
        descriptions.put(IrcState.VERIFYING, "Verifying");
        STATE_DESC_LOOKUP = Collections.unmodifiableMap(descriptions);

        EnumMap<IrcState, String> suggestions = new EnumMap<>(IrcState.class);
        suggestions.put(IrcState.DISCONNECTED, "minetogether.screen.chat.suggestion.disconnected");
        suggestions.put(IrcState.CONNECTING, "minetogether.screen.chat.suggestion.connecting");
        suggestions.put(IrcState.RECONNECTING, "minetogether.screen.chat.suggestion.reconnecting");
        suggestions.put(IrcState.CRASHED, "minetogether.screen.chat.suggestion.crashed");
        suggestions.put(IrcState.BANNED, "minetogether.screen.chat.suggestion.banned");
        suggestions.put(IrcState.VERIFYING, "minetogether.screen.chat.suggestion.verifying");
        STATE_SUGGESTION_LOOKUP = Collections.unmodifiableMap(suggestions);
    }

    private ChatConstants() {
    }

    public static String describe(IrcState state) {
        String description = STATE_DESC_LOOKUP.get(state);
        return description == null ? state.name() : description;
    }
}
