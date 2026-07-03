package net.creeperhost.minetogethercommunity.cosmetic.emote;

import io.netty.buffer.ByteBuf;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.UUID;

public final class EmoteNetworking {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("mtcommunity");
    private static final int MAX_EMOTE_ID_LENGTH = 128;
    private static boolean initialized;

    private EmoteNetworking() {
    }

    public static void init(boolean client) {
        if (initialized) return;
        initialized = true;
        CHANNEL.registerMessage(StartServerHandler.class, StartEmoteC2S.class, 0, Side.SERVER);
        CHANNEL.registerMessage(client ? StartClientHandler.class : NoopStartClientHandler.class, StartEmoteS2C.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(StopServerHandler.class, StopEmoteC2S.class, 2, Side.SERVER);
        CHANNEL.registerMessage(client ? StopClientHandler.class : NoopStopClientHandler.class, StopEmoteS2C.class, 3, Side.CLIENT);
        LOGGER.debug("Emote networking initialized");
    }

    public static void tryBroadcastStart(String emoteId) {
        if (emoteId == null || emoteId.isEmpty() || !initialized) return;
        try {
            CHANNEL.sendToServer(new StartEmoteC2S(emoteId));
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
        return emoteId != null && !emoteId.isEmpty() && emoteId.length() <= MAX_EMOTE_ID_LENGTH;
    }

    public static class StartEmoteC2S implements IMessage {
        private String emoteId;

        public StartEmoteC2S() {
        }

        private StartEmoteC2S(String emoteId) {
            this.emoteId = emoteId;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.emoteId = ByteBufUtils.readUTF8String(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, emoteId == null ? "" : emoteId);
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
            server.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    if (!validEmoteId(message.emoteId)) return;
                    StartEmoteS2C packet = new StartEmoteS2C(sender.getUniqueID(), message.emoteId);
                    List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
                    for (EntityPlayerMP target : players) {
                        if (target == sender) continue;
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
            server.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    StopEmoteS2C packet = new StopEmoteS2C(sender.getUniqueID());
                    List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
                    for (EntityPlayerMP target : players) {
                        if (target == sender) continue;
                        CHANNEL.sendTo(packet, target);
                    }
                }
            });
            return null;
        }
    }

    public static class StartClientHandler implements IMessageHandler<StartEmoteS2C, IMessage> {
        @Override
        public IMessage onMessage(final StartEmoteS2C message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
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
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
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
