package net.creeperhost.minetogethercommunity.connect;

import net.creeperhost.minetogether.connect.lib.util.RSAUtils;
import net.creeperhost.minetogether.connect.lib.web.GetConnectServersRequest;

import java.security.PublicKey;

public class ConnectHost {

    private final String address;
    private final int proxyPort;
    private final PublicKey publicKey;

    public ConnectHost(String address, int proxyPort, PublicKey publicKey) {
        this.address = address;
        this.proxyPort = proxyPort;
        this.publicKey = publicKey;
    }

    public ConnectHost(GetConnectServersRequest.ConnectServer node) {
        this(node.address, node.port, RSAUtils.loadRSAPublicKey(RSAUtils.loadPem(node.publicKey)));
    }

    public String getAddress() {
        return address;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }
}
