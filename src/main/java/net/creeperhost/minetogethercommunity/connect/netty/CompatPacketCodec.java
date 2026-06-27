package net.creeperhost.minetogethercommunity.connect.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.util.AttributeKey;
import net.creeperhost.minetogether.connect.lib.netty.PacketCtx;
import net.creeperhost.minetogether.connect.lib.netty.PacketType;
import net.creeperhost.minetogether.connect.lib.netty.packet.Packet;
import net.creeperhost.minetogether.connect.lib.netty.packet.SHello;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.function.Function;

public class CompatPacketCodec extends ByteToMessageCodec<Packet> {

    private static final Logger LOGGER = LogManager.getLogger();
    // Minecraft 1.7.10 ships Netty 4.0.10, which lacks AttributeKey.valueOf used by MTConnectProxyCommon's PacketCodec.
    private static final AttributeKey<Integer> PROTOCOL_VERSION_ATTR = new AttributeKey<Integer>("minetogethercommunity:mtconnect_protocol_version");

    public CompatPacketCodec() {
        super(Packet.class);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Packet packet, ByteBuf out) throws Exception {
        Integer protocolVersion = ctx.channel().attr(PROTOCOL_VERSION_ATTR).get();
        PacketType.PacketHandle handle = PacketType.getPacketHandle(packet);
        if (protocolVersion != null && handle.requiresVersion > protocolVersion.intValue()) {
            LOGGER.info("Skipping packet {} to {}. Wrong protocol version, Has: {} Requires: {}",
                    packet.getClass().getName(),
                    ctx.channel().remoteAddress(),
                    protocolVersion,
                    Integer.valueOf(handle.requiresVersion));
            return;
        }
        out.writeByte(handle.id);
        packet.write(out);
        if (packet instanceof SHello) {
            ctx.channel().attr(PROTOCOL_VERSION_ATTR).set(Integer.valueOf(((SHello) packet).protocolVersion));
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int packetId = in.readUnsignedByte();
        Integer protocolVersion = ctx.channel().attr(PROTOCOL_VERSION_ATTR).get();
        Function<PacketCtx, ? extends Packet<?>> factory = PacketType.getPacketFactory(packetId);
        out.add(factory.apply(new PacketCtx(in, protocolVersion == null ? -1 : protocolVersion.intValue())));
    }
}
