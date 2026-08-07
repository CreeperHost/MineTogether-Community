package net.creeperhost.minetogethercommunity.forge;

import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

final class MineTogetherForgeClient {

    static void init(IEventBus modEventBus) {
        MinecraftForge.EVENT_BUS.addListener(MineTogetherForgeClient::screenInitialized);
        ForgeClientEvents.init(modEventBus);
    }

    private static void screenInitialized(ScreenEvent.Init.Post event) {
        // INIT_POST from Architectury does not fire on re-init, such as a resize.
        MineTogetherChat.onScreenPostInit(event.getScreen());
    }

    private MineTogetherForgeClient() {
    }
}
