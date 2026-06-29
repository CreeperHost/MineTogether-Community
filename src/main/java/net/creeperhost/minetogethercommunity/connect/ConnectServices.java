package net.creeperhost.minetogethercommunity.connect;

import com.google.gson.Gson;
import net.creeperhost.minetogether.connect.lib.web.GetConnectServersRequest;
import net.creeperhost.minetogether.lib.web.ApiClientResponse;
import net.creeperhost.minetogether.lib.web.requests.GetClosestDCRequest;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.util.ModPackInfo;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

public class ConnectServices {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Connect");
    private static final Gson GSON = new Gson();
    private static final String FORCED_NODE = System.getProperty("connect.node");
    private static final String NODE_HOSTS_OVERRIDE = System.getProperty("connect.mesh.hosts");

    private static ConnectHost endpoint;

    public static synchronized ConnectHost getEndpoint() throws IOException {
        if (endpoint == null) {
            GetConnectServersRequest.ConnectServer node = chooseServer();
            LOGGER.info("Selected MTConnect server: {}", node.name);
            endpoint = new ConnectHost(node);
        }
        return endpoint;
    }

    public static ConnectHost getSpecificEndpoint(String node) throws IOException {
        if (StringUtils.isBlank(node)) {
            return getEndpoint();
        }

        List<GetConnectServersRequest.ConnectServer> servers = pollServers();
        if (servers == null || servers.isEmpty()) {
            throw new IOException("No MineTogether Connect servers returned.");
        }
        for (GetConnectServersRequest.ConnectServer server : servers) {
            if (node.equals(server.name)) {
                return new ConnectHost(server);
            }
        }
        throw new IOException("No MineTogether Connect node found with id " + node);
    }

    public static String getModpackKey() {
        return ModPackInfo.getInfo().getConnectPackKey();
    }

    private static GetConnectServersRequest.ConnectServer chooseServer() throws IOException {
        if (Boolean.getBoolean("mt.develop.connect")) {
            return GetConnectServersRequest.ConnectServer.getLocalHost();
        }

        List<GetConnectServersRequest.ConnectServer> servers = pollServers();
        if (servers == null || servers.isEmpty()) {
            throw new IOException("No MineTogether Connect servers returned.");
        }

        if (!StringUtils.isBlank(FORCED_NODE)) {
            for (GetConnectServersRequest.ConnectServer server : servers) {
                if (FORCED_NODE.equals(server.name)) {
                    return server;
                }
            }
            throw new IOException("Forced MineTogether Connect node not found: " + FORCED_NODE);
        }

        GetConnectServersRequest.ConnectServer first = servers.get(0);
        try {
            ApiClientResponse<GetClosestDCRequest.Response> closestDCResponse = MineTogether.API.execute(new GetClosestDCRequest());
            if (!closestDCResponse.hasBody()) {
                LOGGER.warn("Failed to get closest DC list, using first Connect node {}", first.name);
                return first;
            }
            for (GetClosestDCRequest.DataCenter dc : closestDCResponse.apiResponse().getDataCenters()) {
                for (GetConnectServersRequest.ConnectServer server : servers) {
                    if (server.location != null && server.location.equals(dc.getName())) {
                        LOGGER.info("Selected Connect node {} from closest DC {}", server.name, dc.getName());
                        return server;
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("Failed to choose nearest Connect node, using first node {}", first.name, ex);
        }
        return first;
    }

    private static List<GetConnectServersRequest.ConnectServer> pollServers() throws IOException {
        if (!StringUtils.isBlank(NODE_HOSTS_OVERRIDE)) {
            File override = new File(NODE_HOSTS_OVERRIDE);
            FileReader reader = new FileReader(override);
            try {
                return GSON.fromJson(reader, GetConnectServersRequest.LIST_SERVERS);
            } finally {
                reader.close();
            }
        }

        ApiClientResponse<List<GetConnectServersRequest.ConnectServer>> response = MineTogether.API.execute(new GetConnectServersRequest());
        if (response.statusCode() != 200) {
            LOGGER.error("Failed to query Connect nodes. Status: {}", response.statusCode());
            return null;
        }
        return response.apiResponse();
    }
}
