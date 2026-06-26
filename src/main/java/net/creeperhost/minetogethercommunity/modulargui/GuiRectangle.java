package net.creeperhost.minetogethercommunity.modulargui;

public class GuiRectangle extends GuiElement<GuiRectangle> {

    private int color;

    public GuiRectangle(GuiElement<?> parent, int color) {
        super(parent);
        this.color = color;
    }

    public GuiRectangle color(int color) {
        this.color = color;
        return this;
    }

    @Override
    protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
        drawRect(x, y, x + width, y + height, color);
    }
}
