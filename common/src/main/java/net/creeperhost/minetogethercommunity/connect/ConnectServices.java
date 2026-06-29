package net.creeperhost.minetogethercommunity.connect;

import com.google.gson.Gson;
import net.covers1624.quack.collection.FastStream;
import net.covers1624.quack.gson.JsonUtils;
import net.creeperhost.minetogether.lib.web.ApiClientResponse;
import net.creeperhost.minetogether.lib.web.requests.GetClosestDCRequest;
import net.creeperhost.minetogether.connect.lib.web.GetConnectServersRequest;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.util.ModPackInfo;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ConnectServices {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new Gson();

    // Useful for testing, can connect to specific node.
    private static final String FORCED_NODE = System.getProperty("connect.node");
    // Useful for testing, can force the use of a host list json. Running node mesh locally, for example.
    @Nullable
    private static final String NODE_HOSTS_OVERRIDE = System.getProperty("connect.mesh.hosts");

    @Nullable
    private static ConnectHost endpoint;

    public static ConnectHost getEndpoint() {
        if (endpoint == null) {
            GetConnectServersRequest.ConnectServer node = chooseServer();
            LOGGER.info("Selected MTConnect server: " + node.name);

            endpoint = new ConnectHost(node);
        }
        return endpoint;
    }

    public static ConnectHost getSpecificEndpoint(@Nullable String node) throws IOException {
        if (node == null) {
            return getEndpoint();
        }

        List<GetConnectServersRequest.ConnectServer> servers = pollServers();
        if (servers == null || servers.isEmpty()) {
            throw new IllegalStateException("No server list returned.");
        }
        GetConnectServersRequest.ConnectServer server = FastStream.of(servers)
                .filter(e -> e.name.equals(node))
                .firstOrDefault();
        if (server == null) {
            throw new IllegalStateException("Did not find node with id: " + node);
        }
        return new ConnectHost(server);
    }

    public static @Nullable String getModpackKey() {
        return ModPackInfo.getInfo().getConnectPackKey();
    }

    private static GetConnectServersRequest.ConnectServer chooseServer() {
        if (Boolean.getBoolean("mt.develop.connect")) {
            return GetConnectServersRequest.ConnectServer.getLocalHost();
        }
        try {
            List<GetConnectServersRequest.ConnectServer> servers = pollServers();
            if (servers == null || servers.isEmpty()) {
                // TODO, this needs to gracefully fail as noted bellow.
                LOGGER.warn("No MTConnect nodes found.. :(");
                throw new NotImplementedException();
            }

            if (FORCED_NODE != null) {
                return FastStream.of(servers)
                        .filter(e -> e.name.equals(FORCED_NODE))
                        .first();
            }

            GetConnectServersRequest.ConnectServer first = servers.get(0);

            ApiClientResponse<GetClosestDCRequest.Response> closestDCResponse = MineTogether.API.execute(new GetClosestDCRequest());
            if (!closestDCResponse.hasBody()) {
                LOGGER.error("Failed to get Closest DC locations. Using first server: {}", first.name);
                return first;
            }

            for (GetClosestDCRequest.DataCenter dc : closestDCResponse.apiResponse().getDataCenters()) {
                for (GetConnectServersRequest.ConnectServer server : servers) {
                    if (server.location.equals(dc.getName())) {
                        LOGGER.info("Selected server {}. Closest DC was {}.", server.name, dc.getName());
                        return server;
                    }
                }
            }

            LOGGER.info("Could not select a server. Using first server: {}", first.name);
            return first;
        } catch (IOException ex) {
            // TODO, this needs to gracefully fail, getEndpoint likely needs to return null, and isEnabled needs to return false.
            throw new NotImplementedException("TODO, Implement exception handling for this:", ex);
        }
    }

    @Nullable
    private static List<GetConnectServersRequest.ConnectServer> pollServers() throws IOException {
        if (NODE_HOSTS_OVERRIDE != null) {
            return JsonUtils.parse(GSON, Path.of(NODE_HOSTS_OVERRIDE), GetConnectServersRequest.LIST_SERVERS);
        }
        ApiClientResponse<List<GetConnectServersRequest.ConnectServer>> apiResp = MineTogether.API.execute(new GetConnectServersRequest());
        if (apiResp.statusCode() != 200) {
            LOGGER.error("Failed to query node list. Got: {}", apiResp.statusCode());
            return null;
        }
        return apiResp.apiResponse();
    }
}
