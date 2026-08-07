package net.creeperhost.minetogethercommunity.neoforge;

import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.compat.Integration;
import net.creeperhost.minetogethercommunity.neoforge.compat.pausemenu.PauseMenuIntegration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

final class MineTogetherNeoForgeClient {

    static void init(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(MineTogetherNeoForgeClient::screenInitialized);
        NeoForgeClientEvents.init(modEventBus);
        Integration.runOptional("ftbpmapi", () -> PauseMenuIntegration::init);
    }

    private static void screenInitialized(ScreenEvent.Init.Post event) {
        // INIT_POST from Architectury does not fire on re-init, such as a resize.
        MineTogetherChat.onScreenPostInit(event.getScreen());
    }

    private MineTogetherNeoForgeClient() {
    }
}
