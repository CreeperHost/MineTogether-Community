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
import net.creeperhost.minetogether.connect.lib.netty.packet.SRequestFriendServers;
import net.creeperhost.minetogether.connect.lib.netty.packet.SRequestMaxPlayers;
import net.creeperhost.minetogether.connect.lib.netty.packet.SUserConnect;
import net.creeperhost.minetogether.connect.lib.util.AESUtils;
import net.creeperhost.minetogether.connect.lib.util.RSAUtils;
import net.creeperhost.minetogether.session.JWebToken;
import net.creeperhost.minetogethercommunity.config.Config;
import net.creeperhost.minetogethercommunity.connect.ConnectHandler;
import net.creeperhost.minetogethercommunity.connect.ConnectHost;
import net.creeperhost.minetogethercommunity.util.DiagnosticLog;
import net.minecraft.client.Minecraft;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.NetworkSystem;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.NetHandlerHandshakeTCP;
import net.minecraft.util.MessageDeserializer;
import net.minecraft.util.MessageDeserializer2;
import net.minecraft.util.MessageSerializer;
import net.minecraft.util.MessageSerializer2;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NettyClient {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Connect");
    private static final Field NETWORK_MANAGERS_FIELD = findField(NetworkSystem.class, "networkManagers", "field_151272_f");

    public static ProxyConnection publishServer(final IntegratedServer server, final ConnectHost endpoint, final JWebToken session, final String modpackKey, final int maxPlayers) {
        final Throwable[] error = new Throwable[1];
        final boolean[] accepted = new boolean[] { false };
        ProxyConnection connection = new ProxyConnection(endpoint) {
            private boolean disconnectRequested;

            @Override
            protected void channelReady() {
                super.channelReady();
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] proxy handshake ready; registering hosted server endpoint={}:{} session={} modpackKey={} maxPlayers={}",
                        endpoint.getAddress(), Integer.valueOf(endpoint.getProxyPort()), ConnectHandler.describeToken(session),
                        ConnectHandler.describeModpackKey(modpackKey), Integer.valueOf(maxPlayers));
                sendPacket(new SHostRegister(session.toString(), modpackKey));
            }

            @Override
            protected void onDisconnected(String message) {
                error[0] = new IOException("Failed to host server: " + message);
                synchronized (error) {
                    error.notifyAll();
                }
                sendChat("minetogether.connect.open.failed", message);
                ConnectHandler.unPublish();
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                super.channelInactive(ctx);
                if (disconnectRequested) {
                    sendChat("minetogether.connect.open.proxy.closed");
                } else {
                    sendChat("minetogether.connect.open.proxy.disconnect");
                    ConnectHandler.unPublish();
                }
            }

            @Override
            public void handleAccepted(ChannelHandlerContext ctx, CAccepted packet) {
                super.handleAccepted(ctx, packet);
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] hosted server registration accepted by MTConnect proxy");
                synchronized (error) {
                    accepted[0] = true;
                    error.notifyAll();
                }
                sendChat("minetogether.connect.open.success");
            }

            @Override
            public void handleMaxPlayers(ChannelHandlerContext ctx, CMaxPlayers packet) {
                int playerCap = packet.maxPlayers > 0 ? packet.maxPlayers : Integer.MAX_VALUE;
                ConnectHandler.setServerMaxPlayers(server, Math.min(playerCap, maxPlayers));
            }

            @Override
            public void handleServerLink(ChannelHandlerContext ctx, CServerLink packet) {
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] proxy requested hosted server back-link endpoint={}:{} linkToken={}",
                        endpoint.getAddress(), Integer.valueOf(endpoint.getProxyPort()), ConnectHandler.describeServerToken(packet.linkToken));
                link(server, endpoint, session, packet.linkToken);
            }

            @Override
            public void disconnect() {
                if (disconnectRequested) return;
                disconnectRequested = true;
                if (channel != null) channel.close();
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
            throwUnchecked(error[0]);
        }
        if (!accepted[0]) {
            future.channel().close();
            throwUnchecked(new IOException("Timeout reached whilst waiting for hosted server registration."));
        }
        return connection;
    }

    public static int getMaxPlayers(ConnectHost endpoint, final JWebToken session) throws IOException {
        final IOException[] error = new IOException[1];
        final int[] result = new int[] { -2 };

        ProxyConnection connection = new ProxyConnection(endpoint) {
            @Override
            protected void channelReady() {
                super.channelReady();
                sendPacket(new SRequestMaxPlayers(session.toString()));
            }

            @Override
            public void handleMaxPlayers(ChannelHandlerContext ctx, CMaxPlayers packet) {
                result[0] = packet.maxPlayers;
                synchronized (error) {
                    error.notifyAll();
                }
                channel.close();
            }

            @Override
            protected void onDisconnected(String message) {
                error[0] = new IOException("Failed to get max players: " + message);
                synchronized (error) {
                    error.notifyAll();
                }
            }
        };

        ChannelFuture future = openConnection(endpoint, connection);
        waitForProxy(error, new WaitCondition() {
            @Override
            public boolean isDone() {
                return result[0] != -2 || error[0] != null;
            }
        });
        if (error[0] != null) throw error[0];
        if (result[0] == -2) {
            future.channel().close();
            throw new IOException("Timeout reached whilst waiting for server response.");
        }
        return result[0];
    }

    public static CFriendServers getFriendServers(ConnectHost endpoint, final JWebToken session, final String modpackKey) throws IOException {
        final IOException[] error = new IOException[1];
        final CFriendServers[] result = new CFriendServers[1];

        ProxyConnection connection = new ProxyConnection(endpoint) {
            @Override
            protected void channelReady() {
                super.channelReady();
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] proxy handshake ready; requesting friend servers endpoint={}:{} session={} modpackKey={}",
                        endpoint.getAddress(), Integer.valueOf(endpoint.getProxyPort()), ConnectHandler.describeToken(session),
                        ConnectHandler.describeModpackKey(modpackKey));
                sendPacket(new SRequestFriendServers(session.toString(), modpackKey));
            }

            @Override
            public void handleFriendServers(ChannelHandlerContext ctx, CFriendServers packet) {
                int count = packet == null || packet.servers == null ? 0 : packet.servers.size();
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] proxy returned friend-server packet: count={}", Integer.valueOf(count));
                if (packet != null && packet.servers != null) {
                    int maxLogged = Math.min(count, 10);
                    for (int i = 0; i < maxLogged; i++) {
                        CFriendServers.ServerEntry entry = packet.servers.get(i);
                        if (entry == null) {
                            DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] proxy friend-server packet entry index={} <null>", Integer.valueOf(i));
                            continue;
                        }
                        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] proxy friend-server packet entry index={} friend={} serverToken={} node={}",
                                Integer.valueOf(i), ConnectHandler.describeFriendHash(entry.friend),
                                ConnectHandler.describeServerToken(entry.serverToken),
                                entry.node == null || entry.node.trim().isEmpty() ? "<local>" : entry.node);
                    }
                    if (count > maxLogged) {
                        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] proxy friend-server packet entry log truncated total={}", Integer.valueOf(count));
                    }
                }
                result[0] = packet;
                synchronized (error) {
                    error.notifyAll();
                }
                channel.close();
            }

            @Override
            protected void onDisconnected(String message) {
                DiagnosticLog.warn(LOGGER, "[MT-1710-DIAG] proxy disconnected while listing friend servers: {}", message);
                error[0] = new IOException("Failed to list friend servers: " + message);
                synchronized (error) {
                    error.notifyAll();
                }
            }
        };

        ChannelFuture future = openConnection(endpoint, connection);
        waitForProxy(error, new WaitCondition() {
            @Override
            public boolean isDone() {
                return result[0] != null || error[0] != null;
            }
        });
        if (error[0] != null) throw error[0];
        if (result[0] == null) {
            future.channel().close();
            throw new IOException("Timeout reached whilst waiting for server response.");
        }
        return result[0];
    }

    public static NetworkManager connect(final ConnectHost endpoint, final JWebToken session, final String serverToken, final boolean isQuery) throws IOException {
        final boolean[] connecting = new boolean[] { true };
        final boolean[] rawStarted = new boolean[] { false };
        final Throwable[] error = new Throwable[1];
        final NetworkManager networkManager = new NetworkManager(true);

        ProxyConnection proxyConnection = new ProxyConnection(endpoint) {
            private boolean loggedRawPacket;

            @Override
            protected void buildPipeline(ChannelPipeline pipeline) {
                pipeline.addLast("mt:raw", new RawCodec());
                pipeline.addLast("splitter", new MessageDeserializer2());
                pipeline.addLast("decoder", new MessageDeserializer(NetworkManager.STATISTICS));
                pipeline.addLast("prepender", new MessageSerializer2());
                pipeline.addLast("encoder", new MessageSerializer(NetworkManager.STATISTICS));
                pipeline.addLast("packet_handler", networkManager);
            }

            @Override
            protected void channelReady() {
                super.channelReady();
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] proxy handshake ready; requesting friend-server raw connection endpoint={}:{} session={} serverToken={} query={}",
                        endpoint.getAddress(), Integer.valueOf(endpoint.getProxyPort()), ConnectHandler.describeToken(session),
                        ConnectHandler.describeServerToken(serverToken), Boolean.valueOf(isQuery));
                sendPacket(new SUserConnect(session.toString(), serverToken, isQuery));
            }

            @Override
            protected void onDisconnected(String message) {
                if (connecting[0]) {
                    error[0] = new IOException("Failed to connect to friend server: " + message);
                    synchronized (error) {
                        error.notifyAll();
                    }
                } else {
                    networkManager.closeChannel(new TextComponentString(message));
                }
            }

            @Override
            public void handleAccepted(ChannelHandlerContext ctx, CAccepted packet) {
                super.handleAccepted(ctx, packet);
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] friend-server user connection accepted endpoint={}:{} serverToken={} query={}",
                        endpoint.getAddress(), Integer.valueOf(endpoint.getProxyPort()),
                        ConnectHandler.describeServerToken(serverToken), Boolean.valueOf(isQuery));
            }

            @Override
            public void handleBeginRaw(ChannelHandlerContext ctx, CBeginRaw packet) {
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] friend-server raw handoff started endpoint={}:{} serverToken={} query={}",
                        endpoint.getAddress(), Integer.valueOf(endpoint.getProxyPort()),
                        ConnectHandler.describeServerToken(serverToken), Boolean.valueOf(isQuery));
                synchronized (error) {
                    rawStarted[0] = true;
                    error.notifyAll();
                }
            }

            @Override
            protected void onRawPacket(int readableBytes) {
                if (loggedRawPacket) return;
                loggedRawPacket = true;
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] friend-server first raw packet received endpoint={}:{} serverToken={} query={} bytes={}",
                        endpoint.getAddress(), Integer.valueOf(endpoint.getProxyPort()),
                        ConnectHandler.describeServerToken(serverToken), Boolean.valueOf(isQuery), Integer.valueOf(readableBytes));
            }
        };

        ChannelFuture future = openConnection(endpoint, proxyConnection);
        waitForProxy(error, new WaitCondition() {
            @Override
            public boolean isDone() {
                return rawStarted[0] || error[0] != null;
            }
        });
        if (error[0] != null) {
            future.channel().close();
            if (error[0] instanceof IOException) throw (IOException) error[0];
            throwUnchecked(error[0]);
        }
        if (!rawStarted[0]) {
            future.channel().close();
            throw new IOException("Timeout reached whilst waiting for proxy raw connection.");
        }
        connecting[0] = false;
        return networkManager;
    }

    private static void link(final IntegratedServer server, final ConnectHost endpoint, final JWebToken session, final String linkToken) {
        final NetworkManager networkManager = new RelayedServerNetworkManager();
        ProxyConnection connection = new ProxyConnection(endpoint) {
            private boolean loggedRawPacket;

            @Override
            protected void buildPipeline(ChannelPipeline pipeline) {
                pipeline.addLast("mt:raw", new RawCodec());
                pipeline.addLast("splitter", new MessageDeserializer2());
                pipeline.addLast("decoder", new MessageDeserializer(NetworkManager.STATISTICS));
                pipeline.addLast("prepender", new MessageSerializer2());
                pipeline.addLast("encoder", new MessageSerializer(NetworkManager.STATISTICS));
                pipeline.addLast("packet_handler", networkManager);
            }

            @Override
            protected void channelReady() {
                super.channelReady();
                networkManager.setNetHandler(new NetHandlerHandshakeTCP(server, networkManager));
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] proxy handshake ready; registering hosted server back-link endpoint={}:{} session={} linkToken={}",
                        endpoint.getAddress(), Integer.valueOf(endpoint.getProxyPort()), ConnectHandler.describeToken(session),
                        ConnectHandler.describeServerToken(linkToken));
                sendPacket(new SHostConnect(session.toString(), linkToken));
            }

            @Override
            protected void onDisconnected(String message) {
                DiagnosticLog.warn(LOGGER, "[MT-1710-DIAG] hosted server back-link terminated endpoint={}:{} linkToken={} reason={}",
                        endpoint.getAddress(), Integer.valueOf(endpoint.getProxyPort()),
                        ConnectHandler.describeServerToken(linkToken), message);
            }

            @Override
            public void handleAccepted(ChannelHandlerContext ctx, CAccepted packet) {
                super.handleAccepted(ctx, packet);
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] hosted server back-link accepted endpoint={}:{} linkToken={}",
                        endpoint.getAddress(), Integer.valueOf(endpoint.getProxyPort()), ConnectHandler.describeServerToken(linkToken));
            }

            @Override
            protected void onRawPacket(int readableBytes) {
                if (loggedRawPacket) return;
                loggedRawPacket = true;
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] hosted server back-link first raw packet endpoint={}:{} linkToken={} bytes={}",
                        endpoint.getAddress(), Integer.valueOf(endpoint.getProxyPort()),
                        ConnectHandler.describeServerToken(linkToken), Integer.valueOf(readableBytes));
            }
        };

        openConnection(endpoint, connection);
        addNetworkManager(server.getNetworkSystem(), networkManager);
        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] injected hosted server back-link NetworkManager endpoint={}:{} linkToken={}",
                endpoint.getAddress(), Integer.valueOf(endpoint.getProxyPort()), ConnectHandler.describeServerToken(linkToken));
    }

    /**
     * Vanilla changes a server-side connection to PLAY from the login thread.
     * A proxy-backed connection runs on the MTConnect event loop instead, and
     * Netty 4.0 rejects pipeline mutations from that foreign thread. Marshal the
     * state transition onto the channel event loop just like a normal socket.
     */
    private static final class RelayedServerNetworkManager extends NetworkManager {
        private RelayedServerNetworkManager() {
            super(false);
        }

        @Override
        public void setConnectionState(final EnumConnectionState state) {
            if (state == EnumConnectionState.PLAY && channel() != null && !channel().eventLoop().inEventLoop()) {
                channel().eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        RelayedServerNetworkManager.super.setConnectionState(state);
                    }
                });
                return;
            }
            super.setConnectionState(state);
        }
    }

    private static ChannelFuture openConnection(final ConnectHost endpoint, final ProxyConnection connection) {
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
        return NetworkManager.eventLoops;
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

    private static void sendChat(final String key, final Object... args) {
        net.creeperhost.minetogethercommunity.util.ClientTaskRunner.run(new Runnable() {
            @Override
            public void run() {
                if (Minecraft.getMinecraft().ingameGUI != null) {
                    Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(new TextComponentTranslation(key, args));
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable throwable) throws E {
        throw (E) throwable;
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

    public static class ProxyConnection extends AbstractChannelHandler<ClientPacketHandler> implements ClientPacketHandler {

        private final ConnectHost endpoint;
        private final byte[] nonce = new byte[32];
        private final SecretKey aesSecret;

        public ProxyConnection(ConnectHost endpoint) {
            this.endpoint = endpoint;
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
            DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] MTConnect proxy handshake validated for endpoint={}:{}",
                    endpoint.getAddress(), Integer.valueOf(endpoint.getProxyPort()));
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
            onRawPacket(packet.data.readableBytes());
            ctx.fireChannelRead(packet.data.retain());
        }

        @Override
        public void handlePong(ChannelHandlerContext ctx, CPong packet) {
        }

        @Override
        public void handleMessage(ChannelHandlerContext ctx, CMessage packet) {
            if (Minecraft.getMinecraft().thePlayer != null) {
                net.creeperhost.minetogethercommunity.util.ClientTaskRunner.run(new Runnable() {
                    @Override
                    public void run() {
                        Minecraft.getMinecraft().thePlayer.addChatMessage(new TextComponentString("[MTConnect Broadcast] " + packet.message));
                    }
                });
            }
        }

        protected void onRawPacket(int readableBytes) {
        }
    }
}
