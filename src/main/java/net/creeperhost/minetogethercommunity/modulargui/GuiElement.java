package net.creeperhost.minetogethercommunity.modulargui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

@SuppressWarnings("unchecked")
public class GuiElement<T extends GuiElement<T>> extends Gui {

    protected final GuiElement<?> parent;
    protected final ModularGui gui;
    protected final List<GuiElement<?>> children = new ArrayList<>();
    private final List<RenderHook> renderHooks = new ArrayList<>();
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean visible = true;
    protected boolean enabled = true;
    private BooleanSupplier visibleSupplier;
    private BooleanSupplier enabledSupplier;

    public GuiElement(ModularGui gui) {
        this.parent = null;
        this.gui = gui;
    }

    public GuiElement(GuiElement<?> parent) {
        this.parent = parent;
        this.gui = parent.gui;
        parent.add(this);
    }

    public T setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        return (T) this;
    }

    public T setVisible(boolean visible) {
        this.visible = visible;
        this.visibleSupplier = null;
        return (T) this;
    }

    public T setVisible(BooleanSupplier visibleSupplier) {
        this.visible = true;
        this.visibleSupplier = visibleSupplier;
        return (T) this;
    }

    public T setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.enabledSupplier = null;
        return (T) this;
    }

    public T setEnabled(BooleanSupplier enabledSupplier) {
        this.enabled = true;
        this.enabledSupplier = enabledSupplier;
        return (T) this;
    }

    public T add(GuiElement<?> child) {
        children.add(child);
        return (T) this;
    }

    public T remove(GuiElement<?> child) {
        children.remove(child);
        return (T) this;
    }

    public T onRender(RenderHook hook) {
        if (hook != null) renderHooks.add(hook);
        return (T) this;
    }

    public void init() {
        for (GuiElement<?> child : children) child.init();
    }

    public void tick() {
        for (GuiElement<?> child : children) child.tick();
    }

    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!isVisible()) return;
        renderBackground(mouseX, mouseY, partialTicks);
        for (GuiElement<?> child : new ArrayList<>(children)) {
            child.render(mouseX, mouseY, partialTicks);
        }
        for (RenderHook hook : new ArrayList<>(renderHooks)) {
            hook.render(this, mouseX, mouseY, partialTicks);
        }
        renderForeground(mouseX, mouseY, partialTicks);
    }

    protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
    }

    protected void renderForeground(int mouseX, int mouseY, float partialTicks) {
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!isVisible() || !isEnabled()) return false;
        List<GuiElement<?>> copy = new ArrayList<>(children);
        Collections.reverse(copy);
        for (GuiElement<?> child : copy) {
            if (child.mouseClicked(mouseX, mouseY, mouseButton)) return true;
        }
        return false;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (!isVisible()) return false;
        List<GuiElement<?>> copy = new ArrayList<>(children);
        Collections.reverse(copy);
        for (GuiElement<?> child : copy) {
            if (child.mouseReleased(mouseX, mouseY, state)) return true;
        }
        return false;
    }

    public boolean mouseInput(int mouseX, int mouseY, int dWheel) throws IOException {
        if (!isVisible() || !isEnabled()) return false;
        List<GuiElement<?>> copy = new ArrayList<>(children);
        Collections.reverse(copy);
        for (GuiElement<?> child : copy) {
            if (child.mouseInput(mouseX, mouseY, dWheel)) return true;
        }
        return false;
    }

    public boolean keyTyped(char typedChar, int keyCode) throws IOException {
        if (!isVisible() || !isEnabled()) return false;
        List<GuiElement<?>> copy = new ArrayList<>(children);
        Collections.reverse(copy);
        for (GuiElement<?> child : copy) {
            if (child.keyTyped(typedChar, keyCode)) return true;
        }
        return false;
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public boolean isEnabled() {
        return enabled && (enabledSupplier == null || enabledSupplier.getAsBoolean());
    }

    public boolean isVisible() {
        return visible && (visibleSupplier == null || visibleSupplier.getAsBoolean());
    }

    public Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    public FontRenderer font() {
        return mc().fontRenderer;
    }

    public ModularGui getModularGui() {
        return gui;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
