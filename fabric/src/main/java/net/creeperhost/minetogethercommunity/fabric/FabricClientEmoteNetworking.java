package net.creeperhost.minetogethercommunity.fabric;

import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteNetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public final class FabricClientEmoteNetworking {

    private static boolean initialized;

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientPlayNetworking.registerGlobalReceiver(EmoteNetworking.START_S2C_TYPE, (payload, context) ->
                context.client().execute(() -> EmoteNetworking.handleStartFromServer(payload.playerId(), payload.emoteId())));
        ClientPlayNetworking.registerGlobalReceiver(EmoteNetworking.STOP_S2C_TYPE, (payload, context) ->
                context.client().execute(() -> EmoteNetworking.handleStopFromServer(payload.playerId())));
    }

    public static boolean canSendToServer() {
        try {
            return ClientPlayNetworking.canSend(EmoteNetworking.START_C2S_TYPE);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static void sendToServer(EmoteNetworking.StartEmoteC2S packet) {
        ClientPlayNetworking.send(packet);
    }

    public static void sendToServer(EmoteNetworking.StopEmoteC2S packet) {
        ClientPlayNetworking.send(packet);
    }

    private FabricClientEmoteNetworking() {
    }
}
