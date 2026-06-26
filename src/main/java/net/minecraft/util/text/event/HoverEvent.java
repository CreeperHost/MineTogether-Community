package net.minecraft.util.text.event;

import net.minecraft.util.IChatComponent;
import net.minecraft.util.text.ITextComponent;

public class HoverEvent {
    private final Action action;
    private final ITextComponent value;

    public HoverEvent(Action action, ITextComponent value) {
        this.action = action;
        this.value = value;
    }

    public Action getAction() {
        return action;
    }

    public ITextComponent getValue() {
        return value;
    }

    public net.minecraft.event.HoverEvent unwrap() {
        return new net.minecraft.event.HoverEvent(action.unwrap(), value);
    }

    public static HoverEvent wrap(net.minecraft.event.HoverEvent event) {
        if (event == null) return null;
        IChatComponent legacyValue = event.getValue();
        ITextComponent textValue = legacyValue instanceof ITextComponent ? (ITextComponent) legacyValue : null;
        return new HoverEvent(Action.wrap(event.getAction()), textValue);
    }

    public enum Action {
        SHOW_TEXT(net.minecraft.event.HoverEvent.Action.SHOW_TEXT),
        SHOW_ACHIEVEMENT(net.minecraft.event.HoverEvent.Action.SHOW_ACHIEVEMENT),
        SHOW_ITEM(net.minecraft.event.HoverEvent.Action.SHOW_ITEM);

        private final net.minecraft.event.HoverEvent.Action legacy;

        Action(net.minecraft.event.HoverEvent.Action legacy) {
            this.legacy = legacy;
        }

        net.minecraft.event.HoverEvent.Action unwrap() {
            return legacy;
        }

        static Action wrap(net.minecraft.event.HoverEvent.Action legacy) {
            for (Action action : values()) {
                if (action.legacy == legacy) return action;
            }
            return SHOW_TEXT;
        }
    }
}
