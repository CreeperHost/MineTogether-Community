package net.creeperhost.minetogethercommunity.connect;

import net.minecraft.realms.RealmsSharedConstants;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RemoteServer {

    private final String friendHash;
    private final String serverToken;
    private final String node;
    private String motd = "";
    private String status = "";
    private String version = RealmsSharedConstants.VERSION_STRING;
    private int protocol = RealmsSharedConstants.NETWORK_PROTOCOL_VERSION;
    private long ping = -2L;
    private boolean pinged;
    private List<String> playerList = Collections.emptyList();

    public RemoteServer(String friendHash, String serverToken, String node) {
        this.friendHash = friendHash;
        this.serverToken = serverToken;
        this.node = node;
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
}
