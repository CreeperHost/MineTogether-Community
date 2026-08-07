package net.creeperhost.minetogethercommunity.modulargui;

import net.creeperhost.minetogethercommunity.gui.chat.MTStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class GuiButton extends GuiElement<GuiButton> {

    public static final int LEFT_CLICK = 0;
    public static final int RIGHT_CLICK = 1;

    private Supplier<String> label;
    private Runnable action = () -> {};
    private BooleanSupplier toggled = () -> false;
    private boolean pressed;
    private ButtonStyle style = ButtonStyle.NORMAL;

    public GuiButton(GuiElement<?> parent, ITextComponent label) {
        this(parent, () -> label.getFormattedText());
    }

    public GuiButton(GuiElement<?> parent, Supplier<String> label) {
        super(parent);
        this.label = label;
    }

    public GuiButton onPress(Runnable action) {
        this.action = action;
        return this;
    }

    public GuiButton setLabel(Supplier<String> label) {
        this.label = label;
        return this;
    }

    public String getLabel() {
        return label == null ? null : label.get();
    }

    public GuiButton setToggleMode(BooleanSupplier toggled) {
        this.toggled = toggled == null ? () -> false : toggled;
        return this;
    }

    public GuiButton primary() {
        this.style = ButtonStyle.PRIMARY;
        return this;
    }

    public GuiButton caution() {
        this.style = ButtonStyle.CAUTION;
        return this;
    }

    @Override
    protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
        boolean hover = isMouseOver(mouseX, mouseY);
        drawRect(x, y, x + width, y + height, background(hover));
        drawCenteredString(Minecraft.getMinecraft().fontRenderer, fittedLabel(), x + width / 2, y + (height - 8) / 2, isEnabled() ? MTStyle.Flat.TEXT : MTStyle.Flat.TEXT_DISABLED);
    }

    private String fittedLabel() {
        String value = label.get();
        if (value == null || value.isEmpty()) return "";
        int maxWidth = Math.max(0, width - 4);
        if (font().getStringWidth(value) <= maxWidth) return value;
        if (maxWidth <= font().getStringWidth("...")) {
            return font().trimStringToWidth(value, maxWidth);
        }
        return font().trimStringToWidth(value, maxWidth - font().getStringWidth("...")) + "...";
    }

    private int background(boolean hover) {
        if (!isEnabled()) return MTStyle.Flat.BUTTON_DISABLED;
        boolean active = hover || toggled.getAsBoolean();
        if (style == ButtonStyle.PRIMARY) return active ? MTStyle.Flat.BUTTON_PRIMARY_HOVER : MTStyle.Flat.BUTTON_PRIMARY;
        if (style == ButtonStyle.CAUTION) return active ? MTStyle.Flat.BUTTON_CAUTION_HOVER : MTStyle.Flat.BUTTON_CAUTION;
        return active ? MTStyle.Flat.BUTTON_HOVER : MTStyle.Flat.BUTTON;
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!isVisible() || !isEnabled() || mouseButton != 0 || !isMouseOver(mouseX, mouseY)) return false;
        pressed = true;
        return true;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (!isVisible()) return false;
        if (pressed && state == 0) {
            pressed = false;
            if (isEnabled() && isMouseOver(mouseX, mouseY)) action.run();
            return true;
        }
        return false;
    }

    public static GuiButton translated(GuiElement<?> parent, String key) {
        return new GuiButton(parent, () -> I18n.format(key));
    }

    private enum ButtonStyle {
        NORMAL,
        PRIMARY,
        CAUTION
    }
}
