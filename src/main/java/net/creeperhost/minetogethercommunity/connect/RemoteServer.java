package net.creeperhost.minetogethercommunity.connect;

import net.creeperhost.minetogether.connect.lib.netty.packet.CFriendServers;
import net.minecraft.realms.RealmsSharedConstants;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RemoteServer {

    private final String friendHash;
    private final String serverToken;
    private final String node;
    private final String modpackKey;
    private final PackCompatibility compatibility;
    private String motd = "";
    private String status = "";
    private String version = RealmsSharedConstants.VERSION_STRING;
    private int protocol = RealmsSharedConstants.NETWORK_PROTOCOL_VERSION;
    private long ping = -2L;
    private boolean pinged;
    private List<String> playerList = Collections.emptyList();

    public RemoteServer(String friendHash, String serverToken, String node) {
        this(friendHash, serverToken, node, null, PackCompatibility.UNKNOWN);
    }

    public RemoteServer(String friendHash, String serverToken, String node, String modpackKey, PackCompatibility compatibility) {
        this.friendHash = friendHash;
        this.serverToken = serverToken;
        this.node = node;
        this.modpackKey = modpackKey;
        this.compatibility = compatibility == null ? PackCompatibility.UNKNOWN : compatibility;
    }

    public static RemoteServer fromEntry(CFriendServers.ServerEntry entry) {
        return new RemoteServer(entry.friend, entry.serverToken, entry.node, readStringField(entry, "modpackKey"), readCompatibility(entry));
    }

    public boolean shouldWarnBeforeJoin() {
        return compatibility != PackCompatibility.SAME;
    }

    public String getFriendHash() {
        return friendHash;
    }

    public String getServerToken() {
        return serverToken;
    }

    public String getNode() {
        return node;
    }

    public String getModpackKey() {
        return modpackKey;
    }

    public PackCompatibility getCompatibility() {
        return compatibility;
    }

    public String getMotd() {
        return motd;
    }

    public void setMotd(String motd) {
        this.motd = motd == null ? "" : motd;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status == null ? "" : status;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version == null ? "" : version;
    }

    public int getProtocol() {
        return protocol;
    }

    public void setProtocol(int protocol) {
        this.protocol = protocol;
    }

    public long getPing() {
        return ping;
    }

    public void setPing(long ping) {
        this.ping = ping;
    }

    public boolean isPinged() {
        return pinged;
    }

    public void setPinged(boolean pinged) {
        this.pinged = pinged;
    }

    public List<String> getPlayerList() {
        return playerList;
    }

    public void setPlayerList(List<String> playerList) {
        this.playerList = playerList == null ? Collections.<String>emptyList() : playerList;
    }

    public void resetPingState() {
        motd = "";
        status = "";
        version = RealmsSharedConstants.VERSION_STRING;
        protocol = RealmsSharedConstants.NETWORK_PROTOCOL_VERSION;
        ping = -2L;
        pinged = false;
        playerList = Collections.emptyList();
    }

    private static String readStringField(Object source, String fieldName) {
        try {
            Field field = source.getClass().getField(fieldName);
            Object value = field.get(source);
            return value instanceof String && !((String) value).isEmpty() ? (String) value : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static PackCompatibility readCompatibility(Object source) {
        try {
            Field field = source.getClass().getField("compatibility");
            Object value = field.get(source);
            if (value != null) {
                return PackCompatibility.valueOf(String.valueOf(value));
            }
        } catch (IllegalArgumentException | ReflectiveOperationException ignored) {
        }
        return PackCompatibility.UNKNOWN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RemoteServer)) return false;
        RemoteServer that = (RemoteServer) o;
        return Objects.equals(friendHash, that.friendHash)
                && Objects.equals(serverToken, that.serverToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(friendHash, serverToken);
    }

    public enum PackCompatibility {
        SAME,
        DIFFERENT,
        UNKNOWN
    }
}
