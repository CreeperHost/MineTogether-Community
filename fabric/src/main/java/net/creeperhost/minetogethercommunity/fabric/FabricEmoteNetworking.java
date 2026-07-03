package net.creeperhost.minetogethercommunity.fabric;

import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class FabricEmoteNetworking {

    private static boolean initialized;

    public static void init() {
        if (initialized) return;
        initialized = true;
        PayloadTypeRegistry.serverboundPlay().register(EmoteNetworking.START_C2S_TYPE, EmoteNetworking.START_C2S_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(EmoteNetworking.STOP_C2S_TYPE, EmoteNetworking.STOP_C2S_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EmoteNetworking.START_S2C_TYPE, EmoteNetworking.START_S2C_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EmoteNetworking.STOP_S2C_TYPE, EmoteNetworking.STOP_S2C_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(EmoteNetworking.START_C2S_TYPE, (payload, context) ->
                context.server().execute(() -> EmoteNetworking.handleStartFromClient(context.player(), payload.emoteId())));
        ServerPlayNetworking.registerGlobalReceiver(EmoteNetworking.STOP_C2S_TYPE, (payload, context) ->
                context.server().execute(() -> EmoteNetworking.handleStopFromClient(context.player())));
    }

    public static void sendToPlayer(ServerPlayer player, EmoteNetworking.StartEmoteS2C packet) {
        if (ServerPlayNetworking.canSend(player, EmoteNetworking.START_S2C_TYPE)) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    public static void sendToPlayer(ServerPlayer player, EmoteNetworking.StopEmoteS2C packet) {
        if (ServerPlayNetworking.canSend(player, EmoteNetworking.STOP_S2C_TYPE)) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    private FabricEmoteNetworking() {
    }
}
