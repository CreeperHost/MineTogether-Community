package net.creeperhost.minetogethercommunity.neoforge;

import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.compat.Integration;
import net.creeperhost.minetogethercommunity.compat.ftbquests.FTBQuestsCompat;
import net.creeperhost.minetogethercommunity.connect.DedicatedServerConnect;
import net.creeperhost.minetogethercommunity.neoforge.datagen.DataGenEventHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Created by covers1624 on 20/6/22.
 */
@Mod (MineTogether.MOD_ID)
public class MineTogetherNeoForge {

    public MineTogetherNeoForge(IEventBus eventBus) {
        eventBus.addListener(NeoForgeEmoteNetworking::registerPayloads);
        MineTogether.init();
        DataGenEventHandler.init(eventBus);

        if (FMLEnvironment.getDist().isClient()) {
            Integration.runOptional("ftbquests", () -> FTBQuestsCompat::registerNeoForgeEvents);
            NeoForge.EVENT_BUS.addListener(this::clientInit);
            NeoForgeClientEvents.init(eventBus);
        } else {
            NeoForge.EVENT_BUS.addListener(this::serverStarted);
            NeoForge.EVENT_BUS.addListener(this::serverStopping);
            NeoForge.EVENT_BUS.addListener(this::playerLoggedIn);
        }
    }

    private void clientInit(ScreenEvent.Init.Post event) {
        MineTogetherChat.onScreenPostInit(event.getScreen());
    }

    private void serverStarted(ServerStartedEvent event) {
        DedicatedServerConnect.serverStarted(event.getServer());
    }

    private void serverStopping(ServerStoppingEvent event) {
        DedicatedServerConnect.serverStopping(event.getServer());
    }

    private void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            DedicatedServerConnect.playerJoined((ServerPlayer) event.getEntity());
        }
    }
}
