package net.creeperhost.minetogethercommunity.modulargui;

import net.creeperhost.minetogethercommunity.gui.chat.MTStyle;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.function.Consumer;

public class GuiTextPrompt extends GuiElement<GuiTextPrompt> {

    private static final int MODAL_WIDTH = 200;
    private static final int PADDING = 5;
    private static final int TEXT_FIELD_HEIGHT = 14;
    private static final int BUTTON_HEIGHT = 14;
    private static final int BUTTON_SPACING = 2;

    private final String title;
    private final String initialValue;
    private final Consumer<String> callback;
    private net.minecraft.client.gui.GuiTextField field;

    public GuiTextPrompt(GuiElement<?> parent, String title, String initialValue, Consumer<String> callback) {
        super(parent);
        this.title = title == null ? "" : title;
        this.initialValue = initialValue == null ? "" : initialValue;
        this.callback = callback;
        setBounds(parent.x(), parent.y(), parent.width(), parent.height());
    }

    @Override
    public void init() {
        int modalWidth = modalWidth();
        int modalLeft = x + (width - modalWidth) / 2;
        int textHeight = font().FONT_HEIGHT;
        int textY = textFieldTop() + Math.max(0, (TEXT_FIELD_HEIGHT - textHeight) / 2);
        field = new net.minecraft.client.gui.GuiTextField(font(), modalLeft + PADDING + 3, textY, modalWidth - PADDING * 2 - 6, textHeight);
        field.setEnableBackgroundDrawing(false);
        field.setMaxStringLength(64);
        field.setText(initialValue);
        field.setFocused(true);
        super.init();
    }

    @Override
    protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
        drawRect(x, y, x + width, y + height, MTStyle.Flat.BACKGROUND);
        int modalWidth = modalWidth();
        int modalLeft = x + (width - modalWidth) / 2;
        int modalTop = modalTop();
        drawTooltipBackground(modalLeft, modalTop, modalWidth, modalHeight());
        drawCenteredString(font(), title, modalLeft + modalWidth / 2, modalTop + PADDING, MTStyle.Flat.TEXT);
        drawRect(modalLeft + PADDING, textFieldTop(), modalLeft + modalWidth - PADDING, textFieldTop() + TEXT_FIELD_HEIGHT, 0xA0202020);
        if (field != null) field.drawTextBox();

        int buttonTop = buttonTop();
        int buttonWidth = (modalWidth - PADDING * 2 - BUTTON_SPACING) / 2;
        drawButton(mouseX, mouseY, modalLeft + PADDING, buttonTop, buttonWidth, I18n.format("minetogether.gui.button.ok"), true);
        drawButton(mouseX, mouseY, modalLeft + PADDING + buttonWidth + BUTTON_SPACING, buttonTop, buttonWidth, I18n.format("minetogether.gui.button.cancel"), false);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!isVisible() || mouseButton != 0) return false;
        int modalWidth = modalWidth();
        int modalLeft = x + (width - modalWidth) / 2;
        int buttonTop = buttonTop();
        int buttonWidth = (modalWidth - PADDING * 2 - BUTTON_SPACING) / 2;
        if (field != null) field.mouseClicked(mouseX, mouseY, mouseButton);
        if (buttonHover(mouseX, mouseY, modalLeft + PADDING, buttonTop, buttonWidth)) {
            submit();
        } else if (buttonHover(mouseX, mouseY, modalLeft + PADDING + buttonWidth + BUTTON_SPACING, buttonTop, buttonWidth)) {
            close();
        }
        return true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) throws IOException {
        if (!isVisible()) return false;
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            submit();
            return true;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            close();
            return true;
        }
        return field != null && field.textboxKeyTyped(typedChar, keyCode);
    }

    private int modalWidth() {
        return Math.min(MODAL_WIDTH, Math.max(140, width - 40));
    }

    private int modalTop() {
        return y + height / 2 - 20;
    }

    private int modalHeight() {
        return PADDING + font().FONT_HEIGHT + 3 + TEXT_FIELD_HEIGHT + 3 + BUTTON_HEIGHT + PADDING;
    }

    private int textFieldTop() {
        return modalTop() + PADDING + font().FONT_HEIGHT + 3;
    }

    private int buttonTop() {
        return textFieldTop() + TEXT_FIELD_HEIGHT + 3;
    }

    private void submit() {
        String value = field == null ? "" : field.getText();
        close();
        if (callback != null) callback.accept(value);
    }

    private void close() {
        setVisible(false);
        setEnabled(false);
        if (parent != null) {
            parent.remove(this);
        }
    }

    private void drawButton(int mouseX, int mouseY, int bx, int by, int bw, String label, boolean primary) {
        boolean hover = buttonHover(mouseX, mouseY, bx, by, bw);
        int color = primary
                ? (hover ? MTStyle.Flat.BUTTON_PRIMARY_HOVER : MTStyle.Flat.BUTTON_PRIMARY)
                : (hover ? MTStyle.Flat.BUTTON_CAUTION_HOVER : MTStyle.Flat.BUTTON_CAUTION);
        drawRect(bx, by, bx + bw, by + BUTTON_HEIGHT, color);
        drawCenteredString(font(), label, bx + bw / 2, by + 3, MTStyle.Flat.TEXT);
    }

    private boolean buttonHover(int mouseX, int mouseY, int bx, int by, int bw) {
        return mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + BUTTON_HEIGHT;
    }

    private void drawTooltipBackground(int left, int top, int modalWidth, int modalHeight) {
        drawRect(left, top, left + modalWidth, top + modalHeight, 0xF0101010);
        drawRect(left, top, left + modalWidth, top + 1, 0x88505050);
        drawRect(left, top + modalHeight - 1, left + modalWidth, top + modalHeight, 0x55202020);
        drawRect(left, top, left + 1, top + modalHeight, 0x88505050);
        drawRect(left + modalWidth - 1, top, left + modalWidth, top + modalHeight, 0x55202020);
    }
}
