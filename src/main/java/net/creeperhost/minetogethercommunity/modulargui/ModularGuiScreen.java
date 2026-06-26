package net.creeperhost.minetogethercommunity.modulargui;

import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;

public class ModularGuiScreen extends GuiScreen {

    private final GuiProvider provider;
    private final GuiScreen parentScreen;
    private ModularGui modularGui;

    public ModularGuiScreen(GuiProvider provider, GuiScreen parentScreen) {
        this.provider = provider;
        this.parentScreen = parentScreen;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        modularGui = new ModularGui(this, provider, parentScreen);
        modularGui.init(width, height);
    }

    @Override
    public void onGuiClosed() {
        GuiClip.clear();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        Keyboard.enableRepeatEvents(true);
        if (modularGui != null) modularGui.tick();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GuiClip.clear();
        try {
            if (modularGui == null || modularGui.rendersBackground()) {
                drawDefaultBackground();
            }
            if (modularGui != null) {
                modularGui.render(mouseX, mouseY, partialTicks);
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
        } finally {
            GuiClip.clear();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        try {
            if (modularGui == null || !modularGui.mouseClicked(mouseX, mouseY, mouseButton)) {
                super.mouseClicked(mouseX, mouseY, mouseButton);
            }
        } catch (IOException ex) {
            throw new RuntimeException("Error handling modular GUI mouse click", ex);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (modularGui == null || !modularGui.mouseReleased(mouseX, mouseY, state)) {
            super.mouseReleased(mouseX, mouseY, state);
        }
    }

    @Override
    public void handleMouseInput() {
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        int dWheel = Mouse.getEventDWheel();
        try {
            if (dWheel != 0 && modularGui != null && modularGui.mouseInput(mouseX, mouseY, dWheel)) {
                return;
            }
            super.handleMouseInput();
        } catch (IOException ex) {
            throw new RuntimeException("Error handling modular GUI mouse input", ex);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        try {
            if (modularGui == null || !modularGui.keyTyped(typedChar, keyCode)) {
                super.keyTyped(typedChar, keyCode);
            }
        } catch (IOException ex) {
            throw new RuntimeException("Error handling modular GUI key input", ex);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return modularGui != null && modularGui.isPauseScreen();
    }

    public ModularGui getModularGui() {
        return modularGui;
    }

    public GuiScreen getParentScreen() {
        return parentScreen;
    }
}
