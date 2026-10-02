package net.creeperhost.minetogethercommunity.ci.connectservice;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.creeperhost.minetogether.connect.lib.netty.AbstractChannelHandler;
import net.creeperhost.minetogether.connect.lib.netty.CipherCodec;
import net.creeperhost.minetogether.connect.lib.netty.NetworkAttrs;
import net.creeperhost.minetogether.connect.lib.netty.ProtocolVersions;
import net.creeperhost.minetogether.connect.lib.netty.packet.CDisconnect;
import net.creeperhost.minetogether.connect.lib.netty.packet.CHello;
import net.creeperhost.minetogether.connect.lib.netty.packet.CPong;
import net.creeperhost.minetogether.connect.lib.netty.packet.Packet;
import net.creeperhost.minetogether.connect.lib.netty.packet.SAccepted;
import net.creeperhost.minetogether.connect.lib.netty.packet.SHello;
import net.creeperhost.minetogether.connect.lib.netty.packet.SHostConnect;
import net.creeperhost.minetogether.connect.lib.netty.packet.SHostRegister;
import net.creeperhost.minetogether.connect.lib.netty.packet.SMeshConnect;
import net.creeperhost.minetogether.connect.lib.netty.packet.SPing;
import net.creeperhost.minetogether.connect.lib.netty.packet.SRaw;
import net.creeperhost.minetogether.connect.lib.netty.packet.SRequestFriendServers;
import net.creeperhost.minetogether.connect.lib.netty.packet.SRequestMaxPlayers;
import net.creeperhost.minetogether.connect.lib.netty.packet.SUserConnect;
import net.creeperhost.minetogether.connect.lib.netty.packet.ServerPacketHandler;
import net.creeperhost.minetogether.connect.lib.util.AESUtils;
import net.creeperhost.minetogether.connect.lib.util.RSAUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.util.concurrent.TimeUnit;

