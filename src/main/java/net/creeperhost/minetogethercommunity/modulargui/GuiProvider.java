package net.creeperhost.minetogethercommunity.modulargui;

public interface GuiProvider {

    GuiElement<?> createRootElement(ModularGui gui);

    default void tick(ModularGui gui) {
    }
}
