package net.creeperhost.minetogethercommunity.connect.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.creeperhost.minetogether.connect.lib.netty.packet.Packet;

/**
 * Keeps MTConnect control packets out of the trailing Minecraft encoders.
 *
 * <p>The proxy library's keepalive handler writes Pong from the channel tail.
 * A relayed connection also has vanilla packet encoders after the MTConnect
 * handler, so on Netty 4.0 the Pong can be consumed by the wrong encoder and
 * close an otherwise healthy channel. Route only MTConnect packets back to the
 * MTConnect handler context; ordinary Minecraft writes retain their normal
 * outbound path.</p>
 */
final class ProxyPacketOutboundRouter extends ChannelOutboundHandlerAdapter {

    private final String proxyHandlerName;

    ProxyPacketOutboundRouter(String proxyHandlerName) {
        this.proxyHandlerName = proxyHandlerName;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof Packet) {
            ChannelHandlerContext proxyContext = ctx.pipeline().context(proxyHandlerName);
            if (proxyContext == null) {
                promise.setFailure(new IllegalStateException("Missing MTConnect proxy handler: " + proxyHandlerName));
                return;
            }
            proxyContext.write(msg, promise);
            return;
        }
        ctx.write(msg, promise);
    }
}