final class ProxyClientConnection extends AbstractChannelHandler<ServerPacketHandler>
        implements ServerPacketHandler, ServicePeer {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final long LINK_TIMEOUT_SECONDS = 15;

    private final String id;
    private final PrivateKey privateKey;
    private final ConnectRegistry registry;
    private State state = State.HANDSHAKE;

    ProxyClientConnection(String id, PrivateKey privateKey, ConnectRegistry registry) {
        this.id = id;
        this.privateKey = privateKey;
        this.registry = registry;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet<ServerPacketHandler> packet) throws Exception {
        packet.handle(ctx, this);
    }

    @Override
    public void channelInactive(@NotNull ChannelHandlerContext ctx) throws Exception {
        state = State.CLOSED;
        registry.disconnected(this);
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("Local MTConnect peer {} failed", id, cause);
        ctx.close();
    }

    @Override
    public void handleHello(ChannelHandlerContext ctx, SHello packet) {
        if (state != State.HANDSHAKE) {
            ctx.close();
            return;
        }
        if (packet.protocolVersion < ProtocolVersions.MINIMUM_PROTOCOL_VERSION
                || packet.protocolVersion > ProtocolVersions.PROTOCOL_VERSION) {
            LOGGER.warn("Rejecting MTConnect peer {} with unsupported protocol {}", id, packet.protocolVersion);
            ctx.close();
            return;
        }

        final SecretKey secret;
        try {
            secret = AESUtils.readSecretKey(RSAUtils.decrypt(packet.encryptedAESKey, privateKey));
        } catch (RuntimeException exception) {
            LOGGER.warn("Rejecting MTConnect peer {} with an invalid encrypted key", id);
            ctx.close();
            return;
        }

        ctx.channel().attr(NetworkAttrs.PROTOCOL_VERSION_ATTR).set(packet.protocolVersion);
        // writeAndFlush traverses outbound codecs synchronously, so CHello is framed in plaintext
        // before the stream cipher is inserted for every subsequent inbound/outbound byte.
        sendPacket(new CHello(RSAUtils.sign(packet.nonce, privateKey)));
        ChannelPipeline pipeline = ctx.pipeline();
        pipeline.addBefore("mt:frame_codec", "mt:aes_codec", new CipherCodec(
                AESUtils.loadCipher(Cipher.ENCRYPT_MODE, secret),
                AESUtils.loadCipher(Cipher.DECRYPT_MODE, secret)
        ));
        state = State.ENCRYPTED;
    }

    @Override
    public void handleAccepted(ChannelHandlerContext ctx, SAccepted packet) {
        if (state != State.ENCRYPTED) {
            protocolError("Unexpected SAccepted");
            return;
        }
        state = State.READY;
        configureKeepAlivePing();
    }

    @Override
    public void handleRequestFriendServers(ChannelHandlerContext ctx, SRequestFriendServers packet) {
        if (!claimReady(State.TEMPORARY)) return;
        withIdentity(packet.sessionToken, identity -> registry.discover(this, identity, packet.modpackKey));
    }

    @Override
    public void handleHostRegister(ChannelHandlerContext ctx, SHostRegister packet) {
        if (!claimReady(State.HOST_CONTROL)) return;
        withIdentity(packet.sessionToken, identity -> registry.registerHost(this, identity, packet.modpackKey));
    }

    @Override
    public void handleRequestMaxPlayers(ChannelHandlerContext ctx, SRequestMaxPlayers packet) {
        if (!claimReady(State.TEMPORARY)) return;
        withIdentity(packet.sessionToken, identity -> registry.requestMaxPlayers(this, identity));
    }

    @Override
    public void handleHostConnect(ChannelHandlerContext ctx, SHostConnect packet) {
        if (!claimReady(State.HOST_LINK)) return;
        withIdentity(packet.sessionToken, identity -> {
            registry.linkHost(this, identity, packet.linkToken);
            if (isActive()) state = State.RELAY;
        });
    }

    @Override
    public void handleUserConnect(ChannelHandlerContext ctx, SUserConnect packet) {
        if (!claimReady(State.USER_PENDING)) return;
        withIdentity(packet.sessionToken, identity -> {
            String linkToken = registry.requestUser(this, identity, packet.serverToken, packet.isServerListQuery);
            if (linkToken != null) {
                channel.eventLoop().schedule(() -> registry.expirePending(linkToken), LINK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        });
    }

    @Override
    public void handleMeshConnect(ChannelHandlerContext ctx, SMeshConnect packet) {
        protocolError("Mesh connections are not supported by the local test service");
    }

    @Override
    public void handleRaw(ChannelHandlerContext ctx, SRaw packet) {
        if (state != State.USER_PENDING && state != State.HOST_LINK && state != State.RELAY) {
            packet.data.release();
            protocolError("Raw data arrived before a relay was linked");
            return;
        }
        state = State.RELAY;
        registry.relay(this, packet.data);
    }

    @Override
    public void handlePing(ChannelHandlerContext ctx, SPing packet) {
        write(new CPong());
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean isActive() {
        return channel != null && channel.isActive();
    }

    @Override
    public void write(Packet<?> packet) {
        if (!isActive()) {
            releaseRaw(packet);
            return;
        }
        sendPacket(packet);
    }

    @Override
    public void writeAndClose(Packet<?> packet) {
        if (!isActive()) {
            releaseRaw(packet);
            return;
        }
        sendAndClose(packet);
    }

    @Override
    public void close() {
        if (channel != null) channel.close();
    }

    private boolean claimReady(State claimed) {
        if (state != State.READY) {
            protocolError("Operation arrived before SAccepted or more than once");
            return false;
        }
        state = claimed;
        return true;
    }

    private void withIdentity(String token, java.util.function.Consumer<Identity> action) {
        try {
            action.accept(Identity.parse(token));
        } catch (RuntimeException exception) {
            LOGGER.warn("Rejecting MTConnect peer {} with invalid local test identity: {}", id, exception.getMessage());
            writeAndClose(new CDisconnect("Invalid MineTogether test session."));
        }
    }

    private void protocolError(String message) {
        LOGGER.warn("Local MTConnect protocol error from {}: {}", id, message);
        writeAndClose(new CDisconnect(message));
    }

    private static void releaseRaw(Packet<?> packet) {
        if (packet instanceof net.creeperhost.minetogether.connect.lib.netty.packet.CRaw raw
                && raw.data.refCnt() > 0) {
            raw.data.release();
        }
    }

    private enum State {
        HANDSHAKE,
        ENCRYPTED,
        READY,
        TEMPORARY,
        HOST_CONTROL,
        USER_PENDING,
        HOST_LINK,
        RELAY,
        CLOSED
    }
}
