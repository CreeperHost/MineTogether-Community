package net.creeperhost.minetogethercommunity.modulargui;

import java.util.function.Supplier;

public class OptionDialog extends GuiDialog {

    public OptionDialog(GuiElement<?> parent, Supplier<String> title, Supplier<String> message,
                        Supplier<String> confirmLabel, Runnable confirm,
                        Supplier<String> cancelLabel, Runnable cancel) {
        super(parent, title, message);
        addButton(confirmLabel, confirm);
        addButton(cancelLabel, cancel == null ? new Runnable() {
            @Override
            public void run() {
                setVisible(false);
            }
        } : cancel);
    }
}
