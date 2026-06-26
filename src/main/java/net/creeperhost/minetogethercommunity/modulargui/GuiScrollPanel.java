package net.creeperhost.minetogethercommunity.modulargui;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class GuiScrollPanel extends GuiElement<GuiScrollPanel> {

    private static final int BAR_WIDTH = 4;

    private int scroll;
    private int contentBottom;
    private boolean draggingScrollBar;
    private int scrollDragOffset;

    public GuiScrollPanel(GuiElement<?> parent) {
        super(parent);
    }

    public GuiScrollPanel setContentBottom(int contentBottom) {
        this.contentBottom = contentBottom;
        clampScroll();
        return this;
    }

    private int maxScroll() {
        return Math.max(0, contentBottom - (y + height));
    }

    private void clampScroll() {
        int max = maxScroll();
        if (scroll < 0) scroll = 0;
        if (scroll > max) scroll = max;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!isVisible()) return;
        clampScroll();
        updateScrollDrag(mouseY);
        renderBackground(mouseX, mouseY, partialTicks);
        GuiClip.push(x, y, width, height);
        GlStateManager.pushMatrix();
        GuiClip.pushOffset(0, -scroll);
        try {
            GlStateManager.translate(0.0F, -scroll, 0.0F);
            for (GuiElement<?> child : new ArrayList<>(children)) {
                child.render(mouseX, mouseY + scroll, partialTicks);
            }
        } finally {
            GuiClip.popOffset();
            GlStateManager.popMatrix();
            GuiClip.pop();
        }
        drawScrollBar(mouseX, mouseY);
        renderForeground(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!isVisible() || !isEnabled() || !isMouseOver(mouseX, mouseY)) return false;
        if (mouseButton == 0 && maxScroll() > 0 && isOverScrollBar(mouseX, mouseY)) {
            startScrollDrag(mouseY);
            return true;
        }
        java.util.List<GuiElement<?>> copy = new ArrayList<>(children);
        Collections.reverse(copy);
        for (GuiElement<?> child : copy) {
            if (child.mouseClicked(mouseX, mouseY + scroll, mouseButton)) return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (!isVisible()) return false;
        if (state == 0 && draggingScrollBar) {
            draggingScrollBar = false;
            return true;
        }
        java.util.List<GuiElement<?>> copy = new ArrayList<>(children);
        Collections.reverse(copy);
        for (GuiElement<?> child : copy) {
            if (child.mouseReleased(mouseX, mouseY + scroll, state)) return true;
        }
        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseInput(int mouseX, int mouseY, int dWheel) throws IOException {
        if (!isVisible() || !isEnabled() || !isMouseOver(mouseX, mouseY)) return false;
        java.util.List<GuiElement<?>> copy = new ArrayList<>(children);
        Collections.reverse(copy);
        for (GuiElement<?> child : copy) {
            boolean overNestedScroll = dWheel != 0
                    && (child instanceof GuiList || child instanceof GuiScrollPanel)
                    && child.isMouseOver(mouseX, mouseY + scroll);
            if (child.mouseInput(mouseX, mouseY + scroll, dWheel)) return true;
            if (overNestedScroll) return true;
        }
        if (dWheel != 0 && maxScroll() > 0) {
            int previous = scroll;
            scroll += dWheel < 0 ? 20 : -20;
            clampScroll();
            if (scroll != previous) return true;
        }
        return false;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) throws IOException {
        if (!isVisible() || !isEnabled()) return false;
        java.util.List<GuiElement<?>> copy = new ArrayList<>(children);
        Collections.reverse(copy);
        for (GuiElement<?> child : copy) {
            if (child.keyTyped(typedChar, keyCode)) return true;
        }
        return false;
    }

    private void drawScrollBar(int mouseX, int mouseY) {
        ScrollBarMetrics metrics = scrollBarMetrics();
        if (metrics == null) return;
        boolean hover = isOverScrollBar(mouseX, mouseY) || draggingScrollBar;
        drawRect(metrics.barX, metrics.trackTop, metrics.barX + BAR_WIDTH, metrics.trackTop + metrics.trackHeight, hover ? 0x80505050 : 0x20505050);
        drawRect(metrics.barX, metrics.handleTop, metrics.barX + BAR_WIDTH, metrics.handleTop + metrics.handleHeight, hover ? 0xFFFFFFFF : 0x88FFFFFF);
    }

    private void startScrollDrag(int mouseY) {
        ScrollBarMetrics metrics = scrollBarMetrics();
        if (metrics == null) return;
        draggingScrollBar = true;
        if (mouseY >= metrics.handleTop && mouseY <= metrics.handleTop + metrics.handleHeight) {
            scrollDragOffset = mouseY - metrics.handleTop;
        } else {
            scrollDragOffset = metrics.handleHeight / 2;
            setScrollFromMouse(mouseY, metrics);
        }
    }

    private void updateScrollDrag(int mouseY) {
        if (!draggingScrollBar) return;
        if (!Mouse.isButtonDown(0)) {
            draggingScrollBar = false;
            return;
        }
        ScrollBarMetrics metrics = scrollBarMetrics();
        if (metrics != null) {
            setScrollFromMouse(mouseY, metrics);
        }
    }

    private void setScrollFromMouse(int mouseY, ScrollBarMetrics metrics) {
        if (metrics.travel <= 0) {
            scroll = 0;
            return;
        }
        int handleTop = mouseY - scrollDragOffset;
        int localTop = Math.max(0, Math.min(metrics.travel, handleTop - metrics.trackTop));
        scroll = localTop * metrics.maxScroll / metrics.travel;
        clampScroll();
    }

    private boolean isOverScrollBar(int mouseX, int mouseY) {
        ScrollBarMetrics metrics = scrollBarMetrics();
        return metrics != null
                && mouseX >= metrics.barX - 1
                && mouseX < metrics.barX + BAR_WIDTH + 1
                && mouseY >= y
                && mouseY < y + height;
    }

    private ScrollBarMetrics scrollBarMetrics() {
        int max = maxScroll();
        if (max <= 0 || height <= 0) return null;
        int barX = x + width - BAR_WIDTH - 1;
        int trackTop = y + 2;
        int trackHeight = Math.max(1, height - 4);
        int contentHeight = Math.max(height, contentBottom - y);
        int handleHeight = Math.max(18, trackHeight * height / contentHeight);
        if (handleHeight > trackHeight) handleHeight = trackHeight;
        int travel = Math.max(0, trackHeight - handleHeight);
        int handleTop = trackTop + travel * scroll / max;
        return new ScrollBarMetrics(max, barX, trackTop, trackHeight, handleHeight, travel, handleTop);
    }

    private static class ScrollBarMetrics {
        private final int maxScroll;
        private final int barX;
        private final int trackTop;
        private final int trackHeight;
        private final int handleHeight;
        private final int travel;
        private final int handleTop;

        private ScrollBarMetrics(int maxScroll, int barX, int trackTop, int trackHeight, int handleHeight, int travel, int handleTop) {
            this.maxScroll = maxScroll;
            this.barX = barX;
            this.trackTop = trackTop;
            this.trackHeight = trackHeight;
            this.handleHeight = handleHeight;
            this.travel = travel;
            this.handleTop = handleTop;
        }
    }

}
