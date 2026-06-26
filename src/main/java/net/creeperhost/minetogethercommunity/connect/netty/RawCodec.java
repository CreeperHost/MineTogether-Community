package net.creeperhost.minetogethercommunity.connect.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import net.creeperhost.minetogether.connect.lib.netty.packet.CRaw;
import net.creeperhost.minetogether.connect.lib.netty.packet.Packet;
import net.creeperhost.minetogether.connect.lib.netty.packet.SRaw;

import java.util.List;

public class RawCodec extends MessageToMessageCodec<Packet<?>, ByteBuf> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
        out.add(new SRaw(msg.copy()));
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, Packet<?> msg, List<Object> out) {
        if (msg instanceof CRaw) {
            out.add(((CRaw) msg).data);
        }
    }
}
