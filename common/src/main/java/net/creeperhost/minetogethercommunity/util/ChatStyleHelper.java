package net.creeperhost.minetogethercommunity.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

public class ChatStyleHelper {

    @Nullable
    public static Style styleAtChatPosition(Minecraft minecraft, Font font, double mouseX, double mouseY, ChatComponent.DisplayMode displayMode, boolean includeInsertions) {
        ChatHit hit = chatHitAtPosition(minecraft, font, minecraft.gui.hud.getChat(), mouseX, mouseY, displayMode, includeInsertions);
        return hit == null ? null : hit.style();
    }

    @Nullable
    public static ChatHit chatHitAtPosition(Minecraft minecraft, Font font, ChatComponent chat, double mouseX, double mouseY, ChatComponent.DisplayMode displayMode, boolean includeInsertions) {
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        LineTrackingStyleFinder finder = new LineTrackingStyleFinder(font, (int) mouseX, (int) mouseY, includeInsertions);
        chat.captureClickableText(finder, screenHeight, minecraft.gui.hud.getGuiTicks(), displayMode);
        Style style = finder.result();
        return style == null ? null : new ChatHit(style, finder.line());
    }

    @Nullable
    public static Style styleAtWidth(Font font, Component component, int width) {
        return styleAtWidth(font, component.getVisualOrderText(), width);
    }

    @Nullable
    public static Style styleAtWidth(Font font, FormattedCharSequence sequence, int width) {
        int[] consumed = {0};
        Style[] found = {null};
        sequence.accept((index, style, codePoint) -> {
            int charWidth = font.width(FormattedCharSequence.codepoint(codePoint, style));
            if (width <= consumed[0] + charWidth) {
                found[0] = style;
                return false;
            }
            consumed[0] += charWidth;
            return true;
        });
        return found[0];
    }

    public record ChatHit(Style style, @Nullable FormattedCharSequence line) {
    }

    private static class LineTrackingStyleFinder implements ActiveTextCollector {

        private final ActiveTextCollector.ClickableStyleFinder delegate;
        @Nullable
        private FormattedCharSequence line;

        private LineTrackingStyleFinder(Font font, int mouseX, int mouseY, boolean includeInsertions) {
            delegate = new ActiveTextCollector.ClickableStyleFinder(font, mouseX, mouseY)
                    .includeInsertions(includeInsertions);
        }

        @Override
        public Parameters defaultParameters() {
            return delegate.defaultParameters();
        }

        @Override
        public void defaultParameters(Parameters parameters) {
            delegate.defaultParameters(parameters);
        }

        @Override
        public void accept(TextAlignment alignment, int x, int y, Parameters parameters, FormattedCharSequence sequence) {
            delegate.accept(alignment, x, y, parameters, sequence);
            if (line == null && delegate.result() != null) {
                line = sequence;
            }
        }

        @Override
        public void acceptScrolling(Component component, int x, int y, int width, int startTime, int endTime, Parameters parameters) {
            delegate.acceptScrolling(component, x, y, width, startTime, endTime, parameters);
        }

        @Nullable
        private Style result() {
            return delegate.result();
        }

        @Nullable
        private FormattedCharSequence line() {
            return line;
        }
    }
}
