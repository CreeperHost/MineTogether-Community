package net.creeperhost.minetogethercommunity.modulargui;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GuiTextField extends GuiElement<GuiTextField> {

    private static final int HORIZONTAL_PADDING = 3;

    private net.minecraft.client.gui.GuiTextField field;
    private Consumer<String> changed = value -> {};
    private Supplier<String> suggestion = () -> "";
    private int suggestionColor = 0x7F7F80;
    private String text = "";
    private int maxLength = 256;
    private boolean focused;
    private boolean canLoseFocus = true;

    public GuiTextField(GuiElement<?> parent) {
        super(parent);
    }

    @Override
    public void init() {
        int textHeight = font().FONT_HEIGHT;
        int textY = y + Math.max(0, (height - textHeight) / 2);
        int fieldWidth = Math.max(1, width - HORIZONTAL_PADDING * 2);
        field = new net.minecraft.client.gui.GuiTextField(0, font(), x + HORIZONTAL_PADDING, textY, fieldWidth, textHeight);
        field.setEnableBackgroundDrawing(false);
        field.setMaxStringLength(maxLength);
        field.setText(text);
        field.setFocused(focused);
        field.setCanLoseFocus(canLoseFocus);
        field.setTextColor(0xE0E0E0);
        field.setDisabledTextColour(0x707070);
        super.init();
    }

    public GuiTextField setText(String text) {
        this.text = text == null ? "" : text;
        if (field != null) field.setText(this.text);
        return this;
    }

    public String getText() {
        return field == null ? text : field.getText();
    }

    public GuiTextField onChanged(Consumer<String> changed) {
        this.changed = changed;
        return this;
    }

    public GuiTextField setMaxLength(int maxLength) {
        this.maxLength = maxLength;
        if (field != null) field.setMaxStringLength(maxLength);
        return this;
    }

    public GuiTextField setFocused(boolean focused) {
        this.focused = focused;
        if (field != null) field.setFocused(focused);
        return this;
    }

    public GuiTextField setCanLoseFocus(boolean canLoseFocus) {
        this.canLoseFocus = canLoseFocus;
        if (field != null) field.setCanLoseFocus(canLoseFocus);
        return this;
    }

    public GuiTextField setSuggestion(String suggestion) {
        return setSuggestion(() -> suggestion == null ? "" : suggestion);
    }

    public GuiTextField setSuggestion(Supplier<String> suggestion) {
        this.suggestion = suggestion == null ? () -> "" : suggestion;
        return this;
    }

    public GuiTextField setSuggestionColor(int suggestionColor) {
        this.suggestionColor = suggestionColor;
        return this;
    }

    @Override
    public void tick() {
        if (field != null) {
            field.updateCursorCounter();
            focused = field.isFocused();
            text = field.getText();
        }
        super.tick();
    }

    @Override
    protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
        if (field != null) {
            field.drawTextBox();
            String hint = suggestion.get();
            if (field.getText().isEmpty() && hint != null && !hint.isEmpty()) {
                font().drawStringWithShadow(hint, x + HORIZONTAL_PADDING, y + Math.max(0, (height - font().FONT_HEIGHT) / 2), suggestionColor);
            }
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!isVisible() || !isEnabled() || field == null) return false;
        field.mouseClicked(mouseX, mouseY, mouseButton);
        focused = field.isFocused();
        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) throws IOException {
        if (!isVisible() || !isEnabled()) return false;
        if (field != null && field.textboxKeyTyped(typedChar, keyCode)) {
            text = field.getText();
            changed.accept(field.getText());
            return true;
        }
        return false;
    }
}
