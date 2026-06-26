package net.creeperhost.minetogethercommunity.modulargui;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.text.ITextComponent;

import java.util.List;
import java.util.function.Supplier;

public class GuiText extends GuiElement<GuiText> {

    private Supplier<String> text;
    private int color = 0xFFFFFF;
    private boolean centered;
    private boolean rightAligned;
    private boolean wrap;
    private float scale = 1.0F;
    private Supplier<String> tooltip;

    public GuiText(GuiElement<?> parent, ITextComponent text) {
        this(parent, () -> text.getFormattedText());
    }

    public GuiText(GuiElement<?> parent, Supplier<String> text) {
        super(parent);
        this.text = text;
    }

    public GuiText setColor(int color) {
        this.color = color;
        return this;
    }

    public GuiText centered() {
        centered = true;
        rightAligned = false;
        return this;
    }

    public GuiText rightAligned() {
        rightAligned = true;
        centered = false;
        return this;
    }

    public GuiText setWrap(boolean wrap) {
        this.wrap = wrap;
        return this;
    }

    public GuiText setScale(float scale) {
        this.scale = Math.max(0.25F, scale);
        return this;
    }

    public GuiText setTextSupplier(Supplier<String> text) {
        this.text = text;
        return this;
    }

    public GuiText setTooltip(Supplier<String> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    @Override
    protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
        String value = wrap ? currentText() : fittedText();
        if (scale != 1.0F) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, 0.0F);
            GlStateManager.scale(scale, scale, 1.0F);
            int scaledWidth = Math.max(1, Math.round(width / scale));
            drawText(value, 0, 0, scaledWidth);
            GlStateManager.popMatrix();
            return;
        }
        drawText(value, x, y, width);
    }

    @Override
    protected void renderForeground(int mouseX, int mouseY, float partialTicks) {
        if (tooltip == null || !isMouseOver(mouseX, mouseY)) return;
        String value = tooltip.get();
        TooltipRenderer.draw(this, font(), value, mouseX, mouseY, gui.getScreen().width, gui.getScreen().height);
    }

    private void drawText(String value, int drawX, int drawY, int drawWidth) {
        if (wrap) {
            List<String> lines = font().listFormattedStringToWidth(value, Math.max(1, drawWidth));
            int lineLimit = visibleLineLimit();
            int visibleLines = Math.min(lines.size(), lineLimit);
            for (int i = 0; i < visibleLines; i++) {
                int lineY = drawY + i * font().FONT_HEIGHT;
                String line = i == visibleLines - 1 && lines.size() > visibleLines
                        ? fitWithEllipsis(lines.get(i), drawWidth) : lines.get(i);
                if (centered) {
                    drawCenteredString(font(), line, drawX + drawWidth / 2, lineY, color);
                } else if (rightAligned) {
                    font().drawStringWithShadow(line, drawX + drawWidth - font().getStringWidth(line), lineY, color);
                } else {
                    font().drawStringWithShadow(line, drawX, lineY, color);
                }
            }
            return;
        }
        if (centered) {
            drawCenteredString(font(), value, drawX + drawWidth / 2, drawY, color);
        } else if (rightAligned) {
            font().drawStringWithShadow(value, drawX + drawWidth - font().getStringWidth(value), drawY, color);
        } else {
            font().drawStringWithShadow(value, drawX, drawY, color);
        }
    }

    private String fittedText() {
        String value = currentText();
        if (value == null || value.isEmpty() || width <= 0) return value == null ? "" : value;
        int fitWidth = scale == 1.0F ? width : Math.max(1, Math.round(width / scale));
        if (font().getStringWidth(value) <= fitWidth) return value;
        if (fitWidth <= font().getStringWidth("...")) {
            return font().trimStringToWidth(value, fitWidth);
        }
        return font().trimStringToWidth(value, fitWidth - font().getStringWidth("...")) + "...";
    }

    private String fitWithEllipsis(String value, int fitWidth) {
        if (font().getStringWidth(value) <= fitWidth) return value;
        int dots = font().getStringWidth("...");
        if (fitWidth <= dots) return font().trimStringToWidth(value, fitWidth);
        return font().trimStringToWidth(value, fitWidth - dots) + "...";
    }

    private int visibleLineLimit() {
        int scaledHeight = scale == 1.0F ? height : Math.max(1, Math.round(height / scale));
        return Math.max(1, scaledHeight / Math.max(1, font().FONT_HEIGHT));
    }

    private String currentText() {
        String value = text.get();
        return value == null ? "" : value;
    }

    public int measuredHeight() {
        if (!wrap || width <= 0) return Math.max(8, Math.round(font().FONT_HEIGHT * scale));
        int fitWidth = scale == 1.0F ? width : Math.max(1, Math.round(width / scale));
        int lines = Math.max(1, font().listFormattedStringToWidth(currentText(), fitWidth).size());
        return Math.max(8, Math.round(lines * font().FONT_HEIGHT * scale));
    }
}
