package net.creeperhost.minetogethercommunity.fabric;

import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteNetworking;
import net.creeperhost.minetogethercommunity.platform.MineTogetherClientPlatformService;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.Connection;

public final class MineTogetherClientPlatformImpl implements MineTogetherClientPlatformService {

    @Override
    public void registerKeyMapping(KeyMapping keyMapping) {
        KeyMappingHelper.registerKeyMapping(keyMapping);
    }

    @Override
    public void prepareConnection(Connection connection) {
    }

    @Override
    public boolean canSendEmoteToServer() {
        return FabricClientEmoteNetworking.canSendToServer();
    }

    @Override
    public boolean canSendEmoteHelloToServer() {
        return FabricClientEmoteNetworking.canSendHelloToServer();
    }

    @Override
    public void sendEmoteHelloToServer(EmoteNetworking.EmoteHelloC2S packet) {
        FabricClientEmoteNetworking.sendToServer(packet);
    }

    @Override
    public void sendEmoteStartToServer(EmoteNetworking.StartEmoteC2S packet) {
        FabricClientEmoteNetworking.sendToServer(packet);
    }

    @Override
    public void sendEmoteStopToServer(EmoteNetworking.StopEmoteC2S packet) {
        FabricClientEmoteNetworking.sendToServer(packet);
    }
}
