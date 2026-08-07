package net.creeperhost.minetogethercommunity.fabric;

import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.connect.DedicatedServerConnect;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteNetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Created by covers1624 on 20/6/22.
 */
public class MineTogetherFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        MineTogether.init();
        FabricEmoteNetworking.init();

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            serverInit();
        }
    }

    private void serverInit() {
        ServerLifecycleEvents.SERVER_STARTED.register(DedicatedServerConnect::serverStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(DedicatedServerConnect::serverStopping);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            DedicatedServerConnect.playerJoined(handler.player);
            EmoteNetworking.syncPersistentEmotes(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                EmoteNetworking.playerQuit(handler.player.getUUID()));
    }
}
