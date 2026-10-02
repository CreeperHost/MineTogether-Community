package net.creeperhost.minetogethercommunity.ci.connectservice;

import io.netty.buffer.Unpooled;
import net.creeperhost.minetogether.connect.lib.netty.packet.CAccepted;
import net.creeperhost.minetogether.connect.lib.netty.packet.CBeginRaw;
import net.creeperhost.minetogether.connect.lib.netty.packet.CDisconnect;
import net.creeperhost.minetogether.connect.lib.netty.packet.CFriendServers;
import net.creeperhost.minetogether.connect.lib.netty.packet.CMaxPlayers;
import net.creeperhost.minetogether.connect.lib.netty.packet.CMessage;
import net.creeperhost.minetogether.connect.lib.netty.packet.CRaw;
import net.creeperhost.minetogether.connect.lib.netty.packet.CServerLink;
import net.creeperhost.minetogether.connect.lib.netty.packet.Packet;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectRegistryTest {

    private static final Identity HOST = identity("10000000-0000-4000-8000-000000000001", "CI Host");
    private static final Identity FRIEND = identity("20000000-0000-4000-8000-000000000002", "CI Friend");
    private static final Identity EXTRA = identity("30000000-0000-4000-8000-000000000003", "CI Extra");
    private static final Identity OUTSIDER = identity("40000000-0000-4000-8000-000000000004", "CI Outsider");

    @Test
    void coversDiscoveryAdmissionRelayLimitAndHostCleanup() {
        ConnectRegistry registry = new ConnectRegistry();
        registry.setFriend(HOST.uuid(), FRIEND.uuid(), true);
        registry.setFriend(HOST.uuid(), EXTRA.uuid(), true);
        registry.setLimit(HOST.uuid(), 2);

        FakePeer hostControl = new FakePeer("host-control");
        registry.registerHost(hostControl, HOST, "modpack:ci");
        assertInstanceOf(CMaxPlayers.class, hostControl.packets.get(0));
        assertInstanceOf(CAccepted.class, hostControl.packets.get(1));

        FakePeer discovery = new FakePeer("discovery");
        registry.discover(discovery, FRIEND, "modpack:ci");
        CFriendServers servers = assertInstanceOf(CFriendServers.class, discovery.onlyPacket());
        assertEquals(1, servers.servers.size());
        assertEquals(HOST.uuidHash(), servers.servers.get(0).friend);
        String serverToken = servers.servers.get(0).serverToken;

        FakePeer wrongPack = new FakePeer("wrong-pack");
        registry.discover(wrongPack, FRIEND, "modpack:other");
        assertTrue(assertInstanceOf(CFriendServers.class, wrongPack.onlyPacket()).servers.isEmpty());

        FakePeer outsider = new FakePeer("outsider");
        registry.requestUser(outsider, OUTSIDER, serverToken, false);
        assertEquals(ConnectRegistry.NOT_FRIEND_MESSAGE,
                assertInstanceOf(CDisconnect.class, outsider.onlyPacket()).message);

        // A server-list status query gets a real relay but consumes no player slot.
        FakePeer queryUser = new FakePeer("query-user");
        String queryToken = registry.requestUser(queryUser, FRIEND, serverToken, true);
        FakePeer queryHost = new FakePeer("query-host");
        registry.linkHost(queryHost, HOST, queryToken);
        assertInstanceOf(CAccepted.class, queryHost.onlyPacket());
        assertInstanceOf(CBeginRaw.class, queryUser.onlyPacket());

        FakePeer friendUser = new FakePeer("friend-user");
        String friendToken = registry.requestUser(friendUser, FRIEND, serverToken, false);
        FakePeer friendHost = new FakePeer("friend-host");
        registry.linkHost(friendHost, HOST, friendToken);
        assertInstanceOf(CBeginRaw.class, friendUser.onlyPacket());

        byte[] raw = "minecraft-frame".getBytes(StandardCharsets.UTF_8);
        registry.relay(friendUser, Unpooled.wrappedBuffer(raw));
        CRaw relayed = assertInstanceOf(CRaw.class, friendHost.packets.get(1));
        byte[] received = new byte[relayed.data.readableBytes()];
        relayed.data.readBytes(received);
        relayed.data.release();
        assertEquals("minecraft-frame", new String(received, StandardCharsets.UTF_8));

        FakePeer extra = new FakePeer("extra");
        registry.requestUser(extra, EXTRA, serverToken, false);
        assertEquals(ConnectRegistry.FULL_MESSAGE,
                assertInstanceOf(CDisconnect.class, extra.onlyPacket()).message);
        assertTrue(hostControl.packets.stream().anyMatch(CMessage.class::isInstance));

        registry.disconnected(hostControl);
        assertFalse(friendUser.active);
        assertFalse(queryUser.active);
        assertTrue(registry.stateJson().contains("\"hosts\":[]"));
        assertTrue(registry.eventsJson().contains("\"type\":\"HOST_REMOVED\""));
        assertTrue(registry.eventsJson().contains("\"type\":\"RELAY_CLOSED\""));
    }

    @Test
    void expiredPendingLinkReleasesItsReservation() {
        ConnectRegistry registry = new ConnectRegistry();
        registry.setFriend(HOST.uuid(), FRIEND.uuid(), true);
        registry.setLimit(HOST.uuid(), 2);
        FakePeer host = new FakePeer("host");
        registry.registerHost(host, HOST, "pack");

        FakePeer joining = new FakePeer("joining");
        String token = registry.requestUser(joining, FRIEND, "server-0001", false);
        registry.expirePending(token);

        assertInstanceOf(CDisconnect.class, joining.onlyPacket());
        assertTrue(registry.stateJson().contains("\"pendingLinks\":0"));
        assertTrue(registry.stateJson().contains("\"usedPlayers\":1"));
    }

    private static Identity identity(String uuid, String username) {
        UUID parsed = UUID.fromString(uuid);
        return new Identity(parsed, username, Identity.hash(parsed));
    }

    private static final class FakePeer implements ServicePeer {
        private final String id;
        private final List<Packet<?>> packets = new ArrayList<>();
        private boolean active = true;

        private FakePeer(String id) {
            this.id = id;
        }

        private Packet<?> onlyPacket() {
            assertEquals(1, packets.size(), "Expected exactly one packet from " + id);
            return packets.get(0);
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void write(Packet<?> packet) {
            packets.add(packet);
        }

        @Override
        public void writeAndClose(Packet<?> packet) {
            packets.add(packet);
            active = false;
        }

        @Override
        public void close() {
            active = false;
        }
    }
}
