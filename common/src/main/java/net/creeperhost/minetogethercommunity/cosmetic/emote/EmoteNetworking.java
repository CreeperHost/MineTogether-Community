package net.creeperhost.minetogethercommunity.cosmetic.emote;

import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.MineTogetherPlatform;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticIdValidator;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EmoteNetworking {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int MAX_EMOTE_ID_LENGTH = 128;
    private static final Map<UUID, String> ACTIVE_PERSISTENT_EMOTES = new ConcurrentHashMap<>();

    public static final CustomPacketPayload.Type<StartEmoteC2S> START_C2S_TYPE = type("emote_start_c2s");
    public static final CustomPacketPayload.Type<StartEmoteS2C> START_S2C_TYPE = type("emote_start_s2c");
    public static final CustomPacketPayload.Type<StopEmoteC2S> STOP_C2S_TYPE = type("emote_stop_c2s");
    public static final CustomPacketPayload.Type<StopEmoteS2C> STOP_S2C_TYPE = type("emote_stop_s2c");

    public static final StreamCodec<RegistryFriendlyByteBuf, StartEmoteC2S> START_C2S_CODEC =
            CustomPacketPayload.codec(StartEmoteC2S::write, StartEmoteC2S::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, StartEmoteS2C> START_S2C_CODEC =
            CustomPacketPayload.codec(StartEmoteS2C::write, StartEmoteS2C::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, StopEmoteC2S> STOP_C2S_CODEC =
            CustomPacketPayload.codec(StopEmoteC2S::write, StopEmoteC2S::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, StopEmoteS2C> STOP_S2C_CODEC =
            CustomPacketPayload.codec(StopEmoteS2C::write, StopEmoteS2C::new);

    private static boolean initialized;

    public static void init() {
        if (initialized) return;
        initialized = true;
        LOGGER.debug("Emote networking initialized");
    }

    public static void tryBroadcastStart(String emoteId, boolean persistent) {
        if (!validEmoteId(emoteId) || !MineTogetherPlatform.canSendEmoteToServer()) return;
        MineTogetherPlatform.sendEmoteStartToServer(new StartEmoteC2S(emoteId, persistent));
    }

    public static void tryBroadcastStop() {
        if (!MineTogetherPlatform.canSendEmoteToServer()) return;
        MineTogetherPlatform.sendEmoteStopToServer(new StopEmoteC2S());
    }

    public static void handleStartFromClient(ServerPlayer serverPlayer, String emoteId, boolean persistent) {
        if (!validEmoteId(emoteId)) return;
        UUID playerId = serverPlayer.getUUID();
        if (persistent) {
            ACTIVE_PERSISTENT_EMOTES.put(playerId, emoteId);
        } else {
            ACTIVE_PERSISTENT_EMOTES.remove(playerId);
        }
        StartEmoteS2C packet = new StartEmoteS2C(playerId, emoteId);
        for (ServerPlayer target : serverPlayer.level().getServer().getPlayerList().getPlayers()) {
            if (target == serverPlayer) continue;
            MineTogetherPlatform.sendEmoteStartToClient(target, packet);
        }
    }

    public static void handleStopFromClient(ServerPlayer serverPlayer) {
        UUID playerId = serverPlayer.getUUID();
        ACTIVE_PERSISTENT_EMOTES.remove(playerId);
        StopEmoteS2C packet = new StopEmoteS2C(playerId);
        for (ServerPlayer target : serverPlayer.level().getServer().getPlayerList().getPlayers()) {
            if (target == serverPlayer) continue;
            MineTogetherPlatform.sendEmoteStopToClient(target, packet);
        }
    }

    public static void handleStartFromServer(UUID playerId, String emoteId) {
        if (!validEmoteId(emoteId)) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null && mc.player.getUUID().equals(playerId)) return;
            CosmeticDownloader.instance().ensureAssetLoaded("emote", emoteId);
            EmotePlayer.playRemote(playerId, emoteId);
        });
    }

    public static void handleStopFromServer(UUID playerId) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null && mc.player.getUUID().equals(playerId)) return;
            EmotePlayer.stop(playerId);
        });
    }

    private static boolean validEmoteId(String emoteId) {
        return CosmeticIdValidator.isValid(emoteId);
    }

    public static void syncPersistentEmotes(ServerPlayer target) {
        for (Map.Entry<UUID, String> active : ACTIVE_PERSISTENT_EMOTES.entrySet()) {
            if (!active.getKey().equals(target.getUUID())) {
                MineTogetherPlatform.sendEmoteStartToClient(target, new StartEmoteS2C(active.getKey(), active.getValue()));
            }
        }
    }

    public static void playerQuit(UUID playerId) {
        ACTIVE_PERSISTENT_EMOTES.remove(playerId);
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MineTogether.MOD_ID, path));
    }

    public record StartEmoteC2S(String emoteId, boolean persistent) implements CustomPacketPayload {
        public StartEmoteC2S(RegistryFriendlyByteBuf buf) {
            this(buf.readUtf(MAX_EMOTE_ID_LENGTH), buf.readBoolean());
        }

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(emoteId, MAX_EMOTE_ID_LENGTH);
            buf.writeBoolean(persistent);
        }

        @Override
        public Type<StartEmoteC2S> type() {
            return START_C2S_TYPE;
        }
    }

    public record StartEmoteS2C(UUID playerId, String emoteId) implements CustomPacketPayload {
        public StartEmoteS2C(RegistryFriendlyByteBuf buf) {
            this(buf.readUUID(), buf.readUtf(MAX_EMOTE_ID_LENGTH));
        }

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(playerId);
            buf.writeUtf(emoteId, MAX_EMOTE_ID_LENGTH);
        }

        @Override
        public Type<StartEmoteS2C> type() {
            return START_S2C_TYPE;
        }
    }

    public record StopEmoteC2S() implements CustomPacketPayload {
        public StopEmoteC2S(RegistryFriendlyByteBuf buf) {
            this();
        }

        public void write(RegistryFriendlyByteBuf buf) {
        }

        @Override
        public Type<StopEmoteC2S> type() {
            return STOP_C2S_TYPE;
        }
    }

    public record StopEmoteS2C(UUID playerId) implements CustomPacketPayload {
        public StopEmoteS2C(RegistryFriendlyByteBuf buf) {
            this(buf.readUUID());
        }

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(playerId);
        }

        @Override
        public Type<StopEmoteS2C> type() {
            return STOP_S2C_TYPE;
        }
    }

    private EmoteNetworking() {
    }
}
