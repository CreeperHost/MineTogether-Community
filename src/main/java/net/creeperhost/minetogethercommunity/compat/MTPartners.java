package net.creeperhost.minetogethercommunity.compat;

import net.creeperhost.minetogethercommunity.modulargui.ModularGui;
import net.creeperhost.minetogethercommunity.orderform.OrderGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import cpw.mods.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;

public final class MTPartners {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Partners");
    private static final String PARTNERS_MOD_ID = "minetogetherpartners";
    private static final String PARTNERS_ORDER_SCREEN = "net.creeperhost.minetogetherpartners.orderform.OrderGui$Screen";

    private MTPartners() {
    }

    public static void openOrderUI(ModularGui gui) {
        openOrderUI(gui, true);
    }

    public static void openOrderUI(ModularGui gui, boolean suggestWorld) {
        gui.mc().displayGuiScreen(createOrderScreen(gui.getScreen(), suggestWorld));
    }

    public static void openOrderUI(GuiScreen parent, boolean suggestWorld) {
        Minecraft.getMinecraft().displayGuiScreen(createOrderScreen(parent, suggestWorld));
    }

    private static GuiScreen createOrderScreen(GuiScreen parent, boolean suggestWorld) {
        GuiScreen partnerScreen = createPartnerOrderScreen(parent, suggestWorld);
        return partnerScreen == null ? new OrderGui.Screen(parent, suggestWorld) : partnerScreen;
    }

    private static GuiScreen createPartnerOrderScreen(GuiScreen parent, boolean suggestWorld) {
        if (!Loader.isModLoaded(PARTNERS_MOD_ID)) return null;
        try {
            Class<?> screenClass = Class.forName(PARTNERS_ORDER_SCREEN);
            try {
                Constructor<?> constructor = screenClass.getConstructor(GuiScreen.class, boolean.class);
                return (GuiScreen) constructor.newInstance(parent, suggestWorld);
            } catch (NoSuchMethodException ignored) {
                Constructor<?> constructor = screenClass.getConstructor(GuiScreen.class);
                return (GuiScreen) constructor.newInstance(parent);
            }
        } catch (Throwable ex) {
            LOGGER.warn("minetogetherpartners is loaded but its order UI could not be opened; using bundled order UI.", ex);
            return null;
        }
    }
}
