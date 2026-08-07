package net.creeperhost.minetogethercommunity.neoforge;

import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.compat.Integration;
import net.creeperhost.minetogethercommunity.compat.ftbquests.FTBQuestsCompat;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

final class MineTogetherNeoForgeClient {

    static void init(IEventBus modEventBus) {
        Integration.runOptional("ftbquests", () -> FTBQuestsCompat::registerNeoForgeEvents);
        NeoForge.EVENT_BUS.addListener(MineTogetherNeoForgeClient::screenInitialized);
        NeoForgeClientEvents.init(modEventBus);
    }

    private static void screenInitialized(ScreenEvent.Init.Post event) {
        MineTogetherChat.onScreenPostInit(event.getScreen());
    }

    private MineTogetherNeoForgeClient() {
    }
}
