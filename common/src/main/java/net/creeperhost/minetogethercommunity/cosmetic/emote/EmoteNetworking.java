package net.creeperhost.minetogethercommunity.cosmetic.emote;

import com.google.common.hash.Hashing;
import net.creeperhost.minetogether.lib.chat.irc.IrcUser;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticIdValidator;
import net.creeperhost.polylib.network.OptionalPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EmoteNetworking {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int MAX_EMOTE_ID_LENGTH = 128;
    private static final String CTCP_START = "MT_EMOTE_START";
    private static final String CTCP_STOP = "MT_EMOTE_STOP";
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
        OptionalPackets.registerClientbound(START_S2C_TYPE, START_S2C_CODEC);
        OptionalPackets.registerClientbound(STOP_S2C_TYPE, STOP_S2C_CODEC);
        OptionalPackets.registerServerbound(START_C2S_TYPE, START_C2S_CODEC, (payload, player) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                handleStartFromClient(serverPlayer, payload.emoteId(), payload.persistent());
            }
        });
        OptionalPackets.registerServerbound(STOP_C2S_TYPE, STOP_C2S_CODEC, (payload, player) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                handleStopFromClient(serverPlayer);
            }
        });
        LOGGER.debug("Emote networking initialized");
    }

    public static void initClient() {
        OptionalPackets.registerClientHandler(START_S2C_TYPE,
                (payload, player) -> handleStartFromServer(payload.playerId(), payload.emoteId()));
        OptionalPackets.registerClientHandler(STOP_S2C_TYPE,
                (payload, player) -> handleStopFromServer(payload.playerId()));
    }

    public static boolean canSendToServer() {
        return OptionalPackets.canSendToServer(START_C2S_TYPE)
                && OptionalPackets.canSendToServer(STOP_C2S_TYPE);
    }

    public static void tryBroadcastStart(String emoteId, boolean persistent) {
        if (!validEmoteId(emoteId)) return;
        if (canSendToServer()) {
            OptionalPackets.sendToServer(new StartEmoteC2S(emoteId, persistent));
        } else {
            UUID playerId = localPlayerId();
            if (playerId != null) broadcastCtcp(CTCP_START + " " + playerId + " " + emoteId);
        }
    }

    public static void tryBroadcastStop() {
        if (canSendToServer()) {
            OptionalPackets.sendToServer(new StopEmoteC2S());
        } else {
            UUID playerId = localPlayerId();
            if (playerId != null) broadcastCtcp(CTCP_STOP + " " + playerId);
        }
    }

    public static boolean handleCtcp(Profile sender, String request) {
        if (request == null) return false;
        String[] parts = request.split(" ", 3);
        if (!CTCP_START.equals(parts[0]) && !CTCP_STOP.equals(parts[0])) return false;
        if (sender == null || !sender.isOnline() || parts.length < 2) return true;
        try {
            UUID playerId = UUID.fromString(parts[1]);
            if (!profileMatchesPlayer(sender, playerId)) return true;
            String emoteId = CTCP_START.equals(parts[0]) && parts.length == 3 ? parts[2] : null;
            if (CTCP_START.equals(parts[0]) && !validEmoteId(emoteId)) return true;
            Minecraft.getInstance().execute(() -> {
                if (!isPlayerInCurrentServer(playerId)) return;
                if (emoteId != null) {
                    CosmeticDownloader.instance().ensureAssetLoaded("emote", emoteId);
                    EmotePlayer.playRemote(playerId, emoteId);
                } else {
                    EmotePlayer.stop(playerId);
                }
            });
        } catch (IllegalArgumentException ignored) {
            LOGGER.debug("Ignored malformed MineTogether emote CTCP from {}", sender.getDisplayName());
        }
        return true;
    }

    private static void broadcastCtcp(String request) {
        Minecraft mc = Minecraft.getInstance();
        if (request == null || mc.player == null || mc.level == null || MineTogetherChat.CHAT_STATE == null) return;
        Map<String, Profile> onlineProfiles = new HashMap<>();
        for (Profile profile : MineTogetherChat.CHAT_STATE.profileManager.getKnownProfiles()) {
            if (profile.isOnline() && profile.hasFullHash()) {
                onlineProfiles.put(profile.getFullHash().toUpperCase(Locale.ROOT), profile);
            }
        }
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player.getUUID().equals(mc.player.getUUID())) continue;
            Profile profile = onlineProfiles.get(profileHash(player.getUUID()));
            if (profile == null) continue;
            IrcUser user = MineTogetherChat.CHAT_STATE.ircClient.getUser(profile);
            if (user != null && user.isOnline()) user.sendRawCTCP(request);
        }
    }

    private static UUID localPlayerId() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getUUID();
    }

    private static boolean profileMatchesPlayer(Profile profile, UUID playerId) {
        return profile.hasFullHash() && profileHash(playerId).equalsIgnoreCase(profile.getFullHash());
    }

    private static boolean isPlayerInCurrentServer(UUID playerId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.player.getUUID().equals(playerId)) return false;
        return mc.level.getPlayerByUUID(playerId) != null;
    }

    @SuppressWarnings("deprecation")
    private static String profileHash(UUID playerId) {
        return Hashing.sha256().hashString(playerId.toString(), StandardCharsets.UTF_8).toString().toUpperCase(Locale.ROOT);
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
            OptionalPackets.sendToPlayer(target, packet);
        }
    }

    public static void handleStopFromClient(ServerPlayer serverPlayer) {
        UUID playerId = serverPlayer.getUUID();
        ACTIVE_PERSISTENT_EMOTES.remove(playerId);
        StopEmoteS2C packet = new StopEmoteS2C(playerId);
        for (ServerPlayer target : serverPlayer.level().getServer().getPlayerList().getPlayers()) {
            if (target == serverPlayer) continue;
            OptionalPackets.sendToPlayer(target, packet);
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
                OptionalPackets.sendToPlayer(target, new StartEmoteS2C(active.getKey(), active.getValue()));
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
