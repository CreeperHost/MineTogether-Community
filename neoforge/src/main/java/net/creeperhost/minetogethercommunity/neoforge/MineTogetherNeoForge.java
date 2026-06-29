package net.creeperhost.minetogethercommunity.neoforge;

import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.neoforge.datagen.DataGenEventHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Created by covers1624 on 20/6/22.
 */
@Mod (MineTogether.MOD_ID)
public class MineTogetherNeoForge {

    public MineTogetherNeoForge(IEventBus eventBus) {
        MineTogether.init();
        DataGenEventHandler.init(eventBus);

        if (FMLEnvironment.getDist().isClient()) {
            NeoForge.EVENT_BUS.addListener(this::clientInit);
            NeoForgeClientEvents.init(eventBus);
        }
    }

    private void clientInit(ScreenEvent.Init.Post event) {
        MineTogetherChat.onScreenPostInit(event.getScreen());
    }
}
