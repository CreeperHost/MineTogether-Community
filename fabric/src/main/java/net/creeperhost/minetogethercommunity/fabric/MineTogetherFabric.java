package net.creeperhost.minetogethercommunity.fabric;

import dev.architectury.platform.Platform;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.connect.DedicatedServerConnect;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Created by covers1624 on 20/6/22.
 */
public class MineTogetherFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        MineTogether.init();

        if (Platform.getEnv() == EnvType.SERVER) {
            serverInit();
        }
    }

    private void serverInit() {
        ServerLifecycleEvents.SERVER_STARTED.register(DedicatedServerConnect::serverStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(DedicatedServerConnect::serverStopping);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> DedicatedServerConnect.playerJoined(handler.player));
    }
}
