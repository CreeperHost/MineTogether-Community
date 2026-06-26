package net.creeperhost.minetogethercommunity.connect;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import net.creeperhost.minetogether.connect.lib.netty.packet.CFriendServers;
import net.creeperhost.minetogether.connect.lib.web.GetConnectServersRequest;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogether.lib.chat.profile.ProfileManager;
import net.creeperhost.minetogether.lib.web.ApiClientResponse;
import net.creeperhost.minetogether.lib.web.requests.GetClosestDCRequest;
import net.creeperhost.minetogether.session.JWebToken;
import net.creeperhost.minetogether.session.MineTogetherSession;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.connect.netty.NettyClient;
import net.creeperhost.minetogethercommunity.util.ModPackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.world.GameType;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConnectHandler {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Connect");
    private static final Map<RemoteServer, Profile> AVAILABLE_SERVER_MAP = new HashMap<>();
    private static final ExecutorService SEARCH_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("MT Connect Friend Search").build());
    private static final ExecutorService SHARE_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("MT Connect Share").build());
    private static final Gson GSON = new Gson();
    private static final Field PLAYER_LIST_MAX_PLAYERS = findField(PlayerList.class, "maxPlayers", "field_72405_c");
    private static final Field INTEGRATED_SERVER_IS_PUBLIC = findField(IntegratedServer.class, "isPublic", "field_71346_p");
    private static final Field INTEGRATED_SERVER_LAN_SERVER_PING = findField(IntegratedServer.class, "lanServerPing", "field_71345_q");

    private static final String FORCED_NODE = System.getProperty("connect.node");
    private static final String NODE_HOSTS_OVERRIDE = System.getProperty("connect.mesh.hosts");

    private static long lastSearch;
    private static CompletableFuture<?> activeSearch;
    private static List<CFriendServers.ServerEntry> searchResult;
    private static ConnectHost endpoint;
    private static NettyClient.ProxyConnection publishedServer;
    private static boolean publishing;
    private static boolean didWeShareFirst;
    private static int publishGeneration;
    private static int defaultMaxPlayers = 8;
    private static String statusMessage = "minetogether.connect.status.closed";

    public static void init() {
    }

    public static synchronized ConnectHost getEndpoint() throws IOException {
        if (endpoint == null) {
            GetConnectServersRequest.ConnectServer node = chooseServer();
            LOGGER.info("Selected MTConnect server: {}", node.name);
            endpoint = new ConnectHost(node);
        }
        return endpoint;
    }

    public static ConnectHost getSpecificEndpoint(String node) throws IOException {
        if (node == null || node.trim().isEmpty()) {
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

    private static GetConnectServersRequest.ConnectServer chooseServer() throws IOException {
        if (Boolean.getBoolean("mt.develop.connect")) {
            return GetConnectServersRequest.ConnectServer.getLocalHost();
        }

        List<GetConnectServersRequest.ConnectServer> servers = pollServers();
        if (servers == null || servers.isEmpty()) {
            throw new IOException("No MineTogether Connect servers returned.");
        }

        if (FORCED_NODE != null && !FORCED_NODE.trim().isEmpty()) {
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
        if (NODE_HOSTS_OVERRIDE != null && !NODE_HOSTS_OVERRIDE.trim().isEmpty()) {
            File override = new File(NODE_HOSTS_OVERRIDE);
            try (FileReader reader = new FileReader(override)) {
                return GSON.fromJson(reader, GetConnectServersRequest.LIST_SERVERS);
            }
        }

        ApiClientResponse<List<GetConnectServersRequest.ConnectServer>> response = MineTogether.API.execute(new GetConnectServersRequest());
        if (response.statusCode() != 200) {
            LOGGER.error("Failed to query Connect nodes. Status: {}", response.statusCode());
            return null;
        }
        return response.apiResponse();
    }

    public static boolean isEnabled() {
        return true;
    }

    public static void publishToFriends(final GameType gameType, final boolean cheats, final int maxPlayers) {
        if (publishing || publishedServer != null) return;
        final Minecraft mc = Minecraft.getMinecraft();
        final IntegratedServer server = mc.getIntegratedServer();
        if (server == null) return;

        publishing = true;
        didWeShareFirst = false;
        statusMessage = "minetogether.connect.status.opening";
        final int generation;
        synchronized (ConnectHandler.class) {
            generation = ++publishGeneration;
        }
        try {
            defaultMaxPlayers = server.getPlayerList().getMaxPlayers();
            setServerMaxPlayers(server, Math.max(2, maxPlayers));
            if (!server.getPublic()) {
                String port = server.shareToLAN(gameType, cheats);
                if (port == null) {
                    throw new IOException("Minecraft failed to open the integrated server to LAN.");
                }
                didWeShareFirst = true;
            } else {
                server.setGameType(gameType);
                server.getPlayerList().setCommandsAllowedForAll(cheats);
                if (mc.player != null) {
                    mc.player.setPermissionLevel(cheats ? 4 : 0);
                }
            }
        } catch (Throwable ex) {
            publishing = false;
            statusMessage = "minetogether.connect.status.failed";
            LOGGER.error("Failed to prepare integrated server for Connect sharing", ex);
            sendChat("minetogether.connect.open.failed", ex.getMessage());
            return;
        }

        CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    JWebToken token = requireSessionToken();
                    NettyClient.ProxyConnection connection = NettyClient.publishServer(server, getEndpoint(), token, getModpackKey(), Math.max(2, maxPlayers));
                    synchronized (ConnectHandler.class) {
                        if (generation != publishGeneration || !publishing) {
                            connection.disconnect();
                            return;
                        }
                        publishedServer = connection;
                        statusMessage = "minetogether.connect.status.open";
                    }
                } catch (Throwable ex) {
                    synchronized (ConnectHandler.class) {
                        if (generation != publishGeneration) {
                            return;
                        }
                    }
                    LOGGER.error("Failed to open world to MineTogether friends", ex);
                    sendChat("minetogether.connect.open.failed", ex.getMessage());
                    statusMessage = "minetogether.connect.status.failed";
                    unPublish();
                } finally {
                    synchronized (ConnectHandler.class) {
                        if (generation == publishGeneration) {
                            publishing = false;
                        }
                    }
                }
            }
        }, SHARE_EXECUTOR);
    }

    public static void unPublish() {
        synchronized (ConnectHandler.class) {
            publishGeneration++;
        }
        NettyClient.ProxyConnection published = publishedServer;
        publishedServer = null;
        publishing = false;
        if (published != null) {
            published.disconnect();
        }
        IntegratedServer server = Minecraft.getMinecraft().getIntegratedServer();
        if (server != null) {
            if (didWeShareFirst) {
                unshareIntegratedServer(server);
                didWeShareFirst = false;
            }
            setServerMaxPlayers(server, Math.max(defaultMaxPlayers, 8));
        }
        statusMessage = "minetogether.connect.status.closed";
    }

    public static void closeSharing() {
        unPublish();
        IntegratedServer server = Minecraft.getMinecraft().getIntegratedServer();
        if (server != null && server.getPublic()) {
            unshareIntegratedServer(server);
            setServerMaxPlayers(server, Math.max(defaultMaxPlayers, 8));
        }
        didWeShareFirst = false;
        statusMessage = "minetogether.connect.status.closed";
    }

    public static boolean isPublished() {
        return publishedServer != null;
    }

    public static boolean isPublishing() {
        return publishing;
    }

    public static String getStatusMessage() {
        return statusMessage;
    }

    public static JWebToken requireSessionToken() throws Exception {
        JWebToken token = MineTogetherSession.getDefault().getTokenAsync().get();
        if (token == null) {
            throw new IOException("MineTogether session token is unavailable");
        }
        return token;
    }

    public static void updateFriendsSearch() {
        if (activeSearch != null) {
            if (!activeSearch.isDone()) return;
            activeSearch = null;
            if (searchResult != null) {
                ProfileManager profileManager = MineTogetherChat.CHAT_STATE == null ? null : MineTogetherChat.CHAT_STATE.profileManager;
                Set<RemoteServer> keep = new HashSet<>();
                for (CFriendServers.ServerEntry entry : searchResult) {
                    RemoteServer server = new RemoteServer(entry.friend, entry.serverToken, entry.node);
                    keep.add(server);
                    Profile profile = AVAILABLE_SERVER_MAP.get(server);
                    if (profileManager != null && (profile == null || profile.isStale())) {
                        profile = profileManager.lookupProfile(entry.friend);
                    }
                    if (!AVAILABLE_SERVER_MAP.containsKey(server) || profile != AVAILABLE_SERVER_MAP.get(server)) {
                        AVAILABLE_SERVER_MAP.put(server, profile);
                    }
                }
                AVAILABLE_SERVER_MAP.keySet().retainAll(keep);
                searchResult = null;
            }
            return;
        }

        if (System.currentTimeMillis() - lastSearch < 5000) return;
        lastSearch = System.currentTimeMillis();
        activeSearch = CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                searchResult = null;
                try {
                    JWebToken token = requireSessionToken();
                    searchResult = NettyClient.getFriendServers(getEndpoint(), token, getModpackKey()).servers;
                } catch (Throwable ex) {
                    LOGGER.error("Failed to search for friend servers", ex);
                }
            }
        }, SEARCH_EXECUTOR);
    }

    public static Collection<RemoteServer> getRemoteServers() {
        return AVAILABLE_SERVER_MAP.keySet();
    }

    public static Profile getServerProfile(RemoteServer server) {
        return AVAILABLE_SERVER_MAP.get(server);
    }

    public static void clearAndReset() {
        if (activeSearch != null) {
            activeSearch.cancel(true);
            activeSearch = null;
            searchResult = null;
        }
        AVAILABLE_SERVER_MAP.clear();
        lastSearch = 0;
        endpoint = null;
    }

    public static void setServerMaxPlayers(IntegratedServer server, int maxPlayers) {
        try {
            PLAYER_LIST_MAX_PLAYERS.setInt(server.getPlayerList(), maxPlayers);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Unable to update integrated server max players", ex);
        }
    }

    private static void unshareIntegratedServer(IntegratedServer server) {
        try {
            Object ping = INTEGRATED_SERVER_LAN_SERVER_PING.get(server);
            if (ping instanceof Thread) {
                ((Thread) ping).interrupt();
            }
            INTEGRATED_SERVER_LAN_SERVER_PING.set(server, null);
            INTEGRATED_SERVER_IS_PUBLIC.setBoolean(server, false);
        } catch (IllegalAccessException ex) {
            LOGGER.warn("Unable to clear integrated server LAN sharing state", ex);
        }
    }

    private static String getModpackKey() {
        ModPackInfo.VersionInfo info = ModPackInfo.getInfo();
        String modpackKey = StringUtils.stripToEmpty(info.base64FTBID);
        if (modpackKey.isEmpty()) {
            modpackKey = StringUtils.stripToEmpty(info.curseID);
        }
        return modpackKey.isEmpty() ? null : modpackKey;
    }

    private static Field findField(Class<?> owner, String deobfName, String srgName) {
        for (String name : new String[] { deobfName, srgName }) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new IllegalStateException("Could not find field " + deobfName + " on " + owner.getName());
    }

    private static void sendChat(final String key, final Object... args) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                if (Minecraft.getMinecraft().ingameGUI != null) {
                    Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(new net.minecraft.util.text.TextComponentTranslation(key, args));
                }
            }
        });
    }
}
