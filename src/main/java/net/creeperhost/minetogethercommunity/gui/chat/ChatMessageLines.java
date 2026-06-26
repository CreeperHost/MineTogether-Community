package net.creeperhost.minetogethercommunity.gui.chat;

import net.creeperhost.minetogether.lib.chat.message.Message;
import net.creeperhost.minetogethercommunity.util.MessageFormatter;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiUtilRenderComponents;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.event.ClickEvent;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

final class ChatMessageLines {

    private ChatMessageLines() {
    }

    static List<Line> build(FontRenderer font, List<Message> messages, int width) {
        List<Line> lines = new ArrayList<>();
        int wrapWidth = Math.max(20, width);
        for (Message message : messages) {
            ITextComponent formatted = MessageFormatter.formatGui(message);
            List<ITextComponent> wrapped = GuiUtilRenderComponents.splitText(formatted, wrapWidth, font, false, false);
            if (wrapped.isEmpty()) {
                lines.add(new Line(message, formatted));
            } else {
                for (ITextComponent component : wrapped) {
                    lines.add(new Line(message, component));
                }
            }
        }
        return lines;
    }

    static URL urlAt(FontRenderer font, Line line, int localX) {
        if (line == null || localX < 0) return null;
        int[] cursor = {0};
        return urlAt(font, line.component, localX, cursor);
    }

    static Style styleAt(FontRenderer font, Line line, int localX) {
        if (line == null || localX < 0) return null;
        int[] cursor = {0};
        return styleAt(font, line.component, localX, cursor);
    }

    private static URL urlAt(FontRenderer font, ITextComponent component, int localX, int[] cursor) {
        URL own = urlAtSegment(font, component.getStyle(), component.getUnformattedComponentText(), localX, cursor);
        if (own != null) return own;
        for (ITextComponent sibling : component.getSiblings()) {
            URL siblingUrl = urlAt(font, sibling, localX, cursor);
            if (siblingUrl != null) return siblingUrl;
        }
        return null;
    }

    private static Style styleAt(FontRenderer font, ITextComponent component, int localX, int[] cursor) {
        Style own = styleAtSegment(font, component.getStyle(), component.getUnformattedComponentText(), localX, cursor);
        if (own != null) return own;
        for (ITextComponent sibling : component.getSiblings()) {
            Style siblingStyle = styleAt(font, sibling, localX, cursor);
            if (siblingStyle != null) return siblingStyle;
        }
        return null;
    }

    private static URL urlAtSegment(FontRenderer font, Style style, String text, int localX, int[] cursor) {
        if (text == null || text.isEmpty()) return null;
        int width = font.getStringWidth(text);
        int start = cursor[0];
        cursor[0] += width;
        if (localX < start || localX >= start + width) return null;
        ClickEvent click = style == null ? null : style.getClickEvent();
        if (click == null || click.getAction() != ClickEvent.Action.OPEN_URL) return null;
        try {
            return new URL(click.getValue());
        } catch (MalformedURLException ignored) {
            return null;
        }
    }

    private static Style styleAtSegment(FontRenderer font, Style style, String text, int localX, int[] cursor) {
        if (text == null || text.isEmpty()) return null;
        int width = font.getStringWidth(text);
        int start = cursor[0];
        cursor[0] += width;
        if (localX < start || localX >= start + width) return null;
        return style;
    }

    static class Line {
        final Message message;
        final ITextComponent component;
        final String text;

        private Line(Message message, ITextComponent component) {
            this.message = message;
            this.component = component;
            this.text = component == null ? "" : component.getFormattedText();
        }
    }
}
