package net.creeperhost.minetogethercommunity.gui;

import net.creeperhost.minetogethercommunity.gui.chat.MTStyle;
import net.creeperhost.minetogethercommunity.modulargui.TooltipRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public class IconButton extends GuiButton {

    private static final int BUTTON_SIZE = 20;
    private static final int ICON_SIZE = 20;
    private static final int SHEET_SIZE = 256;

    private final int index;
    private final ResourceLocation sheet;
    private final String tooltip;
    private final boolean single;

    public IconButton(int id, int x, int y, int index, ResourceLocation sheet, String tooltip) {
        super(id, x, y, BUTTON_SIZE, BUTTON_SIZE, "");
        this.index = index;
        this.sheet = sheet;
        this.tooltip = tooltip;
        this.single = false;
    }

    public IconButton(int id, int x, int y, int width, int height, ResourceLocation sheet, String tooltip) {
        super(id, x, y, width, height, "");
        this.index = 0;
        this.sheet = sheet;
        this.tooltip = tooltip;
        this.single = true;
    }

    public void updateBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;
        hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        mc.getTextureManager().bindTexture(sheet);
        GlStateManager.enableBlend();
        GlStateManager.color(1F, 1F, 1F, enabled ? 1F : 0.45F);
        if (single) {
            drawRect(x, y, x + width, y + height, hovered ? 0x64202020 : 0x80000000);
            drawModalRectWithCustomSizedTexture(x, y, 0, 0, width, height, width, height);
        } else {
            int u = index * ICON_SIZE;
            int v = !enabled ? ICON_SIZE * 2 : hovered ? ICON_SIZE : 0;
            drawModalRectWithCustomSizedTexture(x, y, u, v, width, height, SHEET_SIZE, SHEET_SIZE);
        }
        GlStateManager.color(1F, 1F, 1F, 1F);
        if (hovered && tooltip != null && !tooltip.isEmpty()) {
            TooltipRenderer.draw(this, mc.fontRenderer, tooltip, mouseX, mouseY, mc.currentScreen.width, mc.currentScreen.height);
        }
    }
}
