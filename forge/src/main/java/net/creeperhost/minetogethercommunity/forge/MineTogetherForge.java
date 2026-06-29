package net.creeperhost.minetogethercommunity.forge;

import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.connect.DedicatedServerConnect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.client.event.ScreenEvent;
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
            MinecraftForge.EVENT_BUS.addListener(this::clientInit);
            ForgeClientEvents.init(eventBus);
        } else {
            MinecraftForge.EVENT_BUS.addListener(this::serverStarted);
            MinecraftForge.EVENT_BUS.addListener(this::serverStopping);
            MinecraftForge.EVENT_BUS.addListener(this::playerLoggedIn);
        }
    }

    private void clientInit(ScreenEvent.Init.Post event) {
        //We need this because INIT_POST from architectury only works in the initial init event.
        //It does not fire on re-init, e.g. when window is resized. I would consider this a bug in architectury.
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
