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
import net.creeperhost.minetogethercommunity.util.ClientTaskRunner;
import net.creeperhost.minetogethercommunity.util.DiagnosticLog;
import net.creeperhost.minetogethercommunity.util.ModPackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.world.WorldSettings.GameType;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private static final Field PLAYER_LIST_MAX_PLAYERS = findField(ServerConfigurationManager.class, "maxPlayers", "field_72405_c");
    private static final Field INTEGRATED_SERVER_IS_PUBLIC = findField(IntegratedServer.class, "isPublic", "field_71346_p");
    private static final Field INTEGRATED_SERVER_LAN_SERVER_PING = findField(IntegratedServer.class, "lanServerPing", "field_71345_q");

    private static final String FORCED_NODE = System.getProperty("connect.node");
    private static final String NODE_HOSTS_OVERRIDE = System.getProperty("connect.mesh.hosts");

    private static long lastSearch;
    private static CompletableFuture<?> activeSearch;
    private static volatile List<CFriendServers.ServerEntry> searchResult;
    private static ConnectHost endpoint;
    private static NettyClient.ProxyConnection publishedServer;
    private static boolean publishing;
    private static boolean didWeShareFirst;
    private static int publishGeneration;
    private static int defaultMaxPlayers = 8;
    private static String statusMessage = "minetogether.connect.status.closed";
    private static volatile String lastPublishIdentity = "<none>";
    private static volatile String lastSearchIdentity = "<none>";

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
        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] preparing to publish integrated server to friends gameType={} cheats={} maxPlayers={}",
                gameType, Boolean.valueOf(cheats), Integer.valueOf(maxPlayers));
        final int generation;
        synchronized (ConnectHandler.class) {
            generation = ++publishGeneration;
        }
        try {
            defaultMaxPlayers = server.getConfigurationManager().getMaxPlayers();
            setServerMaxPlayers(server, Math.max(2, maxPlayers));
            if (!server.getPublic()) {
                String port = server.shareToLAN(gameType, cheats);
                if (port == null) {
                    throw new IOException("Minecraft failed to open the integrated server to LAN.");
                }
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] integrated server opened to LAN for Connect port={}", port);
                didWeShareFirst = true;
            } else {
                server.setGameType(gameType);
                server.getConfigurationManager().setCommandsAllowedForAll(cheats);
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] reusing existing public integrated server for Connect sharing");
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
                    ModpackIdentity modpack = getModpackIdentity();
                    ConnectHost selectedEndpoint = getEndpoint();
                    lastPublishIdentity = describeIdentity(token, modpack);
                    DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] starting hosted friend-server registration endpoint={}:{} identity={}",
                            selectedEndpoint.getAddress(), Integer.valueOf(selectedEndpoint.getProxyPort()), lastPublishIdentity);
                    NettyClient.ProxyConnection connection = NettyClient.publishServer(server, selectedEndpoint, token, modpack.key, Math.max(2, maxPlayers));
                    synchronized (ConnectHandler.class) {
                        if (generation != publishGeneration || !publishing) {
                            connection.disconnect();
                            return;
                        }
                        publishedServer = connection;
                        statusMessage = "minetogether.connect.status.open";
                        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] MineTogether friends sharing marked open");
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
                List<CFriendServers.ServerEntry> result = searchResult;
                ProfileManager profileManager = MineTogetherChat.CHAT_STATE == null ? null : MineTogetherChat.CHAT_STATE.profileManager;
                Set<RemoteServer> keep = new HashSet<>();
                int skippedNullEntries = 0;
                int availableBefore = AVAILABLE_SERVER_MAP.size();
                for (CFriendServers.ServerEntry entry : result) {
                    if (entry == null) {
                        skippedNullEntries++;
                        continue;
                    }
                    RemoteServer server = RemoteServer.fromEntry(entry);
                    ConnectPackResolver.prefetch(server.getModpackKey());
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
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] applied friend-server search result: returned={} skippedNull={} unique={} availableBefore={} availableAfter={} removed={} profileManager={}",
                        Integer.valueOf(result.size()), Integer.valueOf(skippedNullEntries), Integer.valueOf(keep.size()),
                        Integer.valueOf(availableBefore), Integer.valueOf(AVAILABLE_SERVER_MAP.size()),
                        Integer.valueOf(Math.max(0, availableBefore - AVAILABLE_SERVER_MAP.size())),
                        profileManager == null ? "<missing>" : "<present>");
            } else {
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] friend-server search completed without a result: {}", diagnosticState());
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
                    ConnectHost selectedEndpoint = getEndpoint();
                    ModpackIdentity modpack = getModpackIdentity();
                    ProfileManager profileManager = MineTogetherChat.CHAT_STATE == null ? null : MineTogetherChat.CHAT_STATE.profileManager;
                    lastSearchIdentity = describeIdentity(token, modpack);
                    if (DiagnosticLog.enabled()) {
                        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] starting friend-server search endpoint={}:{} identity={} friendGraph={}",
                                selectedEndpoint.getAddress(), Integer.valueOf(selectedEndpoint.getProxyPort()), lastSearchIdentity,
                                describeFriendGraph(profileManager));
                    }
                    searchResult = searchFriendServers(selectedEndpoint, token, modpack.key, "selected");
                    if (searchResult.isEmpty() && modpack.key != null) {
                        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] keyed friend-server search returned zero; retrying without modpack key");
                        searchResult = searchFriendServers(selectedEndpoint, token, null, "selected-unkeyed");
                    }
                    if (searchResult.isEmpty()) {
                        searchResult = searchAllNodes(token, modpack.key);
                    }
                    if (searchResult.isEmpty() && modpack.key != null) {
                        searchResult = searchAllNodes(token, null);
                    }
                    DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] received friend-server search result: count={}", Integer.valueOf(searchResult.size()));
                } catch (Throwable ex) {
                    DiagnosticLog.error(LOGGER, "[MT-1710-DIAG] failed to search for friend servers", ex);
                }
            }
        }, SEARCH_EXECUTOR);
    }

    private static List<CFriendServers.ServerEntry> searchFriendServers(ConnectHost endpoint, JWebToken token, String modpackKey, String label) throws IOException {
        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] requesting friend servers from {} endpoint={}:{} session={} modpackKey={}",
                label, endpoint.getAddress(), Integer.valueOf(endpoint.getProxyPort()), describeToken(token), describeModpackKey(modpackKey));
        List<CFriendServers.ServerEntry> result = serverEntries(NettyClient.getFriendServers(endpoint, token, modpackKey));
        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] friend-server search returned from {} endpoint={}:{} count={}",
                label, endpoint.getAddress(), Integer.valueOf(endpoint.getProxyPort()), Integer.valueOf(result.size()));
        logSearchEntries(label, result);
        return result;
    }

    private static List<CFriendServers.ServerEntry> searchAllNodes(JWebToken token, String modpackKey) {
        if (Boolean.getBoolean("mt.develop.connect")) {
            return Collections.emptyList();
        }

        List<GetConnectServersRequest.ConnectServer> servers;
        try {
            servers = pollServers();
        } catch (IOException ex) {
            DiagnosticLog.warn(LOGGER, "[MT-1710-DIAG] could not poll Connect nodes for all-node friend-server fallback", ex);
            return Collections.emptyList();
        }
        if (servers == null || servers.isEmpty()) {
            DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] all-node friend-server fallback skipped because no Connect nodes were returned");
            return Collections.emptyList();
        }

        Map<RemoteServer, CFriendServers.ServerEntry> results = new LinkedHashMap<RemoteServer, CFriendServers.ServerEntry>();
        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] selected-node friend-server search was empty; trying {} Connect nodes modpackKey={}",
                Integer.valueOf(servers.size()), modpackKey == null ? "<none>" : modpackKey);
        for (GetConnectServersRequest.ConnectServer server : servers) {
            if (server == null) continue;
            try {
                List<CFriendServers.ServerEntry> nodeResult = searchFriendServers(new ConnectHost(server), token, modpackKey,
                        "node:" + server.name);
                for (CFriendServers.ServerEntry entry : nodeResult) {
                    if (entry == null) continue;
                    CFriendServers.ServerEntry normalized = withFallbackNode(entry, server.name);
                    results.put(RemoteServer.fromEntry(normalized), normalized);
                }
            } catch (Throwable ex) {
                DiagnosticLog.warn(LOGGER, "[MT-1710-DIAG] friend-server search failed for Connect node {}", server.name, ex);
            }
        }
        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] all-node friend-server fallback result: count={} modpackKey={}",
                Integer.valueOf(results.size()), modpackKey == null ? "<none>" : modpackKey);
        return new ArrayList<CFriendServers.ServerEntry>(results.values());
    }

    private static CFriendServers.ServerEntry withFallbackNode(CFriendServers.ServerEntry entry, String fallbackNode) {
        String node = StringUtils.stripToNull(entry.node);
        String fallback = StringUtils.stripToNull(fallbackNode);
        if (node != null || fallback == null) {
            return entry;
        }
        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] stamped friend-server result with fallback node: friend={} node={}", entry.friend, fallback);
        return new CFriendServers.ServerEntry(entry.friend, entry.serverToken, fallback);
    }

    private static List<CFriendServers.ServerEntry> serverEntries(CFriendServers friendServers) {
        return friendServers == null || friendServers.servers == null
                ? Collections.<CFriendServers.ServerEntry>emptyList()
                : friendServers.servers;
    }

    public static Collection<RemoteServer> getRemoteServers() {
        return AVAILABLE_SERVER_MAP.keySet();
    }

    public static int getAvailableServerCount() {
        return AVAILABLE_SERVER_MAP.size();
    }

    public static String diagnosticState() {
        CompletableFuture<?> search = activeSearch;
        StringBuilder builder = new StringBuilder();
        builder.append("available=").append(AVAILABLE_SERVER_MAP.size());
        builder.append(" activeSearch=");
        if (search == null) {
            builder.append("none");
        } else if (search.isCancelled()) {
            builder.append("cancelled");
        } else if (search.isDone()) {
            builder.append("done");
        } else {
            builder.append("running");
        }
        builder.append(" pendingResult=").append(searchResult == null ? "<none>" : Integer.toString(searchResult.size()));
        builder.append(" lastSearchAgeMs=").append(lastSearch == 0L ? "<never>" : Long.toString(System.currentTimeMillis() - lastSearch));
        builder.append(" endpoint=").append(endpoint == null ? "<none>" : endpoint.getAddress() + ":" + endpoint.getProxyPort());
        builder.append(" published=").append(publishedServer != null);
        builder.append(" publishing=").append(publishing);
        builder.append(" status=").append(statusMessage);
        builder.append(" lastPublish=").append(lastPublishIdentity);
        builder.append(" lastSearch=").append(lastSearchIdentity);
        return builder.toString();
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
            PLAYER_LIST_MAX_PLAYERS.setInt(server.getConfigurationManager(), maxPlayers);
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

    private static ModpackIdentity getModpackIdentity() {
        ModPackInfo.VersionInfo info = ModPackInfo.getInfo();
        String modpackKey = StringUtils.stripToEmpty(info.getConnectPackKey());
        String source = StringUtils.isBlank(info.base64FTBID) ? "curse" : "ftb";
        if (modpackKey.isEmpty()) {
            source = "none";
            modpackKey = null;
        }
        return new ModpackIdentity(source, modpackKey, StringUtils.stripToEmpty(info.websiteID), StringUtils.stripToEmpty(info.realName));
    }

    public static String describeToken(JWebToken token) {
        if (token == null) {
            return "<none>";
        }
        long expiresIn = token.getExpiry() - System.currentTimeMillis();
        return "user=" + StringUtils.defaultIfBlank(token.getUsername(), "<blank>")
                + " uuidHash=" + maskLongValue(token.getUuidHash())
                + " token=" + fingerprint(token.toString())
                + " expiresInMs=" + expiresIn;
    }

    public static String describeModpackKey(String modpackKey) {
        if (StringUtils.isBlank(modpackKey)) {
            return "<none>";
        }
        return "len=" + modpackKey.length() + " fingerprint=" + fingerprint(modpackKey);
    }

    public static String describeServerToken(String serverToken) {
        return fingerprint(serverToken);
    }

    public static String describeFriendHash(String friendHash) {
        return maskLongValue(friendHash);
    }

    private static String describeIdentity(JWebToken token, ModpackIdentity modpack) {
        return "session{" + describeToken(token) + "} chat{" + describeChatIdentity(token) + "} modpack{" + modpack.describe() + "}";
    }

    private static String describeChatIdentity(JWebToken token) {
        String chatHash = MineTogetherChat.CHAT_AUTH == null ? null : MineTogetherChat.CHAT_AUTH.getHash();
        boolean matchesSession = token != null && StringUtils.equalsIgnoreCase(chatHash, token.getUuidHash());
        return "hash=" + maskLongValue(chatHash) + " matchesSession=" + matchesSession;
    }

    private static String describeFriendGraph(ProfileManager profileManager) {
        if (profileManager == null) {
            return "<missing>";
        }
        try {
            List<Profile> profiles = profileManager.getKnownProfiles();
            Profile ownProfile = profileManager.getOwnProfile();
            int friendCount = 0;
            int onlineFriends = 0;
            int fullProfiles = 0;
            int staleProfiles = 0;
            int updatingProfiles = 0;
            List<String> friendSamples = new ArrayList<String>();
            for (Profile profile : profiles) {
                if (profile.hasFullHash()) {
                    fullProfiles++;
                }
                if (profile.isStale()) {
                    staleProfiles++;
                }
                if (profile.isUpdating()) {
                    updatingProfiles++;
                }
                if (!profile.isFriend()) {
                    continue;
                }
                friendCount++;
                if (profile.isOnline()) {
                    onlineFriends++;
                }
                if (friendSamples.size() < 10) {
                    friendSamples.add(describeProfile(profile)
                            + ":online=" + profile.isOnline()
                            + ":stale=" + profile.isStale()
                            + ":updating=" + profile.isUpdating());
                }
            }
            return "own=" + describeProfile(ownProfile)
                    + " known=" + profiles.size()
                    + " full=" + fullProfiles
                    + " stale=" + staleProfiles
                    + " updating=" + updatingProfiles
                    + " friends=" + friendCount
                    + " onlineFriends=" + onlineFriends
                    + " sample=" + friendSamples;
        } catch (Throwable ex) {
            return "<error:" + ex.getClass().getSimpleName() + ">";
        }
    }

    private static String describeProfile(Profile profile) {
        if (profile == null) {
            return "<none>";
        }
        if (profile.hasFullHash()) {
            return maskLongValue(profile.getFullHash());
        }
        return maskLongValue(profile.getIrcName());
    }

    private static void logSearchEntries(String label, List<CFriendServers.ServerEntry> result) {
        if (!DiagnosticLog.enabled()) {
            return;
        }
        int maxLogged = Math.min(result.size(), 10);
        for (int i = 0; i < maxLogged; i++) {
            CFriendServers.ServerEntry entry = result.get(i);
            if (entry == null) {
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] friend-server search entry from {} index={} <null>", label, Integer.valueOf(i));
                continue;
            }
            DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] friend-server search entry from {} index={} friend={} serverToken={} node={}",
                    label, Integer.valueOf(i), describeFriendHash(entry.friend), describeServerToken(entry.serverToken),
                    StringUtils.defaultIfBlank(entry.node, "<local>"));
        }
        if (result.size() > maxLogged) {
            DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] friend-server search entry log truncated from {} total={}",
                    label, Integer.valueOf(result.size()));
        }
    }

    private static String maskLongValue(String value) {
        if (StringUtils.isBlank(value)) {
            return "<blank>";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 12) {
            return trimmed;
        }
        return trimmed.substring(0, 8) + "..." + trimmed.substring(trimmed.length() - 4);
    }

    private static String fingerprint(String value) {
        if (value == null) {
            return "<null>";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < Math.min(6, hashed.length); i++) {
                int next = hashed[i] & 0xFF;
                if (next < 16) {
                    builder.append('0');
                }
                builder.append(Integer.toHexString(next));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            return "len:" + value.length();
        }
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
        ClientTaskRunner.run(new Runnable() {
            @Override
            public void run() {
                if (Minecraft.getMinecraft().ingameGUI != null) {
                    Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(new net.minecraft.util.text.TextComponentTranslation(key, args));
                }
            }
        });
    }

    private static class ModpackIdentity {
        private final String source;
        private final String key;
        private final String websiteId;
        private final String identifier;

        private ModpackIdentity(String source, String key, String websiteId, String identifier) {
            this.source = source;
            this.key = key;
            this.websiteId = websiteId;
            this.identifier = identifier;
        }

        private String describe() {
            return "source=" + source
                    + " key=" + describeModpackKey(key)
                    + " websiteId=" + StringUtils.defaultIfBlank(websiteId, "<none>")
                    + " identifier=" + StringUtils.defaultIfBlank(identifier, "<none>");
        }
    }
}
