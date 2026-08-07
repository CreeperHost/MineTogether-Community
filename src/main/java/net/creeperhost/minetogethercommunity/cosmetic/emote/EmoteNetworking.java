package net.creeperhost.minetogethercommunity.cosmetic.emote;

import com.google.common.hash.Hashing;
import io.netty.buffer.ByteBuf;
import net.creeperhost.minetogether.lib.chat.irc.IrcUser;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticIdValidator;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.util.ClientTaskRunner;
import net.creeperhost.minetogethercommunity.util.ServerTaskRunner;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EmoteNetworking {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("mtcommunity");
    private static final int MAX_EMOTE_ID_LENGTH = 128;
    private static final String CTCP_START = "MT_EMOTE_START";
    private static final String CTCP_STOP = "MT_EMOTE_STOP";
    private static boolean initialized;
    private static final Map<UUID, String> ACTIVE_PERSISTENT_EMOTES = new ConcurrentHashMap<UUID, String>();

    private EmoteNetworking() {
    }

    public static void init(boolean client) {
        if (initialized) return;
        initialized = true;
        CHANNEL.registerMessage(StartServerHandler.class, StartEmoteC2S.class, 0, Side.SERVER);
        CHANNEL.registerMessage(client ? StartClientHandler.class : NoopStartClientHandler.class, StartEmoteS2C.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(StopServerHandler.class, StopEmoteC2S.class, 2, Side.SERVER);
        CHANNEL.registerMessage(client ? StopClientHandler.class : NoopStopClientHandler.class, StopEmoteS2C.class, 3, Side.CLIENT);
        FMLCommonHandler.instance().bus().register(new PlayerLifecycleHandler());
        LOGGER.debug("Emote networking initialized");
    }

    public static void tryBroadcastStart(String emoteId, boolean persistent) {
        if (!validEmoteId(emoteId) || !initialized) return;
        if (canSendToServer()) {
            try {
                CHANNEL.sendToServer(new StartEmoteC2S(emoteId, persistent));
            } catch (RuntimeException e) {
                LOGGER.debug("Could not broadcast emote start '{}'", emoteId, e);
            }
        } else {
            UUID playerId = localPlayerId();
            if (playerId != null) broadcastCtcp(CTCP_START + " " + playerId + " " + emoteId);
        }
    }

    public static void tryBroadcastStop() {
        if (!initialized) return;
        if (canSendToServer()) {
            try {
                CHANNEL.sendToServer(new StopEmoteC2S());
            } catch (RuntimeException e) {
                LOGGER.debug("Could not broadcast emote stop", e);
            }
        } else {
            UUID playerId = localPlayerId();
            if (playerId != null) broadcastCtcp(CTCP_STOP + " " + playerId);
        }
    }

    public static boolean handleCtcp(final Profile sender, String request) {
        if (request == null) return false;
        String[] parts = request.split(" ", 3);
        if (!CTCP_START.equals(parts[0]) && !CTCP_STOP.equals(parts[0])) return false;
        if (sender == null || !sender.isOnline() || parts.length < 2) return true;
        try {
            final UUID playerId = UUID.fromString(parts[1]);
            if (!profileMatchesPlayer(sender, playerId)) return true;
            final String emoteId = CTCP_START.equals(parts[0]) && parts.length == 3 ? parts[2] : null;
            if (CTCP_START.equals(parts[0]) && !validEmoteId(emoteId)) return true;
            ClientTaskRunner.run(new Runnable() {
                @Override
                public void run() {
                    if (!isPlayerInCurrentServer(playerId)) return;
                    if (emoteId != null) {
                        CosmeticDownloader.instance().ensureAssetLoaded("emote", emoteId);
                        EmotePlayer.playRemote(playerId, emoteId);
                    } else {
                        EmotePlayer.stop(playerId);
                    }
                }
            });
        } catch (IllegalArgumentException ignored) {
            LOGGER.debug("Ignored malformed MineTogether emote CTCP from {}", sender.getDisplayName());
        }
        return true;
    }

    private static void broadcastCtcp(String request) {
        Minecraft mc = Minecraft.getMinecraft();
        if (request == null || mc.thePlayer == null || mc.theWorld == null || MineTogetherChat.CHAT_STATE == null) return;
        Map<String, Profile> onlineProfiles = new HashMap<String, Profile>();
        for (Profile profile : MineTogetherChat.CHAT_STATE.profileManager.getKnownProfiles()) {
            if (profile.isOnline() && profile.hasFullHash()) {
                onlineProfiles.put(profile.getFullHash().toUpperCase(Locale.ROOT), profile);
            }
        }
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player.getUniqueID().equals(mc.thePlayer.getUniqueID())) continue;
            Profile profile = onlineProfiles.get(profileHash(player.getUniqueID()));
            if (profile == null) continue;
            IrcUser user = MineTogetherChat.CHAT_STATE.ircClient.getUser(profile);
            if (user != null && user.isOnline()) user.sendRawCTCP(request);
        }
    }

    private static boolean canSendToServer() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null || mc.getNetHandler().getNetworkManager() == null) return false;
        NetworkDispatcher dispatcher = NetworkDispatcher.get(mc.getNetHandler().getNetworkManager());
        return dispatcher != null && dispatcher.getModList().containsKey(MineTogether.MOD_ID);
    }

    private static UUID localPlayerId() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer == null ? null : mc.thePlayer.getUniqueID();
    }

    private static boolean profileMatchesPlayer(Profile profile, UUID playerId) {
        return profile.hasFullHash() && profileHash(playerId).equalsIgnoreCase(profile.getFullHash());
    }

    private static boolean isPlayerInCurrentServer(UUID playerId) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.getUniqueID().equals(playerId)) return false;
        return mc.theWorld.getPlayerEntityByUUID(playerId) != null;
    }

    private static String profileHash(UUID playerId) {
        return Hashing.sha256().hashString(playerId.toString(), StandardCharsets.UTF_8).toString().toUpperCase(Locale.ROOT);
    }

    private static boolean validEmoteId(String emoteId) {
        return CosmeticIdValidator.isValid(emoteId);
    }

    public static class StartEmoteC2S implements IMessage {
        private String emoteId;
        private boolean persistent;

        public StartEmoteC2S() {
        }

        private StartEmoteC2S(String emoteId, boolean persistent) {
            this.emoteId = emoteId;
            this.persistent = persistent;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.emoteId = ByteBufUtils.readUTF8String(buf);
            this.persistent = buf.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, emoteId == null ? "" : emoteId);
            buf.writeBoolean(persistent);
        }
    }

    public static class StartEmoteS2C implements IMessage {
        private UUID playerId;
        private String emoteId;

        public StartEmoteS2C() {
        }

        private StartEmoteS2C(UUID playerId, String emoteId) {
            this.playerId = playerId;
            this.emoteId = emoteId;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.playerId = new UUID(buf.readLong(), buf.readLong());
            this.emoteId = ByteBufUtils.readUTF8String(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            UUID id = playerId == null ? new UUID(0L, 0L) : playerId;
            buf.writeLong(id.getMostSignificantBits());
            buf.writeLong(id.getLeastSignificantBits());
            ByteBufUtils.writeUTF8String(buf, emoteId == null ? "" : emoteId);
        }
    }

    public static class StopEmoteC2S implements IMessage {
        @Override
        public void fromBytes(ByteBuf buf) {
        }

        @Override
        public void toBytes(ByteBuf buf) {
        }
    }

    public static class StopEmoteS2C implements IMessage {
        private UUID playerId;

        public StopEmoteS2C() {
        }

        private StopEmoteS2C(UUID playerId) {
            this.playerId = playerId;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.playerId = new UUID(buf.readLong(), buf.readLong());
        }

        @Override
        public void toBytes(ByteBuf buf) {
            UUID id = playerId == null ? new UUID(0L, 0L) : playerId;
            buf.writeLong(id.getMostSignificantBits());
            buf.writeLong(id.getLeastSignificantBits());
        }
    }

    public static class StartServerHandler implements IMessageHandler<StartEmoteC2S, IMessage> {
        @Override
        public IMessage onMessage(final StartEmoteC2S message, final MessageContext ctx) {
            final EntityPlayerMP sender = ctx.getServerHandler().playerEntity;
            final MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null) return null;
            ServerTaskRunner.run(server, new Runnable() {
                @Override
                public void run() {
                    if (!validEmoteId(message.emoteId)) return;
                    if (message.persistent) ACTIVE_PERSISTENT_EMOTES.put(sender.getUniqueID(), message.emoteId);
                    else ACTIVE_PERSISTENT_EMOTES.remove(sender.getUniqueID());
                    StartEmoteS2C packet = new StartEmoteS2C(sender.getUniqueID(), message.emoteId);
                    List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
                    for (EntityPlayerMP target : players) {
                        if (target == sender || !canReceiveEmotePackets(target)) continue;
                        CHANNEL.sendTo(packet, target);
                    }
                }
            });
            return null;
        }
    }

    public static class StopServerHandler implements IMessageHandler<StopEmoteC2S, IMessage> {
        @Override
        public IMessage onMessage(StopEmoteC2S message, final MessageContext ctx) {
            final EntityPlayerMP sender = ctx.getServerHandler().playerEntity;
            final MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null) return null;
            ServerTaskRunner.run(server, new Runnable() {
                @Override
                public void run() {
                    ACTIVE_PERSISTENT_EMOTES.remove(sender.getUniqueID());
                    StopEmoteS2C packet = new StopEmoteS2C(sender.getUniqueID());
                    List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
                    for (EntityPlayerMP target : players) {
                        if (target == sender || !canReceiveEmotePackets(target)) continue;
                        CHANNEL.sendTo(packet, target);
                    }
                }
            });
            return null;
        }
    }

    public static class PlayerLifecycleHandler {
        @SubscribeEvent
        public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (!(event.player instanceof EntityPlayerMP)) return;
            EntityPlayerMP target = (EntityPlayerMP) event.player;
            if (!canReceiveEmotePackets(target)) return;
            for (Map.Entry<UUID, String> active : ACTIVE_PERSISTENT_EMOTES.entrySet()) {
                if (!active.getKey().equals(target.getUniqueID())) {
                    CHANNEL.sendTo(new StartEmoteS2C(active.getKey(), active.getValue()), target);
                }
            }
        }

        @SubscribeEvent
        public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (!(event.player instanceof EntityPlayerMP)) return;
            EntityPlayerMP sender = (EntityPlayerMP) event.player;
            UUID playerId = sender.getUniqueID();
            if (ACTIVE_PERSISTENT_EMOTES.remove(playerId) == null) return;

            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null) return;
            StopEmoteS2C packet = new StopEmoteS2C(playerId);
            List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
            for (EntityPlayerMP target : players) {
                if (target == sender || !canReceiveEmotePackets(target)) continue;
                CHANNEL.sendTo(packet, target);
            }
            LOGGER.debug("Cleared persistent emote state for disconnected player {}", sender.getName());
        }
    }

    private static boolean canReceiveEmotePackets(EntityPlayerMP target) {
        if (target == null || target.playerNetServerHandler == null || target.playerNetServerHandler.netManager == null) {
            return false;
        }
        Boolean hasFml = target.playerNetServerHandler.netManager.channel().attr(NetworkRegistry.FML_MARKER).get();
        if (!Boolean.TRUE.equals(hasFml)) {
            return false;
        }
        NetworkDispatcher dispatcher = NetworkDispatcher.get(target.playerNetServerHandler.netManager);
        return dispatcher != null && dispatcher.getModList().containsKey(MineTogether.MOD_ID);
    }

    public static class StartClientHandler implements IMessageHandler<StartEmoteS2C, IMessage> {
        @Override
        public IMessage onMessage(final StartEmoteS2C message, MessageContext ctx) {
            ClientTaskRunner.run(new Runnable() {
                @Override
                public void run() {
                    if (!validEmoteId(message.emoteId)) return;
                    if (Minecraft.getMinecraft().thePlayer != null && Minecraft.getMinecraft().thePlayer.getUniqueID().equals(message.playerId)) return;
                    CosmeticDownloader.instance().ensureAssetLoaded("emote", message.emoteId);
                    EmotePlayer.playRemote(message.playerId, message.emoteId);
                }
            });
            return null;
        }
    }

    public static class StopClientHandler implements IMessageHandler<StopEmoteS2C, IMessage> {
        @Override
        public IMessage onMessage(final StopEmoteS2C message, MessageContext ctx) {
            ClientTaskRunner.run(new Runnable() {
                @Override
                public void run() {
                    if (Minecraft.getMinecraft().thePlayer != null && Minecraft.getMinecraft().thePlayer.getUniqueID().equals(message.playerId)) return;
                    EmotePlayer.stop(message.playerId);
                }
            });
            return null;
        }
    }

    public static class NoopStartClientHandler implements IMessageHandler<StartEmoteS2C, IMessage> {
        @Override
        public IMessage onMessage(StartEmoteS2C message, MessageContext ctx) {
            return null;
        }
    }

    public static class NoopStopClientHandler implements IMessageHandler<StopEmoteS2C, IMessage> {
        @Override
        public IMessage onMessage(StopEmoteS2C message, MessageContext ctx) {
            return null;
        }
    }
}
