package net.creeperhost.minetogethercommunity.modulargui;

import net.creeperhost.minetogethercommunity.gui.chat.MTStyle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class GuiDialog extends GuiElement<GuiDialog> {

    private final Supplier<String> title;
    private final Supplier<String> message;
    private final List<DialogButton> buttons = new ArrayList<>();
    private int boxWidth = 260;

    public GuiDialog(GuiElement<?> parent, Supplier<String> title, Supplier<String> message) {
        super(parent);
        this.title = title == null ? () -> "" : title;
        this.message = message == null ? () -> "" : message;
        setBounds(parent.x(), parent.y(), parent.width(), parent.height());
    }

    public GuiDialog boxWidth(int boxWidth) {
        this.boxWidth = boxWidth;
        return this;
    }

    public GuiDialog addButton(Supplier<String> label, Runnable action) {
        buttons.add(new DialogButton(label == null ? () -> "" : label, action == null ? () -> {} : action));
        return this;
    }

    public GuiDialog closeButton(Supplier<String> label) {
        return addButton(label, new Runnable() {
            @Override
            public void run() {
                setVisible(false);
            }
        });
    }

    @Override
    protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
        drawRect(x, y, x + width, y + height, MTStyle.Flat.BACKGROUND);
        int modalWidth = Math.min(boxWidth, Math.max(120, width - 40));
        int textWidth = modalWidth - 24;
        List<String> titleLines = wrapped(title.get(), textWidth);
        List<String> messageLines = wrapped(message.get(), textWidth);
        int titleHeight = titleLines.size() * font().FONT_HEIGHT;
        int messageHeight = messageLines.size() * font().FONT_HEIGHT;
        int modalHeight = 10 + titleHeight + 6 + messageHeight + 8 + 14 + 8;
        int modalLeft = x + (width - modalWidth) / 2;
        int modalTop = y + (height - modalHeight) / 2;
        drawRect(modalLeft, modalTop, modalLeft + modalWidth, modalTop + modalHeight, MTStyle.Flat.CONTENT_AREA);
        int textY = modalTop + 10;
        drawCenteredLines(titleLines, modalLeft + 12, textY, textWidth, MTStyle.Flat.TEXT);
        textY += titleHeight + 6;
        drawCenteredLines(messageLines, modalLeft + 12, textY, textWidth, MTStyle.Flat.TEXT_MUTED);

        int by = modalTop + modalHeight - 22;
        int spacing = 2;
        int count = Math.max(1, buttons.size());
        int bw = (modalWidth - 24 - spacing * (count - 1)) / count;
        for (int i = 0; i < buttons.size(); i++) {
            int bx = modalLeft + 12 + i * (bw + spacing);
            drawRect(bx, by, bx + bw, by + 18, buttonHover(mouseX, mouseY, i) ? MTStyle.Flat.BUTTON_HOVER : MTStyle.Flat.BUTTON);
            drawCenteredString(font(), trim(buttons.get(i).label.get(), bw - 4), bx + bw / 2, by + 5, MTStyle.Flat.TEXT);
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!isVisible() || mouseButton != 0) return false;
        for (int i = 0; i < buttons.size(); i++) {
            if (buttonHover(mouseX, mouseY, i)) {
                buttons.get(i).action.run();
                return true;
            }
        }
        return true;
    }

    private boolean buttonHover(int mouseX, int mouseY, int index) {
        int modalWidth = Math.min(boxWidth, Math.max(120, width - 40));
        int textWidth = modalWidth - 24;
        int modalHeight = 10 + wrapped(title.get(), textWidth).size() * font().FONT_HEIGHT + 6
                + wrapped(message.get(), textWidth).size() * font().FONT_HEIGHT + 8 + 14 + 8;
        int modalLeft = x + (width - modalWidth) / 2;
        int modalTop = y + (height - modalHeight) / 2;
        int spacing = 2;
        int count = Math.max(1, buttons.size());
        int bw = (modalWidth - 24 - spacing * (count - 1)) / count;
        int bx = modalLeft + 12 + index * (bw + spacing);
        int by = modalTop + modalHeight - 22;
        return mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 18;
    }

    private List<String> wrapped(String value, int width) {
        String safe = value == null || value.isEmpty() ? " " : value;
        return font().listFormattedStringToWidth(safe, Math.max(1, width));
    }

    private void drawCenteredLines(List<String> lines, int left, int top, int width, int color) {
        for (int i = 0; i < lines.size(); i++) {
            drawCenteredString(font(), lines.get(i), left + width / 2, top + i * font().FONT_HEIGHT, color);
        }
    }

    private String trim(String value, int width) {
        if (value == null) return "";
        if (font().getStringWidth(value) <= width) return value;
        int dots = font().getStringWidth("...");
        return font().trimStringToWidth(value, Math.max(1, width - dots)) + "...";
    }

    private static class DialogButton {
        private final Supplier<String> label;
        private final Runnable action;

        private DialogButton(Supplier<String> label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }
}
