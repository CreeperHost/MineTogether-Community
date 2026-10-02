package net.creeperhost.minetogethercommunity.ci.connectservice;

import net.creeperhost.minetogether.connect.lib.netty.packet.Packet;

interface ServicePeer {

    String id();

    boolean isActive();

    void write(Packet<?> packet);

    void writeAndClose(Packet<?> packet);

    void close();
}
