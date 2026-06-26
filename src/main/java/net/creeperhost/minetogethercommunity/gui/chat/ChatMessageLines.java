package net.creeperhost.minetogethercommunity.gui.chat;

import net.creeperhost.minetogether.lib.chat.message.Message;
import net.creeperhost.minetogethercommunity.util.MessageFormatter;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ChatMessageLines {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\s+|\\S+");

    private ChatMessageLines() {
    }

    static List<Line> build(FontRenderer font, List<Message> messages, int width) {
        List<Line> lines = new ArrayList<>();
        int wrapWidth = Math.max(20, width);
        for (Message message : messages) {
            ITextComponent formatted = MessageFormatter.formatGui(message);
            List<ITextComponent> wrapped = wrap(font, formatted, wrapWidth);
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

    private static List<ITextComponent> wrap(FontRenderer font, ITextComponent component, int wrapWidth) {
        List<ITextComponent> lines = new ArrayList<>();
        Wrapper wrapper = new Wrapper(font, wrapWidth, lines);
        appendWrapped(wrapper, component);
        wrapper.finish();
        return lines;
    }

    private static void appendWrapped(Wrapper wrapper, ITextComponent component) {
        wrapper.append(component.getUnformattedComponentText(), component.getStyle());
        for (ITextComponent sibling : component.getSiblings()) {
            appendWrapped(wrapper, sibling);
        }
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

    private static final class Wrapper {
        private final FontRenderer font;
        private final int wrapWidth;
        private final List<ITextComponent> lines;
        private TextComponentString current = new TextComponentString("");
        private int lineWidth;
        private boolean lineHasText;

        private Wrapper(FontRenderer font, int wrapWidth, List<ITextComponent> lines) {
            this.font = font;
            this.wrapWidth = wrapWidth;
            this.lines = lines;
        }

        private void append(String text, Style style) {
            if (text == null || text.isEmpty()) return;
            String[] hardLines = text.split("\\n", -1);
            for (int i = 0; i < hardLines.length; i++) {
                if (i > 0) newLine();
                appendSoftWrapped(hardLines[i], style);
            }
        }

        private void appendSoftWrapped(String text, Style style) {
            Matcher matcher = TOKEN_PATTERN.matcher(text);
            while (matcher.find()) {
                appendToken(matcher.group(), style);
            }
        }

        private void appendToken(String token, Style style) {
            if (token.trim().isEmpty()) {
                if (!lineHasText) return;
                int width = font.getStringWidth(token);
                if (lineWidth + width > wrapWidth) {
                    newLine();
                    return;
                }
                appendPiece(token, style, width);
                return;
            }

            int width = font.getStringWidth(token);
            if (lineHasText && lineWidth + width > wrapWidth) {
                newLine();
            }
            if (width <= wrapWidth) {
                appendPiece(token, style, width);
                return;
            }
            appendLongToken(token, style);
        }

        private void appendLongToken(String token, Style style) {
            StringBuilder part = new StringBuilder();
            int partWidth = 0;
            for (int i = 0; i < token.length(); i++) {
                char c = token.charAt(i);
                int charWidth = font.getCharWidth(c);
                if (part.length() > 0 && lineWidth + partWidth + charWidth > wrapWidth) {
                    appendPiece(part.toString(), style, partWidth);
                    part.setLength(0);
                    partWidth = 0;
                    newLine();
                }
                part.append(c);
                partWidth += charWidth;
            }
            appendPiece(part.toString(), style, partWidth);
        }

        private void appendPiece(String text, Style style, int width) {
            if (text == null || text.isEmpty()) return;
            TextComponentString piece = new TextComponentString(text);
            if (style != null) {
                piece.setChatStyle(style.createDeepCopy());
            }
            current.appendSibling(piece);
            lineWidth += width;
            if (!text.trim().isEmpty()) {
                lineHasText = true;
            }
        }

        private void newLine() {
            if (lineHasText || !current.getSiblings().isEmpty()) {
                lines.add(current);
            }
            current = new TextComponentString("");
            lineWidth = 0;
            lineHasText = false;
        }

        private void finish() {
            if (lineHasText || !current.getSiblings().isEmpty()) {
                lines.add(current);
            }
        }
    }
}
