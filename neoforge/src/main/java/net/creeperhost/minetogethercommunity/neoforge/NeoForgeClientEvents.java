package net.creeperhost.minetogethercommunity.neoforge;

import net.creeperhost.minetogethercommunity.MineTogetherClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

public class NeoForgeClientEvents {

    public static void init(IEventBus eventBus) {
        eventBus.addListener(NeoForgeClientEvents::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(NeoForgeClientEvents::registerClientCommands);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        MineTogetherClientPlatformImpl.getKeyMappings().forEach(event::register);
    }

    private static void registerClientCommands(RegisterClientCommandsEvent event) {
        MineTogetherClient.registerClientCommands(event.getDispatcher());
    }
}
