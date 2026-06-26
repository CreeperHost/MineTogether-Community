package net.creeperhost.minetogethercommunity.modulargui;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public class GuiTexture extends GuiElement<GuiTexture> {

    private final ResourceLocation texture;
    private int textureWidth = 256;
    private int textureHeight = 256;

    public GuiTexture(GuiElement<?> parent, ResourceLocation texture) {
        super(parent);
        this.texture = texture;
    }

    public GuiTexture textureSize(int textureWidth, int textureHeight) {
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        return this;
    }

    @Override
    protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
        mc().getTextureManager().bindTexture(texture);
        GlStateManager.color(1, 1, 1, 1);
        Gui.drawScaledCustomSizeModalRect(x, y, 0, 0, textureWidth, textureHeight, width, height, textureWidth, textureHeight);
    }
}
