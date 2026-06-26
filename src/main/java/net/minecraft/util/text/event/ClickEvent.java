package net.minecraft.util.text.event;

public class ClickEvent {
    private final Action action;
    private final String value;

    public ClickEvent(Action action, String value) {
        this.action = action;
        this.value = value;
    }

    public Action getAction() {
        return action;
    }

    public String getValue() {
        return value;
    }

    public net.minecraft.event.ClickEvent unwrap() {
        return new net.minecraft.event.ClickEvent(action.unwrap(), value);
    }

    public static ClickEvent wrap(net.minecraft.event.ClickEvent event) {
        return event == null ? null : new ClickEvent(Action.wrap(event.getAction()), event.getValue());
    }

    public enum Action {
        OPEN_URL(net.minecraft.event.ClickEvent.Action.OPEN_URL),
        OPEN_FILE(net.minecraft.event.ClickEvent.Action.OPEN_FILE),
        RUN_COMMAND(net.minecraft.event.ClickEvent.Action.RUN_COMMAND),
        TWITCH_USER_INFO(net.minecraft.event.ClickEvent.Action.TWITCH_USER_INFO),
        SUGGEST_COMMAND(net.minecraft.event.ClickEvent.Action.SUGGEST_COMMAND);

        private final net.minecraft.event.ClickEvent.Action legacy;

        Action(net.minecraft.event.ClickEvent.Action legacy) {
            this.legacy = legacy;
        }

        net.minecraft.event.ClickEvent.Action unwrap() {
            return legacy;
        }

        static Action wrap(net.minecraft.event.ClickEvent.Action legacy) {
            for (Action action : values()) {
                if (action.legacy == legacy) return action;
            }
            return RUN_COMMAND;
        }
    }
}
