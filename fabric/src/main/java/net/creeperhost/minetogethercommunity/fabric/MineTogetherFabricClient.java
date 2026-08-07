package net.creeperhost.minetogethercommunity.fabric;

import net.creeperhost.minetogethercommunity.MineTogetherClient;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.compat.Integration;
import net.creeperhost.minetogethercommunity.compat.ftbquests.FTBQuestsCompat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

public final class MineTogetherFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Integration.runOptional("ftbquests", () -> FTBQuestsCompat::registerFabricEvents);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> MineTogetherClient.registerClientCommands(dispatcher));
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> MineTogetherChat.onScreenPostInit(screen));
    }
}
