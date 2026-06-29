package net.creeperhost.minetogethercommunity.connect.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.covers1624.quack.util.SneakyUtils;
import net.creeperhost.minetogether.connect.lib.netty.AbstractChannelHandler;
import net.creeperhost.minetogether.connect.lib.netty.CipherCodec;
import net.creeperhost.minetogether.connect.lib.netty.FrameCodec;
import net.creeperhost.minetogether.connect.lib.netty.LoggingPacketCodec;
import net.creeperhost.minetogether.connect.lib.netty.ProtocolVersions;
import net.creeperhost.minetogether.connect.lib.netty.packet.CAccepted;
import net.creeperhost.minetogether.connect.lib.netty.packet.CBeginRaw;
import net.creeperhost.minetogether.connect.lib.netty.packet.CDisconnect;
import net.creeperhost.minetogether.connect.lib.netty.packet.CFriendServers;
import net.creeperhost.minetogether.connect.lib.netty.packet.CHello;
import net.creeperhost.minetogether.connect.lib.netty.packet.CMaxPlayers;
import net.creeperhost.minetogether.connect.lib.netty.packet.CMessage;
import net.creeperhost.minetogether.connect.lib.netty.packet.CPong;
import net.creeperhost.minetogether.connect.lib.netty.packet.CRaw;
import net.creeperhost.minetogether.connect.lib.netty.packet.CServerLink;
import net.creeperhost.minetogether.connect.lib.netty.packet.ClientPacketHandler;
import net.creeperhost.minetogether.connect.lib.netty.packet.Packet;
import net.creeperhost.minetogether.connect.lib.netty.packet.SAccepted;
import net.creeperhost.minetogether.connect.lib.netty.packet.SHello;
import net.creeperhost.minetogether.connect.lib.netty.packet.SHostConnect;
import net.creeperhost.minetogether.connect.lib.netty.packet.SHostRegister;
import net.creeperhost.minetogether.connect.lib.util.AESUtils;
import net.creeperhost.minetogether.connect.lib.util.RSAUtils;
import net.creeperhost.minetogether.session.JWebToken;
import net.creeperhost.minetogethercommunity.config.Config;
import net.creeperhost.minetogethercommunity.connect.ConnectHost;
import net.creeperhost.minetogethercommunity.util.DiagnosticLog;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.NetworkSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.NetHandlerHandshakeTCP;
import net.minecraft.util.MessageDeserializer;
import net.minecraft.util.MessageDeserializer2;
import net.minecraft.util.MessageSerializer;
import net.minecraft.util.MessageSerializer2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HostNettyClient {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Connect");
    private static final Field NETWORK_MANAGERS_FIELD = findField(NetworkSystem.class, "networkManagers", "field_151272_f");

    public static HostConnection publishServer(final MinecraftServer server, final ConnectHost endpoint, final JWebToken session, final String modpackKey, final int maxPlayers, final HostListener listener) {
        final Throwable[] error = new Throwable[1];
        final boolean[] accepted = new boolean[] { false };
        HostConnection connection = new HostConnection(endpoint, listener) {
            @Override
            protected void channelReady() {
                super.channelReady();
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] proxy handshake ready; registering dedicated server endpoint={}:{} modpackKey={} maxPlayers={}",
                        endpoint.getAddress(), Integer.valueOf(endpoint.getProxyPort()), modpackKey, Integer.valueOf(maxPlayers));
                sendPacket(new SHostRegister(session.toString(), modpackKey));
            }

            @Override
            protected void onDisconnected(String message) {
                error[0] = new IOException("Failed to host server: " + message);
                synchronized (error) {
                    error.notifyAll();
                }
                listener.onDisconnected(message);
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                super.channelInactive(ctx);
                listener.onChannelInactive(disconnectRequested);
            }

            @Override
            public void handleAccepted(ChannelHandlerContext ctx, CAccepted packet) {
                super.handleAccepted(ctx, packet);
                synchronized (error) {
                    accepted[0] = true;
                    error.notifyAll();
                }
                listener.onAccepted();
            }

            @Override
            public void handleMaxPlayers(ChannelHandlerContext ctx, CMaxPlayers packet) {
                int playerCap = packet.maxPlayers > 0 ? packet.maxPlayers : Integer.MAX_VALUE;
                listener.onMaxPlayers(Math.min(playerCap, maxPlayers), packet.maxPlayers);
            }

            @Override
            public void handleServerLink(ChannelHandlerContext ctx, CServerLink packet) {
                link(server, endpoint, session, packet.linkToken, listener);
            }
        };

        ChannelFuture future = openConnection(endpoint, connection);
        waitForProxy(error, new WaitCondition() {
            @Override
            public boolean isDone() {
                return accepted[0] || error[0] != null;
            }
        });
        if (error[0] != null) {
            SneakyUtils.throwUnchecked(error[0]);
        }
        if (!accepted[0]) {
            future.channel().close();
            SneakyUtils.throwUnchecked(new IOException("Timeout reached whilst waiting for hosted server registration."));
        }
        return connection;
    }

    private static void link(final MinecraftServer server, final ConnectHost endpoint, final JWebToken session, final String linkToken, final HostListener listener) {
        final NetworkManager networkManager = new NetworkManager(EnumPacketDirection.SERVERBOUND);
        HostConnection proxyConnection = new HostConnection(endpoint, listener) {
            @Override
            protected void buildPipeline(ChannelPipeline pipeline) {
                pipeline.addLast("mt:raw", new RawCodec());
                pipeline.addLast("splitter", new MessageDeserializer2());
                pipeline.addLast("decoder", new MessageDeserializer(EnumPacketDirection.SERVERBOUND));
                pipeline.addLast("prepender", new MessageSerializer2());
                pipeline.addLast("encoder", new MessageSerializer(EnumPacketDirection.CLIENTBOUND));
                pipeline.addLast("packet_handler", networkManager);
            }

            @Override
            protected void channelReady() {
                super.channelReady();
                networkManager.setNetHandler(new NetHandlerHandshakeTCP(server, networkManager));
                sendPacket(new SHostConnect(session.toString(), linkToken));
            }

            @Override
            protected void onDisconnected(String message) {
                LOGGER.warn("Link connection terminated: {}", message);
            }
        };
        openConnection(endpoint, proxyConnection);
        addNetworkManager(server.getNetworkSystem(), networkManager);
        listener.onServerLink(networkManager);
    }

    private static ChannelFuture openConnection(final ConnectHost endpoint, final HostConnection connection) {
        return new Bootstrap()
                .group(clientEventLoop())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        try {
                            ch.config().setOption(ChannelOption.TCP_NODELAY, true);
                        } catch (ChannelException ignored) {
                        }
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast("timeout", new ReadTimeoutHandler(240));
                        pipeline.addLast("mt:frame_codec", new FrameCodec());
                        pipeline.addLast("mt:packet_codec", new CompatPacketCodec());
                        if (Config.instance().dumpConnectPackets) {
                            pipeline.addLast("mt:logging_codec", new LoggingPacketCodec(LOGGER, true));
                        }
                        pipeline.addLast("mt:packet_handler", connection);
                        connection.buildPipeline(pipeline);
                    }
                })
                .connect(endpoint.getAddress(), endpoint.getProxyPort())
                .syncUninterruptibly();
    }

    private static EventLoopGroup clientEventLoop() {
        return NetworkManager.CLIENT_NIO_EVENTLOOP.getValue();
    }

    @SuppressWarnings("unchecked")
    private static void addNetworkManager(NetworkSystem networkSystem, NetworkManager networkManager) {
        try {
            List<NetworkManager> managers = (List<NetworkManager>) NETWORK_MANAGERS_FIELD.get(networkSystem);
            synchronized (managers) {
                managers.add(networkManager);
            }
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Unable to access NetworkSystem networkManagers", ex);
        }
    }

    private static Field findField(Class<?> owner, String deobfName, String srgName) {
        for (String name : new String[] { deobfName, srgName }) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new IllegalStateException("Could not find field " + deobfName + " on " + owner.getName());
    }

    private static void waitForProxy(Object monitor, WaitCondition condition) {
        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1);
        synchronized (monitor) {
            while (!condition.isDone()) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    return;
                }
                try {
                    monitor.wait(remaining);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted whilst waiting for the proxy.", ex);
                }
            }
        }
    }

    private interface WaitCondition {
        boolean isDone();
    }

    public interface HostListener {
        void onAccepted();

        void onDisconnected(String message);

        void onChannelInactive(boolean disconnectRequested);

        void onMaxPlayers(int maxPlayers, int proxyMaxPlayers);

        void onServerLink(NetworkManager connection);

        void onMessage(String message);
    }

    public static class HostConnection extends AbstractChannelHandler<ClientPacketHandler> implements ClientPacketHandler {

        private final ConnectHost endpoint;
        private final HostListener listener;
        private final byte[] nonce = new byte[32];
        private final SecretKey aesSecret;
        protected boolean disconnectRequested;

        public HostConnection(ConnectHost endpoint, HostListener listener) {
            this.endpoint = endpoint;
            this.listener = listener;
            new SecureRandom().nextBytes(nonce);
            this.aesSecret = AESUtils.generateAESKey();
        }

        protected void buildPipeline(ChannelPipeline pipeline) {
        }

        protected void channelReady() {
            sendPacket(new SAccepted());
        }

        protected void onDisconnected(String message) {
        }

        public void disconnect() {
            if (disconnectRequested) return;
            disconnectRequested = true;
            if (channel != null) channel.close();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Packet<ClientPacketHandler> packet) throws Exception {
            packet.handle(ctx, this);
        }

        @Override
        public final void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            sendPacket(new SHello(nonce, RSAUtils.encrypt(aesSecret.getEncoded(), endpoint.getPublicKey()), ProtocolVersions.PROTOCOL_VERSION));
        }

        @Override
        public void handleHello(ChannelHandlerContext ctx, CHello packet) {
            if (!RSAUtils.isValid(nonce, packet.signedNonce, endpoint.getPublicKey())) {
                LOGGER.error("Failed to validate MTConnect server signature.");
                onDisconnected("Handshake failed.");
                channel.close();
                return;
            }
            channel.pipeline().addBefore("mt:frame_codec", "aes_codec", new CipherCodec(
                    AESUtils.loadCipher(Cipher.ENCRYPT_MODE, aesSecret),
                    AESUtils.loadCipher(Cipher.DECRYPT_MODE, aesSecret)
            ));
            channelReady();
        }

        @Override
        public void handleFriendServers(ChannelHandlerContext ctx, CFriendServers packet) {
            LOGGER.warn("Received unexpected friend-server list on this proxy connection.");
        }

        @Override
        public final void handleDisconnect(ChannelHandlerContext ctx, CDisconnect packet) {
            LOGGER.error("Disconnected from MTConnect proxy: {}", packet.message);
            onDisconnected(packet.message);
            channel.close();
        }

        @Override
        public void handleAccepted(ChannelHandlerContext ctx, CAccepted packet) {
        }

        @Override
        public void handleMaxPlayers(ChannelHandlerContext ctx, CMaxPlayers packet) {
        }

        @Override
        public void handleServerLink(ChannelHandlerContext ctx, CServerLink packet) {
            LOGGER.warn("Received unexpected server link on this proxy connection.");
        }

        @Override
        public void handleBeginRaw(ChannelHandlerContext ctx, CBeginRaw packet) {
            LOGGER.warn("Received unexpected raw handoff on this proxy connection.");
        }

        @Override
        public void handleRaw(ChannelHandlerContext ctx, CRaw packet) {
            ctx.fireChannelRead(packet.data.retain());
        }

        @Override
        public void handlePong(ChannelHandlerContext ctx, CPong packet) {
        }

        @Override
        public void handleMessage(ChannelHandlerContext ctx, CMessage packet) {
            listener.onMessage(packet.message);
        }
    }
}
