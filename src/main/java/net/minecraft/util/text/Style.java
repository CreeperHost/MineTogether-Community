package net.minecraft.util.text;

import net.minecraft.util.ChatStyle;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

public class Style extends ChatStyle {
    private ClickEvent clickEvent;
    private HoverEvent hoverEvent;

    public Style setColor(TextFormatting color) {
        super.setColor(color == null ? null : color.unwrap());
        return this;
    }

    public Style setUnderlined(boolean underlined) {
        super.setUnderlined(underlined);
        return this;
    }

    public Style setClickEvent(ClickEvent event) {
        this.clickEvent = event;
        super.setChatClickEvent(event == null ? null : event.unwrap());
        return this;
    }

    public Style setHoverEvent(HoverEvent event) {
        this.hoverEvent = event;
        super.setChatHoverEvent(event == null ? null : event.unwrap());
        return this;
    }

    public ClickEvent getClickEvent() {
        if (clickEvent == null && super.getChatClickEvent() != null) {
            clickEvent = ClickEvent.wrap(super.getChatClickEvent());
        }
        return clickEvent;
    }

    public HoverEvent getHoverEvent() {
        if (hoverEvent == null && super.getChatHoverEvent() != null) {
            hoverEvent = HoverEvent.wrap(super.getChatHoverEvent());
        }
        return hoverEvent;
    }
}
