package net.creeperhost.minetogethercommunity.gui;

import net.creeperhost.polylib.client.modulargui.elements.GuiElement;
import net.creeperhost.polylib.client.modulargui.lib.ForegroundRender;
import net.creeperhost.polylib.client.modulargui.lib.GuiRender;
import net.creeperhost.polylib.client.modulargui.lib.geometry.GuiParent;
import net.creeperhost.polylib.client.modulargui.sprite.Material;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3x2fStack;

/**
 * Created by brandon3055 on 15/06/2024
 */
public class LoadingSpinner extends GuiElement<LoadingSpinner> implements ForegroundRender {
    private int tick = 340;
    public int colour = 0xFFFFFF;

    public LoadingSpinner(@NotNull GuiParent parent) {
        super(parent);
    }

    @Override
    public void tick(double mouseX, double mouseY) {
        super.tick(mouseX, mouseY);
        tick++;
    }

    @Override
    public void renderInFront(GuiRender render, double mouseX, double mouseY, float partialTicks) {
        Material tex = MTTextures.get("spinner_dot");
        double size = Math.max(xSize(), ySize()) / 2;
        Matrix3x2fStack stack = render.pose();

        stack.pushMatrix();
        stack.translate((float) xCenter(), (float) yCenter());

        int segments = 10;
        for (int i = 1; i < segments + 1; i++) {
            stack.rotate((float) Math.toRadians(20 + tick + partialTicks));
            render.texRect(tex, -4D, -4D - size, 8D, 8D, (((int) ((i / (segments - 1D)) * 0xFF)) << 24) | colour);
        }

        stack.popMatrix();
    }
}
