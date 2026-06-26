package net.creeperhost.minetogethercommunity.modulargui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ModularGui {

    private final ModularGuiScreen screen;
    private final GuiProvider provider;
    private final GuiScreen parentScreen;
    private final List<Runnable> tickHandlers = new ArrayList<>();
    private GuiElement<?> root;
    private ITextComponent title = new TextComponentString("");
    private boolean renderBackground = true;
    private boolean pauseScreen;

    public ModularGui(ModularGuiScreen screen, GuiProvider provider, GuiScreen parentScreen) {
        this.screen = screen;
        this.provider = provider;
        this.parentScreen = parentScreen;
    }

    public void init(int width, int height) {
        tickHandlers.clear();
        root = provider.createRootElement(this);
        root.setBounds(0, 0, width, height);
        root.init();
    }

    public void tick() {
        provider.tick(this);
        for (Runnable handler : new ArrayList<>(tickHandlers)) {
            handler.run();
        }
        if (root != null) root.tick();
    }

    public void render(int mouseX, int mouseY, float partialTicks) {
        if (root != null) root.render(mouseX, mouseY, partialTicks);
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        return root != null && root.mouseClicked(mouseX, mouseY, mouseButton);
    }

    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        return root != null && root.mouseReleased(mouseX, mouseY, state);
    }

    public boolean mouseInput(int mouseX, int mouseY, int dWheel) throws IOException {
        return root != null && root.mouseInput(mouseX, mouseY, dWheel);
    }

    public boolean keyTyped(char typedChar, int keyCode) throws IOException {
        return root != null && root.keyTyped(typedChar, keyCode);
    }

    public void onTick(Runnable handler) {
        tickHandlers.add(handler);
    }

    public Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    public GuiElement<?> getRoot() {
        return root;
    }

    public ModularGuiScreen getScreen() {
        return screen;
    }

    public GuiProvider getProvider() {
        return provider;
    }

    public GuiScreen getParentScreen() {
        return parentScreen;
    }

    public ITextComponent getGuiTitle() {
        return title;
    }

    public void setGuiTitle(ITextComponent title) {
        this.title = title;
    }

    public boolean rendersBackground() {
        return renderBackground;
    }

    public void renderScreenBackground(boolean renderBackground) {
        this.renderBackground = renderBackground;
    }

    public boolean isPauseScreen() {
        return pauseScreen;
    }

    public void setPauseScreen(boolean pauseScreen) {
        this.pauseScreen = pauseScreen;
    }
}
