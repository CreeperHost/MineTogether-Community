package net.creeperhost.minetogethercommunity.ci.connectservice;

import io.netty.buffer.ByteBuf;
import net.creeperhost.minetogether.connect.lib.netty.packet.CAccepted;
import net.creeperhost.minetogether.connect.lib.netty.packet.CBeginRaw;
import net.creeperhost.minetogether.connect.lib.netty.packet.CDisconnect;
import net.creeperhost.minetogether.connect.lib.netty.packet.CFriendServers;
import net.creeperhost.minetogether.connect.lib.netty.packet.CMaxPlayers;
import net.creeperhost.minetogether.connect.lib.netty.packet.CMessage;
import net.creeperhost.minetogether.connect.lib.netty.packet.CRaw;
import net.creeperhost.minetogether.connect.lib.netty.packet.CServerLink;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class ConnectRegistry {

    static final String NOT_FRIEND_MESSAGE = "You cannot join as you are not MineTogether friends with this user!";
    static final String FULL_MESSAGE = "MineTogether friends' server is full. Please contact your friend!";
    static final String HOST_CLOSED_MESSAGE = "Your friend closed their shared MineTogether world.";

    private final AccessPolicy policy = new AccessPolicy();
    private final Map<String, HostRegistration> hostsByToken = new HashMap<>();
    private final Map<UUID, HostRegistration> hostsByOwner = new HashMap<>();
    private final Map<String, PendingLink> pendingByToken = new HashMap<>();
    private final Map<ServicePeer, HostRegistration> hostByControl = new HashMap<>();
    private final Map<ServicePeer, PendingLink> pendingByUser = new HashMap<>();
    private final Map<ServicePeer, Relay> relayByPeer = new HashMap<>();
    private final List<ServiceEvent> events = new ArrayList<>();

    private long serverSequence;
    private long linkSequence;
    private long eventSequence;

    synchronized AccessPolicy policy() {
        return policy;
    }

    synchronized void setFriend(UUID left, UUID right, boolean enabled) {
        policy.setFriend(left, right, enabled);
        event("FRIENDSHIP_SET", Map.of(
                "left", left.toString(),
                "right", right.toString(),
                "enabled", enabled
        ));
    }

    synchronized void setLimit(UUID user, int maxPlayers) {
        policy.setLimit(user, maxPlayers);
        event("LIMIT_SET", Map.of("user", user.toString(), "maxPlayers", maxPlayers));
    }

    synchronized void requestMaxPlayers(ServicePeer peer, Identity identity) {
        int limit = policy.limit(identity.uuid());
        event("MAX_PLAYERS", Map.of("user", identity.uuid().toString(), "maxPlayers", limit));
        peer.writeAndClose(new CMaxPlayers(limit));
    }

    synchronized void registerHost(ServicePeer peer, Identity identity, String modpackKey) {
        if (hostsByOwner.containsKey(identity.uuid())) {
            reject(peer, "This account is already sharing a MineTogether world.", "HOST_DUPLICATE", identity);
            return;
        }

        String serverToken = String.format("server-%04d", ++serverSequence);
        HostRegistration host = new HostRegistration(identity, modpackKey, serverToken, peer, policy.limit(identity.uuid()));
        hostsByToken.put(serverToken, host);
        hostsByOwner.put(identity.uuid(), host);
        hostByControl.put(peer, host);

        peer.write(new CMaxPlayers(host.maxPlayers));
        peer.write(new CAccepted());
        event("HOST_REGISTERED", hostDetails(host));
    }

    synchronized void discover(ServicePeer peer, Identity identity, String modpackKey) {
        List<CFriendServers.ServerEntry> servers = hostsByToken.values().stream()
                .filter(host -> policy.areFriends(identity.uuid(), host.owner.uuid()))
                .filter(host -> Objects.equals(modpackKey, host.modpackKey))
                .sorted(Comparator.comparing(host -> host.serverToken))
                .map(host -> new CFriendServers.ServerEntry(host.owner.uuidHash(), host.serverToken, null))
                .toList();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("user", identity.uuid().toString());
        details.put("modpackKey", modpackKey);
        details.put("servers", servers.size());
        event("DISCOVERY", details);
        peer.writeAndClose(new CFriendServers(servers));
    }

    synchronized String requestUser(ServicePeer peer, Identity identity, String serverToken, boolean query) {
        HostRegistration host = hostsByToken.get(serverToken);
        if (host == null || !host.control.isActive()) {
            reject(peer, "This MineTogether friend server is no longer available.", "REJECTED_UNAVAILABLE", identity);
            return null;
        }
        if (!policy.areFriends(identity.uuid(), host.owner.uuid())) {
            reject(peer, NOT_FRIEND_MESSAGE, "REJECTED_NOT_FRIEND", identity);
            return null;
        }
        if (!query && host.isFull()) {
            reject(peer, FULL_MESSAGE, "REJECTED_FULL", identity);
            host.control.write(new CMessage("Your friend " + identity.username()
                    + " tried to join, but the MineTogether player limit has been reached."));
            return null;
        }

        String linkToken = String.format("link-%04d", ++linkSequence);
        PendingLink pending = new PendingLink(linkToken, host, peer, identity, query);
        pendingByToken.put(linkToken, pending);
        pendingByUser.put(peer, pending);
        host.pending.add(pending);
        host.control.write(new CServerLink(linkToken));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("linkToken", linkToken);
        details.put("serverToken", serverToken);
        details.put("user", identity.uuid().toString());
        details.put("query", query);
        event("LINK_REQUESTED", details);
        return linkToken;
    }

    synchronized void expirePending(String linkToken) {
        PendingLink pending = pendingByToken.get(linkToken);
        if (pending != null) cancelPending(pending, "MineTogether host did not open the relay in time.", true);
    }

    synchronized void linkHost(ServicePeer hostPeer, Identity identity, String linkToken) {
        PendingLink pending = pendingByToken.get(linkToken);
        if (pending == null) {
            reject(hostPeer, "Unknown or expired MineTogether link token.", "HOST_LINK_UNKNOWN", identity);
            return;
        }
        if (!pending.host.owner.uuid().equals(identity.uuid())) {
            reject(hostPeer, "MineTogether link token does not belong to this host.", "HOST_LINK_OWNER_MISMATCH", identity);
            return;
        }
        if (!pending.user.isActive() || !pending.host.control.isActive()) {
            cancelPending(pending, "MineTogether link peer disconnected.", true);
            reject(hostPeer, "MineTogether link peer disconnected.", "HOST_LINK_PEER_GONE", identity);
            return;
        }

        pendingByToken.remove(linkToken);
        pendingByUser.remove(pending.user);
        pending.host.pending.remove(pending);

        Relay relay = new Relay(pending.host, pending.user, hostPeer, pending.userIdentity, pending.query);
        pending.host.relays.add(relay);
        relayByPeer.put(pending.user, relay);
        relayByPeer.put(hostPeer, relay);

        hostPeer.write(new CAccepted());
        // Host link pipelines are raw-ready before SHostConnect and do not handle CBeginRaw.
        pending.user.write(new CBeginRaw());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("linkToken", linkToken);
        details.put("serverToken", pending.host.serverToken);
        details.put("user", pending.userIdentity.uuid().toString());
        details.put("query", pending.query);
        event("LINKED", details);
    }

    synchronized void relay(ServicePeer source, ByteBuf data) {
        Relay relay = relayByPeer.get(source);
        if (relay == null || relay.closed) {
            data.release();
            return;
        }
        ServicePeer target = relay.other(source);
        if (target == null || !target.isActive()) {
            data.release();
            closeRelay(relay, "MineTogether relay peer disconnected.", source);
            return;
        }
        // CRaw releases this retained buffer after PacketCodec writes it.
        target.write(new CRaw(data));
    }

    synchronized void disconnected(ServicePeer peer) {
        HostRegistration host = hostByControl.remove(peer);
        if (host != null) {
            removeHost(host, "control channel closed");
            return;
        }
        PendingLink pending = pendingByUser.remove(peer);
        if (pending != null) {
            cancelPending(pending, "MineTogether joining client disconnected.", false);
            return;
        }
        Relay relay = relayByPeer.get(peer);
        if (relay != null) closeRelay(relay, "MineTogether relay peer disconnected.", peer);
    }

    synchronized boolean dropHost(UUID owner) {
        HostRegistration host = hostsByOwner.get(owner);
        if (host == null) return false;
        event("HOST_FAULT_INJECTED", Map.of("user", owner.toString()));
        host.control.close();
        return true;
    }

    synchronized void reset() {
        for (HostRegistration host : List.copyOf(hostsByToken.values())) {
            host.control.close();
            removeHost(host, "fixture reset");
        }
        policy.reset();
        pendingByToken.clear();
        pendingByUser.clear();
        relayByPeer.clear();
        event("RESET", Map.of());
    }

    synchronized String stateJson() {
        List<Map<String, Object>> hosts = hostsByToken.values().stream()
                .sorted(Comparator.comparing(host -> host.serverToken))
                .map(this::hostDetails)
                .toList();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("hosts", hosts);
        state.put("pendingLinks", pendingByToken.size());
        state.put("relays", relayByPeer.size() / 2);
        state.put("friendships", policy.friendshipCount());
        state.put("events", events.size());
        return Json.value(state);
    }

    synchronized String eventsJson() {
        return Json.value(events.stream().map(ServiceEvent::toMap).toList());
    }

    private void reject(ServicePeer peer, String message, String event, Identity identity) {
        this.event(event, Map.of("user", identity.uuid().toString(), "message", message));
        peer.writeAndClose(new CDisconnect(message));
    }

    private void removeHost(HostRegistration host, String reason) {
        if (hostsByToken.remove(host.serverToken) == null) return;
        hostsByOwner.remove(host.owner.uuid(), host);
        hostByControl.remove(host.control);
        for (PendingLink pending : List.copyOf(host.pending)) {
            cancelPending(pending, HOST_CLOSED_MESSAGE, true);
        }
        for (Relay relay : List.copyOf(host.relays)) {
            closeRelay(relay, HOST_CLOSED_MESSAGE, host.control);
        }
        Map<String, Object> details = hostDetails(host);
        details.put("reason", reason);
        event("HOST_REMOVED", details);
    }

    private void cancelPending(PendingLink pending, String reason, boolean notifyUser) {
        pendingByToken.remove(pending.linkToken);
        pendingByUser.remove(pending.user);
        pending.host.pending.remove(pending);
        if (notifyUser && pending.user.isActive()) pending.user.writeAndClose(new CDisconnect(reason));
        event("LINK_CANCELLED", Map.of("linkToken", pending.linkToken, "reason", reason));
    }

    private void closeRelay(Relay relay, String reason, ServicePeer source) {
        if (relay.closed) return;
        relay.closed = true;
        relay.host.relays.remove(relay);
        relayByPeer.remove(relay.user);
        relayByPeer.remove(relay.hostPeer);
        ServicePeer other = relay.other(source);
        if (other != null) {
            if (other.isActive()) other.writeAndClose(new CDisconnect(reason));
        } else {
            if (relay.user.isActive()) relay.user.writeAndClose(new CDisconnect(reason));
            if (relay.hostPeer.isActive()) relay.hostPeer.writeAndClose(new CDisconnect(reason));
        }
        event("RELAY_CLOSED", Map.of(
                "serverToken", relay.host.serverToken,
                "user", relay.userIdentity.uuid().toString(),
                "query", relay.query,
                "reason", reason
        ));
    }

    private Map<String, Object> hostDetails(HostRegistration host) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("user", host.owner.uuid().toString());
        details.put("username", host.owner.username());
        details.put("hash", host.owner.uuidHash());
        details.put("serverToken", host.serverToken);
        details.put("modpackKey", host.modpackKey);
        details.put("maxPlayers", host.maxPlayers);
        details.put("usedPlayers", host.usedPlayers());
        details.put("pendingLinks", host.pending.size());
        details.put("relays", host.relays.size());
        return details;
    }

    private void event(String type, Map<String, ?> details) {
        events.add(new ServiceEvent(++eventSequence, type, new LinkedHashMap<>(details)));
    }

    private static final class HostRegistration {
        private final Identity owner;
        private final String modpackKey;
        private final String serverToken;
        private final ServicePeer control;
        private final int maxPlayers;
        private final Set<PendingLink> pending = new LinkedHashSet<>();
        private final Set<Relay> relays = new LinkedHashSet<>();

        private HostRegistration(Identity owner, String modpackKey, String serverToken, ServicePeer control, int maxPlayers) {
            this.owner = owner;
            this.modpackKey = modpackKey;
            this.serverToken = serverToken;
            this.control = control;
            this.maxPlayers = maxPlayers;
        }

        private int usedPlayers() {
            int users = (int) pending.stream().filter(link -> !link.query).count();
            users += (int) relays.stream().filter(relay -> !relay.query && !relay.closed).count();
            return 1 + users;
        }

        private boolean isFull() {
            return maxPlayers > 0 && usedPlayers() >= maxPlayers;
        }
    }

    private record PendingLink(String linkToken, HostRegistration host, ServicePeer user,
                               Identity userIdentity, boolean query) {
    }

    private static final class Relay {
        private final HostRegistration host;
        private final ServicePeer user;
        private final ServicePeer hostPeer;
        private final Identity userIdentity;
        private final boolean query;
        private boolean closed;

        private Relay(HostRegistration host, ServicePeer user, ServicePeer hostPeer,
                      Identity userIdentity, boolean query) {
            this.host = host;
            this.user = user;
            this.hostPeer = hostPeer;
            this.userIdentity = userIdentity;
            this.query = query;
        }

        private ServicePeer other(ServicePeer peer) {
            if (peer == user) return hostPeer;
            if (peer == hostPeer) return user;
            return null;
        }
    }

    private record ServiceEvent(long sequence, String type, Map<String, ?> details) {
        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sequence", sequence);
            map.put("type", type);
            map.putAll(details);
            return map;
        }
    }
}
