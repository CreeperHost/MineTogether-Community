package net.creeperhost.minetogethercommunity.platform;

import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.Connection;

public interface MineTogetherClientPlatformService {

    void registerKeyMapping(KeyMapping keyMapping);

    void prepareConnection(Connection connection);

    boolean canSendEmoteToServer();

    boolean canSendEmoteHelloToServer();

    void sendEmoteHelloToServer(EmoteNetworking.EmoteHelloC2S packet);

    void sendEmoteStartToServer(EmoteNetworking.StartEmoteC2S packet);

    void sendEmoteStopToServer(EmoteNetworking.StopEmoteC2S packet);
}
