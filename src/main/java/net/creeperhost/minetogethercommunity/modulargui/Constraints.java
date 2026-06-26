package net.creeperhost.minetogethercommunity.modulargui;

public final class Constraints {

    private Constraints() {
    }

    public static void bind(GuiElement<?> child, GuiElement<?> parent) {
        child.setBounds(parent.x(), parent.y(), parent.width(), parent.height());
    }
}
