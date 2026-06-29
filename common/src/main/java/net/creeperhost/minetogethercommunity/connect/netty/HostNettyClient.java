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
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.covers1624.quack.util.SneakyUtils;
import net.creeperhost.minetogether.connect.lib.netty.AbstractChannelHandler;
import net.creeperhost.minetogether.connect.lib.netty.CipherCodec;
import net.creeperhost.minetogether.connect.lib.netty.FrameCodec;
import net.creeperhost.minetogether.connect.lib.netty.LoggingPacketCodec;
import net.creeperhost.minetogether.connect.lib.netty.PacketCodec;
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
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.LegacyQueryHandler;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.function.Supplier;

public class HostNettyClient {

    private static final Logger LOGGER = LogManager.getLogger();

    public static HostConnection publishServer(MinecraftServer server, ConnectHost endpoint, JWebToken session, @Nullable String modpackKey, int maxPlayers, HostListener listener) {
        Throwable[] error = new Throwable[1];
        HostConnection connection = new HostConnection(endpoint, listener) {
            @Override
            public void channelReady() {
                super.channelReady();
                sendPacket(new SHostRegister(session.toString(), modpackKey));
            }

            @Override
            public void onDisconnected(String message) {
                error[0] = new IOException("Failed to host server: " + message);
                synchronized (error) {
                    error.notifyAll();
                }
                listener.onDisconnected(message);
            }

            @Override
            public void channelInactive(@NotNull ChannelHandlerContext ctx) throws Exception {
                super.channelInactive(ctx);
                listener.onChannelInactive(disconnectRequested);
            }

            @Override
            public void handleAccepted(ChannelHandlerContext ctx, CAccepted cAccepted) {
                super.handleAccepted(ctx, cAccepted);
                synchronized (error) {
                    error.notifyAll();
                }
                listener.onAccepted();
            }

            @Override
            public void handleMaxPlayers(ChannelHandlerContext channelHandlerContext, CMaxPlayers cMaxPlayers) {
                int playerCap = cMaxPlayers.maxPlayers > 0 ? cMaxPlayers.maxPlayers : Integer.MAX_VALUE;
                listener.onMaxPlayers(Math.min(playerCap, maxPlayers), cMaxPlayers.maxPlayers);
            }

            @Override
            public void handleServerLink(ChannelHandlerContext ctx, CServerLink packet) {
                link(server, endpoint, session, packet.linkToken, listener);
            }
        };
        ChannelFuture channelFuture = openConnection(
                endpoint,
                connection,
                ServerConnectionListener.SERVER_EPOLL_EVENT_GROUP::get,
                ServerConnectionListener.SERVER_EVENT_GROUP::get
        );

        synchronized (error) {
            try {
                error.wait();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted whilst waiting.", ex);
            }
        }
        if (error[0] != null) {
            SneakyUtils.throwUnchecked(error[0]);
        }

        ServerConnectionListener serverConnection = server.getConnection();
        assert serverConnection != null;

        synchronized (serverConnection.channels) {
            serverConnection.channels.add(channelFuture);
        }
        return connection;
    }

    private static void link(MinecraftServer server, ConnectHost endpoint, JWebToken session, String linkToken, HostListener listener) {
        ServerConnectionListener serverConnection = server.getConnection();
        assert serverConnection != null;

        Connection connection = new Connection(PacketFlow.SERVERBOUND);

        HostConnection proxyConnection = new HostConnection(endpoint, listener) {
            @Override
            protected void buildPipeline(ChannelPipeline pipeline) {
                pipeline.addLast("mt:raw", new RawCodec());
                pipeline.addLast("legacy_query", new LegacyQueryHandler(server));
                Connection.configureSerialization(pipeline, PacketFlow.SERVERBOUND, false, null);
                connection.configurePacketHandler(pipeline);
            }

            @Override
            public void channelReady() {
                super.channelReady();
                connection.setListenerForServerboundHandshake(new ServerHandshakePacketListenerImpl(server, connection));
                sendPacket(new SHostConnect(session.toString(), linkToken));
            }

            @Override
            public void onDisconnected(String message) {
                LOGGER.warn("Link connection terminated: {}", message);
            }
        };
        openConnection(
                endpoint,
                proxyConnection,
                ServerConnectionListener.SERVER_EPOLL_EVENT_GROUP::get,
                ServerConnectionListener.SERVER_EVENT_GROUP::get
        );

        synchronized (serverConnection.connections) {
            serverConnection.connections.add(connection);
        }
        listener.onServerLink(connection);
    }

