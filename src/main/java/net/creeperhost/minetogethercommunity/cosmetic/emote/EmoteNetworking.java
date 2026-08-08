package net.creeperhost.minetogethercommunity.cosmetic.emote;

import com.google.common.hash.Hashing;
import io.netty.buffer.ByteBuf;
import net.creeperhost.minetogether.lib.chat.irc.IrcUser;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.util.ClientTaskRunner;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticIdValidator;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EmoteNetworking {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String CHANNEL_NAME = "mtcommunity";
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
    private static final int MAX_EMOTE_ID_LENGTH = 128;
    private static final String CTCP_START = "MT_EMOTE_START";
    private static final String CTCP_STOP = "MT_EMOTE_STOP";
    private static boolean initialized;
    private static volatile boolean capableServer;
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
        CHANNEL.registerMessage(HelloServerHandler.class, HelloC2S.class, 4, Side.SERVER);
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
        for (Object playerObject : mc.theWorld.playerEntities) {
            if (!(playerObject instanceof EntityPlayer)) continue;
            EntityPlayer player = (EntityPlayer) playerObject;
            if (player.getUniqueID().equals(mc.thePlayer.getUniqueID())) continue;
            Profile profile = onlineProfiles.get(profileHash(player.getUniqueID()));
            if (profile == null) continue;
            IrcUser user = MineTogetherChat.CHAT_STATE.ircClient.getUser(profile);
            if (user != null && user.isOnline()) user.sendRawCTCP(request);
        }
    }

    private static boolean canSendToServer() {
        return capableServer;
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

    public static void clientWorldReady() {
        if (!initialized || !canSendToServer()) return;
        try {
            CHANNEL.sendToServer(new HelloC2S());
        } catch (RuntimeException e) {
            LOGGER.debug("Could not announce emote packet support", e);
        }
    }

    private static void syncPersistentEmotes(EntityPlayerMP target) {
        for (Map.Entry<UUID, String> active : ACTIVE_PERSISTENT_EMOTES.entrySet()) {
            if (!active.getKey().equals(target.getUniqueID())) {
                CHANNEL.sendTo(new StartEmoteS2C(active.getKey(), active.getValue()), target);
            }
        }
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

    public static class HelloC2S implements IMessage {
        @Override
        public void fromBytes(ByteBuf buf) {
        }

        @Override
        public void toBytes(ByteBuf buf) {
        }
    }

    public static class StartServerHandler implements IMessageHandler<StartEmoteC2S, IMessage> {
        @Override
        public IMessage onMessage(final StartEmoteC2S message, final MessageContext ctx) {
            final EntityPlayerMP sender = ctx.getServerHandler().playerEntity;
            final MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null) return null;
            if (!validEmoteId(message.emoteId)) return null;
            if (message.persistent) ACTIVE_PERSISTENT_EMOTES.put(sender.getUniqueID(), message.emoteId);
            else ACTIVE_PERSISTENT_EMOTES.remove(sender.getUniqueID());
            StartEmoteS2C packet = new StartEmoteS2C(sender.getUniqueID(), message.emoteId);
            List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
            for (EntityPlayerMP target : players) {
                if (target == sender) continue;
                CHANNEL.sendTo(packet, target);
            }
            return null;
        }
    }

    public static class StopServerHandler implements IMessageHandler<StopEmoteC2S, IMessage> {
        @Override
        public IMessage onMessage(StopEmoteC2S message, final MessageContext ctx) {
            final EntityPlayerMP sender = ctx.getServerHandler().playerEntity;
            final MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null) return null;
            ACTIVE_PERSISTENT_EMOTES.remove(sender.getUniqueID());
            StopEmoteS2C packet = new StopEmoteS2C(sender.getUniqueID());
            List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
            for (EntityPlayerMP target : players) {
                if (target == sender) continue;
                CHANNEL.sendTo(packet, target);
            }
            return null;
        }
    }

    public static class HelloServerHandler implements IMessageHandler<HelloC2S, IMessage> {
        @Override
        public IMessage onMessage(HelloC2S message, MessageContext ctx) {
            syncPersistentEmotes(ctx.getServerHandler().playerEntity);
            return null;
        }
    }

    public static class PlayerLifecycleHandler {
        @SubscribeEvent
        public void onChannelRegistration(FMLNetworkEvent.CustomPacketRegistrationEvent<?> event) {
            if (event.side != Side.CLIENT || !registrationChannels(event).contains(CHANNEL_NAME)) return;
            capableServer = "REGISTER".equals(registrationOperation(event));
        }

        @SuppressWarnings("unchecked")
        private static Set<String> registrationChannels(FMLNetworkEvent.CustomPacketRegistrationEvent<?> event) {
            try {
                // These public Forge fields are not part of the Minecraft mappings,
                // but the 1.7.10 reobfuscator still rewrites direct bytecode access.
                // Reflection by Forge's stable runtime field name avoids that bad remap.
                return (Set<String>) event.getClass().getField("registrations").get(event);
            } catch (ReflectiveOperationException exception) {
                LOGGER.warn("Could not inspect Forge channel registrations", exception);
                return java.util.Collections.emptySet();
            }
        }

        private static String registrationOperation(FMLNetworkEvent.CustomPacketRegistrationEvent<?> event) {
            try {
                return (String) event.getClass().getField("operation").get(event);
            } catch (ReflectiveOperationException exception) {
                LOGGER.warn("Could not inspect Forge channel registration operation", exception);
                return "";
            }
        }

        @SubscribeEvent
        public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            capableServer = false;
        }

        @SubscribeEvent
        public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (!(event.player instanceof EntityPlayerMP)) return;
            UUID playerId = event.player.getUniqueID();
            if (ACTIVE_PERSISTENT_EMOTES.remove(playerId) == null) return;
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null) return;
            StopEmoteS2C packet = new StopEmoteS2C(playerId);
            List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
            for (EntityPlayerMP target : players) {
                if (target == event.player) continue;
                CHANNEL.sendTo(packet, target);
            }
            LOGGER.debug("Cleared persistent emote state for disconnected player {}", event.player.getCommandSenderName());
        }
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
