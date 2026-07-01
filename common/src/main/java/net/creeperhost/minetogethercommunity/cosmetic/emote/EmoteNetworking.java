package net.creeperhost.minetogethercommunity.cosmetic.emote;

import dev.architectury.networking.NetworkManager;
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

public class EmoteNetworking {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final SimpleNetworkManager NETWORK = SimpleNetworkManager.create(MineTogether.MOD_ID);
    private static final int MAX_EMOTE_ID_LENGTH = 128;

    private static MessageType START_C2S;
    private static MessageType START_S2C;
    private static boolean initialized;

    public static void init() {
        if (initialized) return;
        initialized = true;
        START_C2S = NETWORK.registerC2S("emote_start_c2s", StartEmoteC2S::new);
        START_S2C = NETWORK.registerS2C("emote_start_s2c", StartEmoteS2C::new);
        LOGGER.debug("Emote networking initialized");
    }

    public static void tryBroadcastStart(String emoteId) {
        if (emoteId == null || emoteId.isEmpty()) return;
        if (START_C2S == null || !NetworkManager.canServerReceive(START_C2S.getId())) return;
        new StartEmoteC2S(emoteId).sendToServer();
    }

    private static boolean validEmoteId(String emoteId) {
        return emoteId != null && !emoteId.isEmpty() && emoteId.length() <= MAX_EMOTE_ID_LENGTH;
    }

    private static class StartEmoteC2S extends BaseC2SMessage {
        private final String emoteId;

        private StartEmoteC2S(String emoteId) {
            this.emoteId = emoteId;
        }

        private StartEmoteC2S(RegistryFriendlyByteBuf buf) {
            this.emoteId = buf.readUtf(MAX_EMOTE_ID_LENGTH);
        }

        @Override
        public MessageType getType() {
            return START_C2S;
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(emoteId, MAX_EMOTE_ID_LENGTH);
        }

        @Override
        public void handle(NetworkManager.PacketContext context) {
            if (!validEmoteId(emoteId)) return;
            Player sender = context.getPlayer();
            if (!(sender instanceof ServerPlayer serverPlayer)) return;
            UUID playerId = serverPlayer.getUUID();
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
            this.emoteId = buf.readUtf(MAX_EMOTE_ID_LENGTH);
        }

        @Override
        public MessageType getType() {
            return START_S2C;
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(playerId);
            buf.writeUtf(emoteId, MAX_EMOTE_ID_LENGTH);
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

    private EmoteNetworking() {
    }
}