    private static ChannelFuture openConnection(ConnectHost endpoint, HostConnection connection, Supplier<EventLoopGroup> epollGroup, Supplier<EventLoopGroup> nioGroup) {
        EventLoopGroup eventGroup;
        Class<? extends Channel> channelClass;
        if (Epoll.isAvailable()) {
            eventGroup = epollGroup.get();
            channelClass = EpollSocketChannel.class;
        } else {
            eventGroup = nioGroup.get();
            channelClass = NioSocketChannel.class;
        }

        return new Bootstrap()
                .group(eventGroup)
                .channel(channelClass)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(@NotNull Channel ch) throws Exception {
                        try {
                            ch.config().setOption(ChannelOption.TCP_NODELAY, true);
                        } catch (ChannelException ignored) {
                        }

                        ChannelPipeline pipe = ch.pipeline();
                        pipe.addLast("timeout", new ReadTimeoutHandler(240));
                        pipe.addLast("mt:frame_codec", new FrameCodec());
                        pipe.addLast("mt:packet_codec", new PacketCodec());
                        if (Config.instance().dumpConnectPackets) {
                            pipe.addLast("mt:logging_codec", new LoggingPacketCodec(LOGGER, true));
                        }
                        pipe.addLast("mt:packet_handler", connection);
                        connection.buildPipeline(pipe);

                    }
                })
                .connect(endpoint.address(), endpoint.proxyPort())
                .syncUninterruptibly();
    }

    public interface HostListener {

        default void onAccepted() {
        }

        default void onDisconnected(String message) {
        }

        default void onChannelInactive(boolean disconnectRequested) {
        }

        default void onMaxPlayers(int maxPlayers, int proxyMaxPlayers) {
        }

        default void onServerLink(Connection connection) {
        }

        default void onMessage(String message) {
        }
    }

    public static class HostConnection extends AbstractChannelHandler<ClientPacketHandler> implements ClientPacketHandler {

        private final ConnectHost endpoint;
        private final HostListener listener;
        private final byte[] nonce = new byte[32];
        private final SecretKey aesSecret;
        protected boolean disconnectRequested = false;

        public HostConnection(ConnectHost endpoint, HostListener listener) {
            this.endpoint = endpoint;
            this.listener = listener;
            new SecureRandom().nextBytes(nonce);
            aesSecret = AESUtils.generateAESKey();
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
            channel.close();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Packet<ClientPacketHandler> packet) throws Exception {
            packet.handle(ctx, this);
        }

        @Override
        public final void channelActive(@NotNull ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);

            sendPacket(new SHello(nonce, RSAUtils.encrypt(aesSecret.getEncoded(), endpoint.publicKey()), ProtocolVersions.PROTOCOL_VERSION));
        }

        @Override
        public void handleHello(ChannelHandlerContext ctx, CHello packet) {
            if (!RSAUtils.isValid(nonce, packet.signedNonce, endpoint.publicKey())) {
                LOGGER.error("Failed to validate server signature.");
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
        public void handleFriendServers(ChannelHandlerContext channelHandlerContext, CFriendServers cFriendServers) {
            throw new NotImplementedException();
        }

        @Override
        public final void handleDisconnect(ChannelHandlerContext ctx, CDisconnect packet) {
            LOGGER.error("Disconnected from proxy: {}", packet.message);
            onDisconnected(packet.message);
        }

        @Override
        public void handleAccepted(ChannelHandlerContext ctx, CAccepted cAccepted) {
        }

        @Override
        public void handleMaxPlayers(ChannelHandlerContext channelHandlerContext, CMaxPlayers cMaxPlayers) {
        }

        @Override
        public void handleServerLink(ChannelHandlerContext ctx, CServerLink packet) {
            throw new NotImplementedException();
        }

        @Override
        public void handleBeginRaw(ChannelHandlerContext ctx, CBeginRaw packet) {
            throw new NotImplementedException();
        }

        @Override
        public void handleRaw(ChannelHandlerContext ctx, CRaw packet) {
            ctx.fireChannelRead(packet.data);
        }

        @Override
        public void handlePong(ChannelHandlerContext channelHandlerContext, CPong cPong) {
        }

        @Override
        public void handleMessage(ChannelHandlerContext ctx, CMessage packet) {
            listener.onMessage(packet.message);
        }
    }
}
