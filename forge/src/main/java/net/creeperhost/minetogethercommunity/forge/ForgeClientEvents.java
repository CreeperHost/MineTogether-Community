package net.creeperhost.minetogethercommunity.forge;

import net.creeperhost.minetogethercommunity.gui.MTTextures;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;

public class ForgeClientEvents {

    public static void init(IEventBus eventBus) {
        eventBus.addListener(ForgeClientEvents::registerReloadListeners);
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(MTTextures.getAtlasHolder());
    }
}
