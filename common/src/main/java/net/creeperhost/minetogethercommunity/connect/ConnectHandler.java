package net.creeperhost.minetogethercommunity.connect;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogether.connect.lib.netty.packet.CFriendServers;
import net.creeperhost.minetogethercommunity.connect.netty.HostNettyClient;
import net.creeperhost.minetogethercommunity.connect.netty.NettyClient;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogether.lib.chat.profile.ProfileManager;
import net.creeperhost.minetogether.session.JWebToken;
import net.creeperhost.minetogether.session.MineTogetherSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by brandon3055 on 21/04/2023
 */
public class ConnectHandler {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<RemoteServer, Profile> AVAILABLE_SERVER_MAP = new HashMap<>();
    private static final ExecutorService SEARCH_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("MT Connect Friend Search Executor").build());
    private static final ExecutorService SHARE_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("MT Connect Friend Share Executor").build());
    private static final ConnectAvailability AVAILABILITY = new ConnectAvailability();
    private static final AtomicInteger TEST_SEARCH_ATTEMPTS = new AtomicInteger();
    private static volatile Callable<List<CFriendServers.ServerEntry>> friendSearch = ConnectHandler::requestFriendServers;

    private static long lastSearch = 0;
    private static CompletableFuture<?> activeSearch = null;
    private static List<CFriendServers.ServerEntry> searchResult = null;
    private static int publishedMaxPlayers = -1;

    private static boolean didWeShareFirst = false;
    private static HostNettyClient.HostConnection publishedServer;

    public static void init() {
    }

    public static ConnectHost getEndpoint() {
        return ConnectServices.getEndpoint();
    }

    public static ConnectHost getSpecificEndpoint(@Nullable String node) throws IOException {
        return ConnectServices.getSpecificEndpoint(node);
    }

    public static boolean isEnabled() {
        return AVAILABILITY.isAvailable();
    }

    public static void publishToFriends(GameType gameType, boolean cheats, int maxPlayers) {
        // Mostly copy of IntegratedServer#publishServer
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;
        setPublishedMaxPlayers(Math.min(2, maxPlayers)); // Set to the minimum; the proxy may raise this after it reports the account cap.
        mc.prepareForMultiplayer();

        if (server.isPublished()) {
            didWeShareFirst = false;
        } else {
            didWeShareFirst = true;
            server.publishedPort = 0; // Doesn't matter, just set to _something_.
            server.setMultiplayerScope(getProxyMultiplayerScope());
        }
        server.setGameTypeForOtherPlayers(gameType);
        server.setCommandsAllowedForOtherPlayers(cheats);

        CompletableFuture.runAsync(() -> {
            try { // TODO, This should be done outside somewhere.
                JWebToken token = MineTogetherSession.getDefault().getTokenAsync().get();
                publishedServer = NettyClient.publishServer(server, getEndpoint(), token, getModpackKey(), maxPlayers);
            } catch (Exception e) {
                Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.translatable("minetogether.connect.open.failed", e.getMessage()));
                LOGGER.error("Failed to open to friends", e);
                unPublish();
            }
        }, SHARE_EXECUTOR);
    }

    public static void unPublish() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        setPublishedMaxPlayers(-1);
        if (server == null) return;
        // This will yeet the control socket, which, should cause the proxy to sever all other connections.
        if (publishedServer != null) {
            publishedServer.disconnect();
            publishedServer = null;
        }
        if (didWeShareFirst) {
            //Un-Share the world.
            server.publishedPort = -1;
            server.setMultiplayerScope(MinecraftServer.MultiplayerScope.OFF);
            server.gameTypeForOtherPlayers = null;
            server.commandsAllowedForOtherPlayers = null;
            server.updateCommandsAllowedForOtherPlayers();
        }
    }

    public static boolean isPublished() {
        return publishedServer != null;
    }

    private static MinecraftServer.MultiplayerScope getProxyMultiplayerScope() {
        try {
            return MinecraftServer.MultiplayerScope.valueOf("ONLINE");
        } catch (IllegalArgumentException ignored) {
            return MinecraftServer.MultiplayerScope.LAN;
        }
    }

    public static int getPublishedMaxPlayers() {
        return publishedMaxPlayers;
    }

    public static void setPublishedMaxPlayers(int maxPlayers) {
        publishedMaxPlayers = maxPlayers;
    }

    public static void updateFriendsSearch() {
        if (activeSearch != null) {
            if (!activeSearch.isDone()) return;

            activeSearch = null;

            if (searchResult != null) {
                ProfileManager profileManager = MineTogetherChat.CHAT_STATE.profileManager;
                Set<RemoteServer> keep = new HashSet<>();
                for (CFriendServers.ServerEntry entry : searchResult) {
                    RemoteServer server = RemoteServer.fromEntry(entry);
                    ConnectPackResolver.prefetch(server.modpackKey);
                    keep.add(server);
                    if (!AVAILABLE_SERVER_MAP.containsKey(server)) {
                        Profile profile = profileManager.lookupProfile(entry.friend);
                        AVAILABLE_SERVER_MAP.put(server, profile);
                    }
                }

                AVAILABLE_SERVER_MAP.entrySet().removeIf(entry -> !keep.contains(entry.getKey()));
                searchResult = null;
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (!AVAILABILITY.shouldAttempt(now) || now - lastSearch < 5000) {
            return;
        }
        lastSearch = now;

        activeSearch = CompletableFuture.runAsync(() -> {
            searchResult = null;
            try {
                searchResult = friendSearch.call();
                AVAILABILITY.succeeded();
            } catch (Throwable e) {
                if (AVAILABILITY.failed(System.currentTimeMillis(), e)) {
                    LOGGER.error("MineTogether Connect discovery is unavailable; retrying in 30 seconds.", e);
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
    }

    private static List<CFriendServers.ServerEntry> requestFriendServers() throws Exception {
        JWebToken token = MineTogetherSession.getDefault().getTokenAsync().get();
        return NettyClient.getFriendServers(getEndpoint(), token, getModpackKey()).servers;
    }

    /** CI-only seam: exercises unavailable discovery without contacting production services. */
    public static void configureUnavailableForTesting() {
        if (!"connect-unavailable".equals(System.getenv("MINETOGETHER_CI_ROLE"))) {
            throw new IllegalStateException("Local Connect testing is only available to the CI probe");
        }
        TEST_SEARCH_ATTEMPTS.set(0);
        AVAILABILITY.reset();
        friendSearch = () -> {
            TEST_SEARCH_ATTEMPTS.incrementAndGet();
            throw new IOException("MineTogether CI forced discovery failure");
        };
    }

    public static int getTestSearchAttempts() {
        return TEST_SEARCH_ATTEMPTS.get();
    }

    private static @Nullable String getModpackKey() {
        return ConnectServices.getModpackKey();
    }
}
