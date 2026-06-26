package net.creeperhost.minetogethercommunity.modulargui;

import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

public class GuiList<E> extends GuiElement<GuiList<E>> {

    private static final int BAR_WIDTH = 4;

    private final List<E> values = new ArrayList<>();
    private BiFunction<GuiElement<?>, E, GuiElement<?>> displayBuilder;
    private int rowHeight = 24;
    private int scroll;
    private boolean draggingScrollBar;
    private int scrollDragOffset;

    public GuiList(GuiElement<?> parent) {
        super(parent);
    }

    @Override
    public GuiList<E> setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        rebuildElements();
        return this;
    }

    public GuiList<E> setValues(List<E> values) {
        this.values.clear();
        this.values.addAll(values);
        rebuildElements();
        return this;
    }

    public GuiList<E> setDisplayBuilder(BiFunction<GuiElement<?>, E, GuiElement<?>> displayBuilder) {
        this.displayBuilder = displayBuilder;
        rebuildElements();
        return this;
    }

    public GuiList<E> setRowHeight(int rowHeight) {
        this.rowHeight = rowHeight;
        rebuildElements();
        return this;
    }

    public int hiddenSize() {
        return Math.max(0, values.size() * rowHeight - height);
    }

    public void rebuildElements() {
        scroll = Math.max(0, Math.min(scroll, hiddenSize()));
        children.clear();
        if (displayBuilder == null) return;
        int yPos = y - scroll;
        for (E value : values) {
            GuiElement<?> child = displayBuilder.apply(this, value);
            child.setBounds(x, yPos, width, rowHeight);
            yPos += rowHeight;
        }
    }

    public void setScroll(int scroll) {
        this.scroll = Math.max(0, Math.min(scroll, hiddenSize()));
        rebuildElements();
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!isVisible()) return;
        updateScrollDrag(mouseY);
        renderBackground(mouseX, mouseY, partialTicks);
        GuiClip.push(x, y, width, height);
        try {
            for (GuiElement<?> child : new ArrayList<GuiElement<?>>(children)) {
                if (child.y() + child.height() >= y && child.y() <= y + height) {
                    child.render(mouseX, mouseY, partialTicks);
                }
            }
        } finally {
            GuiClip.pop();
        }
        drawScrollBar(mouseX, mouseY);
        renderForeground(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!isVisible() || !isEnabled() || !isMouseOver(mouseX, mouseY)) return false;
        if (mouseButton == 0 && hiddenSize() > 0 && isOverScrollBar(mouseX, mouseY)) {
            startScrollDrag(mouseY);
            return true;
        }
        List<GuiElement<?>> copy = new ArrayList<GuiElement<?>>(children);
        Collections.reverse(copy);
        for (GuiElement<?> child : copy) {
            if (child.mouseClicked(mouseX, mouseY, mouseButton)) return true;
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
        List<GuiElement<?>> copy = new ArrayList<GuiElement<?>>(children);
        Collections.reverse(copy);
        for (GuiElement<?> child : copy) {
            if (child.mouseReleased(mouseX, mouseY, state)) return true;
        }
        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseInput(int mouseX, int mouseY, int dWheel) throws IOException {
        if (!isVisible() || !isEnabled() || !isMouseOver(mouseX, mouseY)) return false;
        if (dWheel != 0 && hiddenSize() > 0) {
            int previous = scroll;
            setScroll(scroll + (dWheel < 0 ? rowHeight * 2 : -rowHeight * 2));
            return scroll != previous;
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
            setScroll(0);
            return;
        }
        int handleTop = mouseY - scrollDragOffset;
        int localTop = Math.max(0, Math.min(metrics.travel, handleTop - metrics.trackTop));
        setScroll(localTop * metrics.maxScroll / metrics.travel);
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
        int max = hiddenSize();
        if (max <= 0 || height <= 0) return null;
        int barX = x + width - BAR_WIDTH - 1;
        int trackTop = y + 1;
        int trackHeight = Math.max(1, height - 2);
        int contentHeight = Math.max(height, values.size() * rowHeight);
        int handleHeight = Math.max(12, trackHeight * height / contentHeight);
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
