package net.creeperhost.minetogethercommunity.compat;

import net.creeperhost.polylib.client.modulargui.ModularGui;
import net.minecraft.client.gui.screens.Screen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MTPartners
{
    private static final Logger LOGGER = LogManager.getLogger();

    public static boolean openOrderUI(ModularGui gui) {
        try {
            Class<?> screenClass = Class.forName("net.creeperhost.minetogetherpartners.orderform.OrderGui$Screen");
            Screen screen = (Screen) screenClass.getConstructor(Screen.class, boolean.class).newInstance(gui.getScreen(), true);
            gui.mc().setScreen(screen);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.error("Failed to open minetogetherpartners order form.", e);
            return false;
        }
    }
}
