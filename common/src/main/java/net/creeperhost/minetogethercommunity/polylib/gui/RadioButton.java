package net.creeperhost.minetogethercommunity.polylib.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A simple button which remains 'active' once clicked, capable of being linked
 * to multiple other buttons to clear their 'active' state.
 * <p>
 * Created by covers1624 on 5/8/22.
 */
public class RadioButton extends Button {

    private final List<OnPress> actions = new LinkedList<>();

    private float textScale = 1.0F;
    private boolean verticalText = false;
    private int autoScaleMargins = -1;

    private Supplier<Boolean> selected = () -> false;
    private Runnable onRelease = () -> {
    };

    public RadioButton(int x, int y, int width, int height, Component text) {
        super(x, y, width, height, text, e -> {
        }, Button.DEFAULT_NARRATION);
    }

    public void updateBounds(int x, int y, int width, int height) {
        this.setX(x);
        this.setY(y);
        this.setWidth(width);
        this.height = height; //Really??? There's a setter for everything except height?...
    }

    public RadioButton onRelease(Runnable onRelease) {
        this.onRelease = onRelease;
        return this;
    }

    /**
     * When enabled text will automatically be scaled to with the button bounds with the specified margins at each end.
     * This will only scale down, and only if required.
     * If text scale is set then that scale will be used as the "target" scale but we will scale down if required.
     *
     * @param autoScaleMargins Minimum required space at ether end of the text, -1 to disable auto-scale.
     * @return The same button.
     */
    public RadioButton withAutoScaleText(int autoScaleMargins) {
        this.autoScaleMargins = autoScaleMargins;
        return this;
    }

    /**
     * Sets the scale for the text.
     *
     * @param scale The text scale.
     * @return The same button.
     */
    public RadioButton withTextScale(float scale) {
        textScale = scale;
        return this;
    }

    /**
     * Sets if this button should render the text flipped.
     *
     * @return Render text vertically.
     */
    public RadioButton withVerticalText() {
        verticalText = true;
        return this;
    }

    /**
     * Add an action callback when this button is pressed.
     *
     * @param action The action callback.
     * @return The same button.
     */
    public RadioButton onPressed(OnPress action) {
        actions.add(action);
        return this;
    }

    /**
     * Use to control the "radio" part of the radio button.
     *
     * @param selected A suppler that returns true when the option this button represents is currently selected.
     */
    public RadioButton selectedSupplier(Supplier<Boolean> selected) {
        this.selected = selected;
        return this;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (selected.get()) return;
        for (OnPress action : actions) {
            action.onPress(this);
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        onRelease.run();
        return super.mouseReleased(event);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        int textColor = 0xFFFFFFFF;
        int fillColor = 0x64202020;
        if (isHovered || isPressed()) {
            textColor = 0xFFFFFFA0;
            fillColor = 0x80000000;
        }
        graphics.fill(getX(), getY(), getX() + width, getY() + height, fillColor);

        Font font = Minecraft.getInstance().font;
        float scale = textScale;
        double lHeight = font.lineHeight * scale;
        double lWidth = font.width(getMessage()) * scale;

        int autoWidth = (verticalText ? height : width) - (autoScaleMargins * 2);
        if (autoScaleMargins > -1 && lWidth > autoWidth) {
            scale *= autoWidth / lWidth;
            lHeight = font.lineHeight * scale;
            lWidth = font.width(getMessage()) * scale;
        }

        graphics.pose().pushMatrix();
        if (verticalText) {
            graphics.pose().translate((float) (getX() + lHeight + (width / 2D) - (lHeight / 2D)), (float) (getY() + (height / 2D) - (lWidth / 2D)));
            graphics.pose().rotate((float) Math.toRadians(90F));
        } else {
            graphics.pose().translate((float) (getX() + (width / 2D) - (lWidth / 2D)), (float) (getY() + (height / 2D) - (lHeight / 2D)));
        }

        graphics.pose().scale(scale, scale);

        graphics.text(font, getMessage(), 0, 0, textColor);
        graphics.pose().popMatrix();
    }

    public boolean isPressed() {
        return selected.get();
    }
}
