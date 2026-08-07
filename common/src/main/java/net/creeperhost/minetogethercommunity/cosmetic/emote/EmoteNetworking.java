package net.creeperhost.minetogethercommunity.cosmetic.emote;

import com.google.common.hash.Hashing;
import dev.architectury.networking.NetworkManager;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.simple.BaseC2SMessage;
import dev.architectury.networking.simple.BaseS2CMessage;
import dev.architectury.networking.simple.MessageType;
import dev.architectury.networking.simple.SimpleNetworkManager;
import net.creeperhost.minetogether.lib.chat.irc.IrcUser;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticIdValidator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
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
    private static final SimpleNetworkManager NETWORK = SimpleNetworkManager.create(MineTogether.MOD_ID);
    private static final String CTCP_START = "MT_EMOTE_START";
    private static final String CTCP_STOP = "MT_EMOTE_STOP";
    private static final Map<UUID, String> ACTIVE_PERSISTENT_EMOTES = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> EMOTE_CAPABLE_CLIENTS = ConcurrentHashMap.newKeySet();

    private static MessageType HELLO_C2S;
    private static MessageType START_C2S;
    private static MessageType START_S2C;
    private static MessageType STOP_C2S;
    private static MessageType STOP_S2C;
    private static boolean initialized;
    private static boolean clientSupportAnnounced;

    public static void init() {
        if (initialized) return;
        initialized = true;
        HELLO_C2S = NETWORK.registerC2S("emote_hello_c2s", EmoteHelloC2S::new);
        START_C2S = NETWORK.registerC2S("emote_start_c2s", StartEmoteC2S::new);
        START_S2C = NETWORK.registerS2C("emote_start_s2c", StartEmoteS2C::new);
        STOP_C2S = NETWORK.registerC2S("emote_stop_c2s", StopEmoteC2S::new);
        STOP_S2C = NETWORK.registerS2C("emote_stop_s2c", StopEmoteS2C::new);
        PlayerEvent.PLAYER_JOIN.register(EmoteNetworking::syncPersistentEmotes);
        PlayerEvent.PLAYER_QUIT.register(EmoteNetworking::onPlayerQuit);
        LOGGER.debug("Emote networking initialized");
    }

    /** Called from the client entrypoint; retries until the server's C2S channel is ready. */
    public static void initClient() {
        dev.architectury.event.events.client.ClientTickEvent.CLIENT_POST.register(mc -> {
            if (mc.player == null || mc.getConnection() == null) {
                clientSupportAnnounced = false;
                return;
            }
            if (!clientSupportAnnounced && HELLO_C2S != null && NetworkManager.canServerReceive(HELLO_C2S.getId())) {
                new EmoteHelloC2S().sendToServer();
                clientSupportAnnounced = true;
            }
        });
    }

    public static boolean isClientSupportAnnounced() {
        return clientSupportAnnounced;
    }

    public static void tryBroadcastStart(String emoteId, boolean persistent) {
        if (!validEmoteId(emoteId)) return;
        if (START_C2S != null && NetworkManager.canServerReceive(START_C2S.getId())) {
            new StartEmoteC2S(emoteId, persistent).sendToServer();
        } else {
            UUID playerId = localPlayerId();
            if (playerId != null) broadcastCtcp(CTCP_START + " " + playerId + " " + emoteId);
        }
    }

    public static void tryBroadcastStop() {
        if (STOP_C2S != null && NetworkManager.canServerReceive(STOP_C2S.getId())) {
            new StopEmoteC2S().sendToServer();
        } else {
            UUID playerId = localPlayerId();
            if (playerId != null) broadcastCtcp(CTCP_STOP + " " + playerId);
        }
    }

    /** Handles the direct MineTogether-chat fallback used on servers without this mod. */
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

    private static boolean validEmoteId(String emoteId) {
        return CosmeticIdValidator.isValid(emoteId);
    }

    private static void syncPersistentEmotes(ServerPlayer target) {
        if (START_S2C == null || !EMOTE_CAPABLE_CLIENTS.contains(target.getUUID())) return;
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

        private StartEmoteC2S(FriendlyByteBuf buf) {
            this.emoteId = buf.readUtf(128);
            this.persistent = buf.readBoolean();
        }

        @Override
        public MessageType getType() {
            return START_C2S;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
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
            int recipients = 0;
            for (ServerPlayer target : serverPlayer.server.getPlayerList().getPlayers()) {
                if (target == serverPlayer) continue;
                if (!EMOTE_CAPABLE_CLIENTS.contains(target.getUUID())) continue;
                packet.sendTo(target);
                recipients++;
            }
            LOGGER.debug("Relayed emote '{}' from {} to {} client(s)", emoteId, serverPlayer.getGameProfile().getName(), recipients);
        }
    }

    private static class EmoteHelloC2S extends BaseC2SMessage {
        private EmoteHelloC2S() {
        }

        private EmoteHelloC2S(FriendlyByteBuf buf) {
        }

        @Override
        public MessageType getType() {
            return HELLO_C2S;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
        }

        @Override
        public void handle(NetworkManager.PacketContext context) {
            Player sender = context.getPlayer();
            if (!(sender instanceof ServerPlayer serverPlayer)) return;
            EMOTE_CAPABLE_CLIENTS.add(serverPlayer.getUUID());
            syncPersistentEmotes(serverPlayer);
            LOGGER.debug("Registered emote packet support for {}", serverPlayer.getGameProfile().getName());
        }
    }

    private static void onPlayerQuit(Player player) {
        UUID playerId = player.getUUID();
        boolean wasActive = ACTIVE_PERSISTENT_EMOTES.remove(playerId) != null;
        EMOTE_CAPABLE_CLIENTS.remove(playerId);
        if (!wasActive || STOP_S2C == null || !(player instanceof ServerPlayer serverPlayer)) return;

        StopEmoteS2C packet = new StopEmoteS2C(playerId);
        for (ServerPlayer target : serverPlayer.server.getPlayerList().getPlayers()) {
            if (target == serverPlayer || !EMOTE_CAPABLE_CLIENTS.contains(target.getUUID())) continue;
            packet.sendTo(target);
        }
        LOGGER.debug("Cleared persistent emote state for disconnected player {}", serverPlayer.getGameProfile().getName());
    }

    private static class StartEmoteS2C extends BaseS2CMessage {
        private final UUID playerId;
        private final String emoteId;

        private StartEmoteS2C(UUID playerId, String emoteId) {
            this.playerId = playerId;
            this.emoteId = emoteId;
        }

        private StartEmoteS2C(FriendlyByteBuf buf) {
            this.playerId = buf.readUUID();
            this.emoteId = buf.readUtf(128);
        }

        @Override
        public MessageType getType() {
            return START_S2C;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
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

        private StopEmoteC2S(FriendlyByteBuf buf) {
        }

        @Override
        public MessageType getType() {
            return STOP_C2S;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
        }

        @Override
        public void handle(NetworkManager.PacketContext context) {
            Player sender = context.getPlayer();
            if (!(sender instanceof ServerPlayer serverPlayer)) return;
            UUID playerId = serverPlayer.getUUID();
            ACTIVE_PERSISTENT_EMOTES.remove(playerId);
            StopEmoteS2C packet = new StopEmoteS2C(playerId);
            int recipients = 0;
            for (ServerPlayer target : serverPlayer.server.getPlayerList().getPlayers()) {
                if (target == serverPlayer) continue;
                if (!EMOTE_CAPABLE_CLIENTS.contains(target.getUUID())) continue;
                packet.sendTo(target);
                recipients++;
            }
            LOGGER.debug("Relayed emote stop from {} to {} client(s)", serverPlayer.getGameProfile().getName(), recipients);
        }
    }

    private static class StopEmoteS2C extends BaseS2CMessage {
        private final UUID playerId;

        private StopEmoteS2C(UUID playerId) {
            this.playerId = playerId;
        }

        private StopEmoteS2C(FriendlyByteBuf buf) {
            this.playerId = buf.readUUID();
        }

        @Override
        public MessageType getType() {
            return STOP_S2C;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
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
