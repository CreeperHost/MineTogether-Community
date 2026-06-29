package net.creeperhost.minetogethercommunity.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

public class ChatStyleHelper {

    @Nullable
    public static Style styleAtChatPosition(Minecraft minecraft, Font font, double mouseX, double mouseY, ChatComponent.DisplayMode displayMode, boolean includeInsertions) {
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        ActiveTextCollector.ClickableStyleFinder finder = new ActiveTextCollector.ClickableStyleFinder(font, (int) mouseX, (int) mouseY)
                .includeInsertions(includeInsertions);
        minecraft.gui.getChat().captureClickableText(finder, screenHeight, minecraft.gui.getGuiTicks(), displayMode);
        return finder.result();
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
}
