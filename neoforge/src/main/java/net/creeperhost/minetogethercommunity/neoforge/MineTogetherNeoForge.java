package net.creeperhost.minetogethercommunity.neoforge;

import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.compat.Integration;
import net.creeperhost.minetogethercommunity.neoforge.compat.pausemenu.PauseMenuIntegration;
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

        if (FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.addListener(this::clientInit);
            NeoForgeClientEvents.init(eventBus);
            Integration.runOptional("ftbpmapi", () -> PauseMenuIntegration::init);
        }
    }

    private void clientInit(ScreenEvent.Init.Post event) {
        //We need this because INIT_POST from architectury only works in the initial init event.
        //It does not fire on re-init, e.g. when window is resized. I would consider this a bug in architectury.
        MineTogetherChat.onScreenPostInit(event.getScreen());
    }
}
