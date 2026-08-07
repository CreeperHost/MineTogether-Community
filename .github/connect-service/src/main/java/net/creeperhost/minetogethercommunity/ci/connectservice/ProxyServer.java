package net.creeperhost.minetogethercommunity.ci.connectservice;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.creeperhost.minetogether.connect.lib.netty.FrameCodec;
import net.creeperhost.minetogether.connect.lib.netty.PacketCodec;

import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.util.concurrent.atomic.AtomicLong;

final class ProxyServer implements AutoCloseable {

    private final String bindAddress;
    private final int requestedPort;
    private final PrivateKey privateKey;
    private final ConnectRegistry registry;
    private final AtomicLong peerSequence = new AtomicLong();
    private EventLoopGroup boss;
    private EventLoopGroup workers;
    private Channel serverChannel;

    ProxyServer(String bindAddress, int requestedPort, PrivateKey privateKey, ConnectRegistry registry) {
        this.bindAddress = bindAddress;
        this.requestedPort = requestedPort;
        this.privateKey = privateKey;
        this.registry = registry;
    }

    int start() {
        boss = new NioEventLoopGroup(1);
        workers = new NioEventLoopGroup();
        serverChannel = new ServerBootstrap()
                .group(boss, workers)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        String peerId = "peer-" + peerSequence.incrementAndGet();
                        ChannelPipeline pipeline = channel.pipeline();
                        pipeline.addLast("timeout", new ReadTimeoutHandler(240));
                        pipeline.addLast("mt:frame_codec", new FrameCodec());
                        pipeline.addLast("mt:packet_codec", new PacketCodec());
                        pipeline.addLast("mt:packet_handler", new ProxyClientConnection(peerId, privateKey, registry));
                    }
                })
                .bind(bindAddress, requestedPort)
                .syncUninterruptibly()
                .channel();
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    @Override
    public void close() {
        if (serverChannel != null) serverChannel.close().syncUninterruptibly();
        if (workers != null) workers.shutdownGracefully().syncUninterruptibly();
        if (boss != null) boss.shutdownGracefully().syncUninterruptibly();
    }
}
