package net.creeperhost.minetogethercommunity.forge;

import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.connect.DedicatedServerConnect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/**
 * Created by covers1624 on 20/6/22.
 */
@Mod (MineTogether.MOD_ID)
public class MineTogetherForge {

    public MineTogetherForge() {
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
        MineTogether.init();

        if (FMLEnvironment.dist.isClient()) {
            MineTogetherForgeClient.init(eventBus);
        } else {
            MinecraftForge.EVENT_BUS.addListener(this::serverStarted);
            MinecraftForge.EVENT_BUS.addListener(this::serverStopping);
            MinecraftForge.EVENT_BUS.addListener(this::playerLoggedIn);
        }
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
