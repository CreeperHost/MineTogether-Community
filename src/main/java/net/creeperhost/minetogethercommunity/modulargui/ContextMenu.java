package net.creeperhost.minetogethercommunity.modulargui;

import net.creeperhost.minetogethercommunity.gui.chat.MTStyle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ContextMenu extends GuiElement<ContextMenu> {

    private static final int PADDING = 4;
    private static final int ROW_HEIGHT = 12;
    private static final int MAX_WIDTH = 300;
    private static final int TEXT_SPARE = 8;

    private final List<Option> options = new ArrayList<Option>();
    private boolean closed;

    public ContextMenu(GuiElement<?> parent) {
        super(parent);
    }

    public ContextMenu addTitle(final String title) {
        return addOption(new Supplier<String>() {
            @Override
            public String get() {
                return title;
            }
        }, 0xFFD060, null);
    }

    public ContextMenu addOption(final String label, final int color, Runnable action) {
        return addOption(new Supplier<String>() {
            @Override
            public String get() {
                return label;
            }
        }, color, action);
    }

    public ContextMenu addOption(Supplier<String> label, int color, Runnable action) {
        options.add(new Option(label == null ? new Supplier<String>() {
            @Override
            public String get() {
                return "";
            }
        } : label, color, action));
        return this;
    }

    public ContextMenu position(int mouseX, int mouseY) {
        int menuWidth = PADDING * 2;
        for (Option option : options) {
            menuWidth = Math.max(menuWidth, PADDING * 2 + Math.min(MAX_WIDTH, font().getStringWidth(label(option)) + TEXT_SPARE));
        }
        menuWidth = Math.max(80, Math.min(MAX_WIDTH + PADDING * 2, menuWidth));
        int menuHeight = PADDING * 2;
        for (int i = 0; i < options.size(); i++) {
            menuHeight += rowHeight(i, menuWidth);
        }
        int screenWidth = gui.getScreen().width;
        int screenHeight = gui.getScreen().height;
        int left = mouseX + menuWidth + 4 > screenWidth ? mouseX - menuWidth - 4 : mouseX;
        int top = mouseY + menuHeight + 4 > screenHeight ? mouseY - menuHeight - 4 : mouseY;
        left = Math.max(4, Math.min(left, screenWidth - menuWidth - 4));
        top = Math.max(4, Math.min(top, screenHeight - menuHeight - 4));
        setBounds(left, top, menuWidth, menuHeight);
        init();
        return this;
    }

    @Override
    protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
        drawRect(x, y, x + width, y + height, 0xF0101010);
        drawBorder(0x88505050, 0x55202020);
        int rowY = y + PADDING;
        for (int i = 0; i < options.size(); i++) {
            Option option = options.get(i);
            int rowHeight = rowHeight(i, width);
            if (option.action != null && rowHover(mouseX, mouseY, i)) {
                drawRect(x + 3, rowY, x + width - 3, rowY + rowHeight, MTStyle.Flat.CONTENT_AREA_HOVER);
            }
            List<String> lines = font().listFormattedStringToWidth(label(option), Math.max(1, width - PADDING * 2 - 2));
            int color = option.action == null ? 0xFFD060 : option.color;
            for (int line = 0; line < lines.size(); line++) {
                font().drawStringWithShadow(lines.get(line), x + PADDING + 1, rowY + 2 + line * font().FONT_HEIGHT, color);
            }
            rowY += rowHeight;
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!isVisible() || !isEnabled()) return false;
        if (isMouseOver(mouseX, mouseY)) {
            for (int i = 0; i < options.size(); i++) {
                Option option = options.get(i);
                if (option.action != null && rowHover(mouseX, mouseY, i)) {
                    option.action.run();
                    close();
                    return true;
                }
            }
        }
        close();
        return true;
    }

    public boolean isClosed() {
        return closed || !isVisible();
    }

    private boolean rowHover(int mouseX, int mouseY, int index) {
        int rowY = y + PADDING;
        for (int i = 0; i < index; i++) {
            rowY += rowHeight(i, width);
        }
        return mouseX >= x + 2 && mouseX < x + width - 2 && mouseY >= rowY && mouseY < rowY + rowHeight(index, width);
    }

    private int rowHeight(int index, int menuWidth) {
        if (index < 0 || index >= options.size()) return ROW_HEIGHT;
        List<String> lines = font().listFormattedStringToWidth(label(options.get(index)), Math.max(1, menuWidth - PADDING * 2 - 2));
        return Math.max(ROW_HEIGHT, lines.size() * font().FONT_HEIGHT + 4);
    }

    private String label(Option option) {
        String label = option.label.get();
        return label == null ? "" : label;
    }

    private void drawBorder(int topColor, int bottomColor) {
        drawRect(x, y, x + width, y + 1, topColor);
        drawRect(x, y + height - 1, x + width, y + height, bottomColor);
        drawRect(x, y, x + 1, y + height, topColor);
        drawRect(x + width - 1, y, x + width, y + height, bottomColor);
    }

    public void close() {
        closed = true;
        setVisible(false);
        setEnabled(false);
        if (parent != null) {
            parent.remove(this);
        }
    }

    private static class Option {
        private final Supplier<String> label;
        private final int color;
        private final Runnable action;

        private Option(Supplier<String> label, int color, Runnable action) {
            this.label = label;
            this.color = color;
            this.action = action;
        }
    }
}
