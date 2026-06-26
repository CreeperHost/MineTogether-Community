package net.creeperhost.minetogethercommunity.modulargui;

import net.creeperhost.minetogethercommunity.gui.chat.MTStyle;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class GuiSlider extends GuiElement<GuiSlider> {

    private Supplier<Double> getter = () -> 0D;
    private Consumer<Double> setter = value -> {};
    private boolean dragging;

    public GuiSlider(GuiElement<?> parent) {
        super(parent);
    }

    public GuiSlider bindValue(Supplier<Double> getter, Consumer<Double> setter) {
        this.getter = getter;
        this.setter = setter;
        return this;
    }

    @Override
    protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
        if (dragging) {
            setFromMouse(mouseX);
        }
        boolean hover = isMouseOver(mouseX, mouseY) || dragging;
        drawRect(x, y, x + width, y + height, hover ? 0x80505050 : 0x20505050);
        int knobWidth = Math.min(14, Math.max(6, height + 2));
        int knob = x + (int) (Math.max(0, Math.min(1, getter.get())) * Math.max(0, width - knobWidth));
        drawRect(knob, y, knob + knobWidth, y + height, hover ? 0xFFFFFFFF : 0xCCFFFFFF);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!isVisible() || !isEnabled() || mouseButton != 0 || !isMouseOver(mouseX, mouseY)) return false;
        dragging = true;
        setFromMouse(mouseX);
        return true;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (!isVisible()) return false;
        if (state == 0 && dragging) {
            setFromMouse(mouseX);
            dragging = false;
            return true;
        }
        return false;
    }

    private void setFromMouse(int mouseX) {
        int knobWidth = Math.min(14, Math.max(6, height + 2));
        int range = Math.max(1, width - knobWidth);
        setter.accept(Math.max(0D, Math.min(1D, (mouseX - x - knobWidth / 2D) / (double) range)));
    }
}
