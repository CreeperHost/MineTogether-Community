package net.creeperhost.minetogethercommunity.neoforge;

import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteNetworking;
import net.creeperhost.minetogethercommunity.platform.MineTogetherClientPlatformService;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.ArrayList;
import java.util.List;

public final class MineTogetherClientPlatformImpl implements MineTogetherClientPlatformService {

    private static final List<KeyMapping> KEY_MAPPINGS = new ArrayList<>();

    static List<KeyMapping> getKeyMappings() {
        return KEY_MAPPINGS;
    }

    @Override
    public void registerKeyMapping(KeyMapping keyMapping) {
        KEY_MAPPINGS.add(keyMapping);
    }

    @Override
    public void prepareConnection(Connection connection) {
        // Not required on NeoForge.
    }

    @Override
    public boolean canSendEmoteToServer() {
        return Minecraft.getInstance().getConnection() != null;
    }

    @Override
    public boolean canSendEmoteHelloToServer() {
        return Minecraft.getInstance().getConnection() != null
                && NetworkRegistry.hasChannel(Minecraft.getInstance().getConnection(), EmoteNetworking.HELLO_C2S_TYPE.id());
    }

    @Override
    public void sendEmoteHelloToServer(EmoteNetworking.EmoteHelloC2S packet) {
        ClientPacketDistributor.sendToServer(packet);
    }

    @Override
    public void sendEmoteStartToServer(EmoteNetworking.StartEmoteC2S packet) {
        ClientPacketDistributor.sendToServer(packet);
    }

    @Override
    public void sendEmoteStopToServer(EmoteNetworking.StopEmoteC2S packet) {
        ClientPacketDistributor.sendToServer(packet);
    }
}
