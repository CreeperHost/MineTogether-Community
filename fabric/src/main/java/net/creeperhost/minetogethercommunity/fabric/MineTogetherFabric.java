package net.creeperhost.minetogethercommunity.fabric;

import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.MineTogetherClient;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.connect.DedicatedServerConnect;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
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

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            clientInit();
        } else {
            serverInit();
        }
    }

    private void clientInit() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> MineTogetherClient.registerClientCommands(dispatcher));
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> MineTogetherChat.onScreenPostInit(screen));
    }

    private void serverInit() {
        ServerLifecycleEvents.SERVER_STARTED.register(DedicatedServerConnect::serverStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(DedicatedServerConnect::serverStopping);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> DedicatedServerConnect.playerJoined(handler.player));
    }
}
