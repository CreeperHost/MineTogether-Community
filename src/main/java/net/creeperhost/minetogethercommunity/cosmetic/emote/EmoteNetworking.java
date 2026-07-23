package net.creeperhost.minetogethercommunity.cosmetic.emote;

import io.netty.buffer.ByteBuf;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.util.ClientTaskRunner;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticIdValidator;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EmoteNetworking {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("mtcommunity");
    private static final int MAX_EMOTE_ID_LENGTH = 128;
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
        MinecraftForge.EVENT_BUS.register(new PlayerLifecycleHandler());
        LOGGER.debug("Emote networking initialized");
    }

    public static void tryBroadcastStart(String emoteId, boolean persistent) {
        if (emoteId == null || emoteId.isEmpty() || !initialized) return;
        try {
            CHANNEL.sendToServer(new StartEmoteC2S(emoteId, persistent));
        } catch (RuntimeException e) {
            LOGGER.debug("Could not broadcast emote start '{}'", emoteId, e);
        }
    }

    public static void tryBroadcastStop() {
        if (!initialized) return;
        try {
            CHANNEL.sendToServer(new StopEmoteC2S());
        } catch (RuntimeException e) {
            LOGGER.debug("Could not broadcast emote stop", e);
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

    public static class PlayerLifecycleHandler {
        @SubscribeEvent
        public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (!(event.player instanceof EntityPlayerMP)) return;
            EntityPlayerMP target = (EntityPlayerMP) event.player;
            for (Map.Entry<UUID, String> active : ACTIVE_PERSISTENT_EMOTES.entrySet()) {
                if (!active.getKey().equals(target.getUniqueID())) {
                    CHANNEL.sendTo(new StartEmoteS2C(active.getKey(), active.getValue()), target);
                }
            }
        }

        @SubscribeEvent
        public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            ACTIVE_PERSISTENT_EMOTES.remove(event.player.getUniqueID());
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
