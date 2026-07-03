package net.creeperhost.minetogethercommunity.neoforge;

import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NeoForgeEmoteNetworking {

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MineTogether.MOD_ID).versioned("1");
        registrar.playToServer(EmoteNetworking.START_C2S_TYPE, EmoteNetworking.START_C2S_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        EmoteNetworking.handleStartFromClient(serverPlayer, payload.emoteId());
                    }
                }));
        registrar.playToServer(EmoteNetworking.STOP_C2S_TYPE, EmoteNetworking.STOP_C2S_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        EmoteNetworking.handleStopFromClient(serverPlayer);
                    }
                }));
        registrar.playToClient(EmoteNetworking.START_S2C_TYPE, EmoteNetworking.START_S2C_CODEC, (payload, context) ->
                context.enqueueWork(() -> EmoteNetworking.handleStartFromServer(payload.playerId(), payload.emoteId())));
        registrar.playToClient(EmoteNetworking.STOP_S2C_TYPE, EmoteNetworking.STOP_S2C_CODEC, (payload, context) ->
                context.enqueueWork(() -> EmoteNetworking.handleStopFromServer(payload.playerId())));
    }

    private NeoForgeEmoteNetworking() {
    }
}
