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
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        Keyboard.enableRepeatEvents(true);
        if (modularGui != null) modularGui.tick();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (modularGui == null || modularGui.rendersBackground()) {
            drawDefaultBackground();
        }
        if (modularGui != null) {
            modularGui.render(mouseX, mouseY, partialTicks);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (modularGui == null || !modularGui.mouseClicked(mouseX, mouseY, mouseButton)) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (modularGui == null || !modularGui.mouseReleased(mouseX, mouseY, state)) {
            super.mouseReleased(mouseX, mouseY, state);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0 && modularGui != null && modularGui.mouseInput(mouseX, mouseY, dWheel)) {
            return;
        }
        super.handleMouseInput();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (modularGui == null || !modularGui.keyTyped(typedChar, keyCode)) {
            super.keyTyped(typedChar, keyCode);
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
