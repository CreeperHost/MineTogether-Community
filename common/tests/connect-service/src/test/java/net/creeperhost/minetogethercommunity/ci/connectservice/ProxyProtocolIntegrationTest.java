package net.creeperhost.minetogethercommunity.ci.connectservice;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import net.creeperhost.minetogether.connect.lib.netty.AbstractChannelHandler;
import net.creeperhost.minetogether.connect.lib.netty.CipherCodec;
import net.creeperhost.minetogether.connect.lib.netty.FrameCodec;
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
import net.creeperhost.minetogether.connect.lib.netty.packet.SRequestMaxPlayers;
import net.creeperhost.minetogether.connect.lib.util.AESUtils;
import net.creeperhost.minetogether.connect.lib.util.RSAUtils;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProxyProtocolIntegrationTest {

    @Test
    void negotiatesProtocolFiveEncryptionAndAnswersAnEncryptedRequest() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        ConnectRegistry registry = new ConnectRegistry();
        EventLoopGroup clientGroup = new NioEventLoopGroup(1);

        try (ProxyServer server = new ProxyServer("127.0.0.1", 0, keys.getPrivate(), registry)) {
            int port = server.start();
            TestClient handler = new TestClient(keys.getPublic());
            Channel channel = new Bootstrap()
                    .group(clientGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            channel.pipeline().addLast("mt:frame_codec", new FrameCodec());
                            channel.pipeline().addLast("mt:packet_codec", new PacketCodec());
                            channel.pipeline().addLast("mt:packet_handler", handler);
                        }
                    })
                    .connect("127.0.0.1", port)
                    .sync()
                    .channel();

            assertEquals(AccessPolicy.DEFAULT_MAX_PLAYERS, handler.result.get(10, TimeUnit.SECONDS));
            channel.close().sync();
        } finally {
            clientGroup.shutdownGracefully().sync();
        }
    }

    private static final class TestClient extends AbstractChannelHandler<ClientPacketHandler>
            implements ClientPacketHandler {

        private final PublicKey publicKey;
        private final byte[] nonce = new byte[32];
        private final SecretKey secret = AESUtils.generateAESKey();
        private final CompletableFuture<Integer> result = new CompletableFuture<>();

        private TestClient(PublicKey publicKey) {
            this.publicKey = publicKey;
            new SecureRandom().nextBytes(nonce);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            sendPacket(new SHello(nonce, RSAUtils.encrypt(secret.getEncoded(), publicKey),
                    ProtocolVersions.PROTOCOL_VERSION));
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Packet<ClientPacketHandler> packet) throws Exception {
            packet.handle(ctx, this);
        }

        @Override
        public void handleHello(ChannelHandlerContext ctx, CHello packet) {
            if (!RSAUtils.isValid(nonce, packet.signedNonce, publicKey)) {
                result.completeExceptionally(new IllegalStateException("Invalid server signature"));
                return;
            }
            ctx.pipeline().addBefore("mt:frame_codec", "mt:aes_codec", new CipherCodec(
                    AESUtils.loadCipher(Cipher.ENCRYPT_MODE, secret),
                    AESUtils.loadCipher(Cipher.DECRYPT_MODE, secret)
            ));
            UUID uuid = UUID.fromString("50000000-0000-4000-8000-000000000005");
            sendPacket(new SAccepted());
            sendPacket(new SRequestMaxPlayers(IdentityTest.token(uuid, "Protocol Client")));
        }

        @Override
        public void handleMaxPlayers(ChannelHandlerContext ctx, CMaxPlayers packet) {
            result.complete(packet.maxPlayers);
        }

        @Override
        public void handleDisconnect(ChannelHandlerContext ctx, CDisconnect packet) {
            result.completeExceptionally(new IllegalStateException(packet.message));
        }

        @Override public void handleFriendServers(ChannelHandlerContext ctx, CFriendServers packet) { }
        @Override public void handleAccepted(ChannelHandlerContext ctx, CAccepted packet) { }
        @Override public void handleServerLink(ChannelHandlerContext ctx, CServerLink packet) { }
        @Override public void handleBeginRaw(ChannelHandlerContext ctx, CBeginRaw packet) { }
        @Override public void handlePong(ChannelHandlerContext ctx, CPong packet) { }
        @Override public void handleMessage(ChannelHandlerContext ctx, CMessage packet) { }

        @Override
        public void handleRaw(ChannelHandlerContext ctx, CRaw packet) {
            packet.data.release();
        }
    }
}
