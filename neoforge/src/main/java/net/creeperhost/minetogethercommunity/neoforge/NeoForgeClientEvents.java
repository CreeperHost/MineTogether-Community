package net.creeperhost.minetogethercommunity.neoforge;

import net.creeperhost.minetogethercommunity.gui.MTTextures;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

public class NeoForgeClientEvents {

    public static void init(IEventBus eventBus) {
        eventBus.addListener(NeoForgeClientEvents::registerReloadListeners);
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(MTTextures.getAtlasHolder());
    }
}
