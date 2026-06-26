package net.creeperhost.minetogethercommunity.gui.chat;

import net.minecraft.client.gui.Gui;

public final class MTStyle {

    private MTStyle() {
    }

    public static final class Flat {
        public static final int BACKGROUND = 0xF0000000;
        public static final int CONTENT_AREA = 0x80202020;
        public static final int CONTENT_AREA_HOVER = 0x80505050;
        public static final int BUTTON = 0xFF505050;
        public static final int BUTTON_HOVER = 0xFF909090;
        public static final int BUTTON_DISABLED = 0x88202020;
        public static final int BUTTON_PRIMARY = 0xFF118811;
        public static final int BUTTON_PRIMARY_HOVER = 0xFF44AA44;
        public static final int BUTTON_CAUTION = 0xFF881111;
        public static final int BUTTON_CAUTION_HOVER = 0xFFAA4444;
        public static final int TEXT = 0xFFFFFF;
        public static final int TEXT_DISABLED = 0x777777;
        public static final int TEXT_MUTED = 0xAAAAAA;
        public static final int TEXT_WARN = 0xFFFFAA;

        private Flat() {
        }

        public static int listEntryBackground(boolean hoveredOrSelected) {
            return hoveredOrSelected ? 0x40FFFFFF : 0;
        }

        public static void drawVerticalScrollBar(int x, int y, int width, int height, int contentSize, int viewSize, int scrollTop, boolean hovered) {
            if (contentSize <= viewSize || height <= 0 || width <= 0) return;
            int maxScroll = Math.max(1, contentSize - viewSize);
            int clampedScroll = Math.max(0, Math.min(scrollTop, maxScroll));
            int trackColor = hovered ? 0x80505050 : 0x20505050;
            Gui.drawRect(x, y, x + width, y + height, trackColor);

            int handleHeight = Math.max(10, height * viewSize / contentSize);
            if (handleHeight > height) handleHeight = height;
            int travel = Math.max(0, height - handleHeight);
            int handleTop = y + (travel * clampedScroll / maxScroll);
            Gui.drawRect(x, handleTop, x + width, handleTop + handleHeight, hovered ? 0xFFFFFFFF : 0x88FFFFFF);
        }
    }
}
