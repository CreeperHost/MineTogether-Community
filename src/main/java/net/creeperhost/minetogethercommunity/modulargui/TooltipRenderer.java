package net.creeperhost.minetogethercommunity.modulargui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.util.List;

public final class TooltipRenderer {

    private TooltipRenderer() {
    }

    public static void draw(Gui gui, FontRenderer font, String value, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (value == null || value.isEmpty()) return;
        int maxWidth = Math.min(220, Math.max(80, screenWidth - 16));
        List<String> lines = font.listFormattedStringToWidth(value, maxWidth);
        int tooltipWidth = 0;
        for (String line : lines) {
            tooltipWidth = Math.max(tooltipWidth, font.getStringWidth(line));
        }
        int tooltipHeight = lines.size() * font.FONT_HEIGHT + 6;
        int tooltipX = mouseX + 8;
        int tooltipY = mouseY + 8;
        if (tooltipX + tooltipWidth + 8 > screenWidth) {
            tooltipX = mouseX - tooltipWidth - 12;
        }
        if (tooltipY + tooltipHeight > screenHeight) {
            tooltipY = mouseY - tooltipHeight - 8;
        }
        tooltipX = Math.max(4, Math.min(tooltipX, screenWidth - tooltipWidth - 8));
        tooltipY = Math.max(4, Math.min(tooltipY, screenHeight - tooltipHeight - 4));

        gui.drawRect(tooltipX - 3, tooltipY - 3, tooltipX + tooltipWidth + 5, tooltipY + tooltipHeight, 0xF0101010);
        gui.drawRect(tooltipX - 2, tooltipY - 2, tooltipX + tooltipWidth + 4, tooltipY - 1, 0xFF555555);
        gui.drawRect(tooltipX - 2, tooltipY + tooltipHeight - 1, tooltipX + tooltipWidth + 4, tooltipY + tooltipHeight, 0xFF222222);
        for (int i = 0; i < lines.size(); i++) {
            font.drawStringWithShadow(lines.get(i), tooltipX, tooltipY + i * font.FONT_HEIGHT, 0xE0E0E0);
        }
    }
}
