package net.creeperhost.minetogethercommunity.cosmetic.emote;

import dev.architectury.networking.NetworkManager;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.simple.BaseC2SMessage;
import dev.architectury.networking.simple.BaseS2CMessage;
import dev.architectury.networking.simple.MessageType;
import dev.architectury.networking.simple.SimpleNetworkManager;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EmoteNetworking {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final SimpleNetworkManager NETWORK = SimpleNetworkManager.create(MineTogether.MOD_ID);
    private static final Map<UUID, String> ACTIVE_PERSISTENT_EMOTES = new ConcurrentHashMap<>();

    private static MessageType START_C2S;
    private static MessageType START_S2C;
    private static MessageType STOP_C2S;
    private static MessageType STOP_S2C;
    private static boolean initialized;

    public static void init() {
        if (initialized) return;
        initialized = true;
        START_C2S = NETWORK.registerC2S("emote_start_c2s", StartEmoteC2S::new);
        START_S2C = NETWORK.registerS2C("emote_start_s2c", StartEmoteS2C::new);
        STOP_C2S = NETWORK.registerC2S("emote_stop_c2s", StopEmoteC2S::new);
        STOP_S2C = NETWORK.registerS2C("emote_stop_s2c", StopEmoteS2C::new);
        PlayerEvent.PLAYER_JOIN.register(EmoteNetworking::syncPersistentEmotes);
        PlayerEvent.PLAYER_QUIT.register(player -> ACTIVE_PERSISTENT_EMOTES.remove(player.getUUID()));
        LOGGER.debug("Emote networking initialized");
    }

    public static void tryBroadcastStart(String emoteId, boolean persistent) {
        if (emoteId == null || emoteId.isEmpty()) return;
        if (START_C2S == null || !NetworkManager.canServerReceive(START_C2S.getId())) return;
        new StartEmoteC2S(emoteId, persistent).sendToServer();
    }

    public static void tryBroadcastStop() {
        if (STOP_C2S == null || !NetworkManager.canServerReceive(STOP_C2S.getId())) return;
        new StopEmoteC2S().sendToServer();
    }

    private static boolean validEmoteId(String emoteId) {
        return CosmeticDownloader.isValidAssetId(emoteId);
    }

    private static void syncPersistentEmotes(ServerPlayer target) {
        if (START_S2C == null || !NetworkManager.canPlayerReceive(target, START_S2C.getId())) return;
        for (Map.Entry<UUID, String> active : ACTIVE_PERSISTENT_EMOTES.entrySet()) {
            if (!active.getKey().equals(target.getUUID())) {
                new StartEmoteS2C(active.getKey(), active.getValue()).sendTo(target);
            }
        }
    }

    private static class StartEmoteC2S extends BaseC2SMessage {
        private final String emoteId;
        private final boolean persistent;

        private StartEmoteC2S(String emoteId, boolean persistent) {
            this.emoteId = emoteId;
            this.persistent = persistent;
        }

        private StartEmoteC2S(RegistryFriendlyByteBuf buf) {
            this.emoteId = buf.readUtf(128);
            this.persistent = buf.readBoolean();
        }

        @Override
        public MessageType getType() {
            return START_C2S;
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(emoteId, 128);
            buf.writeBoolean(persistent);
        }

        @Override
        public void handle(NetworkManager.PacketContext context) {
            if (!validEmoteId(emoteId)) return;
            Player sender = context.getPlayer();
            if (!(sender instanceof ServerPlayer serverPlayer)) return;
            UUID playerId = serverPlayer.getUUID();
            if (persistent) {
                ACTIVE_PERSISTENT_EMOTES.put(playerId, emoteId);
            } else {
                ACTIVE_PERSISTENT_EMOTES.remove(playerId);
            }
            StartEmoteS2C packet = new StartEmoteS2C(playerId, emoteId);
            for (ServerPlayer target : serverPlayer.server.getPlayerList().getPlayers()) {
                if (target == serverPlayer) continue;
                if (!NetworkManager.canPlayerReceive(target, START_S2C.getId())) continue;
                packet.sendTo(target);
            }
        }
    }

    private static class StartEmoteS2C extends BaseS2CMessage {
        private final UUID playerId;
        private final String emoteId;

        private StartEmoteS2C(UUID playerId, String emoteId) {
            this.playerId = playerId;
            this.emoteId = emoteId;
        }

        private StartEmoteS2C(RegistryFriendlyByteBuf buf) {
            this.playerId = buf.readUUID();
            this.emoteId = buf.readUtf(128);
        }

        @Override
        public MessageType getType() {
            return START_S2C;
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(playerId);
            buf.writeUtf(emoteId, 128);
        }

        @Override
        public void handle(NetworkManager.PacketContext context) {
            if (!validEmoteId(emoteId)) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getUUID().equals(playerId)) return;
            CosmeticDownloader.instance().ensureAssetLoaded("emote", emoteId);
            EmotePlayer.playRemote(playerId, emoteId);
        }
    }

    private static class StopEmoteC2S extends BaseC2SMessage {
        private StopEmoteC2S() {
        }

        private StopEmoteC2S(RegistryFriendlyByteBuf buf) {
        }

        @Override
        public MessageType getType() {
            return STOP_C2S;
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf) {
        }

        @Override
        public void handle(NetworkManager.PacketContext context) {
            Player sender = context.getPlayer();
            if (!(sender instanceof ServerPlayer serverPlayer)) return;
            UUID playerId = serverPlayer.getUUID();
            ACTIVE_PERSISTENT_EMOTES.remove(playerId);
            StopEmoteS2C packet = new StopEmoteS2C(playerId);
            for (ServerPlayer target : serverPlayer.server.getPlayerList().getPlayers()) {
                if (target == serverPlayer) continue;
                if (!NetworkManager.canPlayerReceive(target, STOP_S2C.getId())) continue;
                packet.sendTo(target);
            }
        }
    }

    private static class StopEmoteS2C extends BaseS2CMessage {
        private final UUID playerId;

        private StopEmoteS2C(UUID playerId) {
            this.playerId = playerId;
        }

        private StopEmoteS2C(RegistryFriendlyByteBuf buf) {
            this.playerId = buf.readUUID();
        }

        @Override
        public MessageType getType() {
            return STOP_S2C;
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(playerId);
        }

        @Override
        public void handle(NetworkManager.PacketContext context) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getUUID().equals(playerId)) return;
            EmotePlayer.stop(playerId);
        }
    }

    private EmoteNetworking() {
    }
}
