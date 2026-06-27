package net.creeperhost.minetogethercommunity.connect.gui;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.authlib.GameProfile;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogether.session.JWebToken;
import net.creeperhost.minetogethercommunity.connect.ConnectHandler;
import net.creeperhost.minetogethercommunity.connect.ConnectHost;
import net.creeperhost.minetogethercommunity.connect.RemoteServer;
import net.creeperhost.minetogethercommunity.connect.netty.NettyClient;
import net.creeperhost.minetogethercommunity.util.DiagnosticLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ServerSelectionList;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.ServerStatusResponse;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.status.INetHandlerStatusClient;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;
import net.minecraft.network.status.server.S00PacketServerInfo;
import net.minecraft.network.status.server.S01PacketPong;
import net.minecraft.realms.RealmsSharedConstants;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.text.TextComponentTranslation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerListAppender {

    public static final ServerListAppender INSTANCE = new ServerListAppender();

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Connect");
    private static final Field SERVER_LIST_SELECTOR = findField(GuiMultiplayer.class, "serverListSelector", "field_146803_h", "h");
    private static final Field SERVER_LIST_INTERNET = findField(ServerSelectionList.class, "serverListInternet", "field_148198_l", "v");
    private static final ExecutorService PING_EXECUTOR = Executors.newFixedThreadPool(5, new ThreadFactoryBuilder().setDaemon(true).setNameFormat("MT Connect Ping %d").build());
    private static final String CANT_CONNECT = new TextComponentTranslation("multiplayer.status.cannot_connect").getFormattedText();

    private final Map<RemoteServer, FriendServerEntry> serverEntries = new LinkedHashMap<RemoteServer, FriendServerEntry>();
    private final List<NetworkManager> pingConnections = Collections.synchronizedList(new ArrayList<NetworkManager>());
    private GuiMultiplayer multiplayerScreen;
    private ServerSelectionList serverList;
    private int tick;
    private long lastScreenDiagnostic;

    private ServerListAppender() {
    }

    public void init(GuiMultiplayer screen) {
        if (!ConnectHandler.isEnabled()) return;
        if (screen == multiplayerScreen && serverList != null) {
            appendEntries();
            return;
        }

        remove();
        multiplayerScreen = screen;
        serverList = getServerList(screen);
        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] initialized multiplayer friend-server hook: serverList={}", serverList == null ? "<missing>" : serverList.getClass().getName());
        ConnectHandler.clearAndReset();
        ConnectHandler.updateFriendsSearch();
        refreshEntries();
        logScreenState("init", true);
    }

    public void tick(GuiScreen currentScreen) {
        processPingConnections();
        if (!(currentScreen instanceof GuiMultiplayer) || !ConnectHandler.isEnabled()) {
            remove();
            return;
        }
        GuiMultiplayer screen = (GuiMultiplayer) currentScreen;
        if (screen != multiplayerScreen || serverList == null) {
            init(screen);
            return;
        }

        if (tick++ % 20 != 0) return;
        ConnectHandler.updateFriendsSearch();
        refreshEntries();
        logScreenState("tick", false);
    }

    public void remove() {
        removeEntriesFromList();
        serverEntries.clear();
        closePingConnections();
        multiplayerScreen = null;
        serverList = null;
        tick = 0;
        lastScreenDiagnostic = 0L;
    }

    public void openSelected(GuiMultiplayer screen) {
        FriendServerEntry entry = getSelectedFriendEntry(screen);
        if (entry != null) {
            Minecraft.getMinecraft().displayGuiScreen(new FriendConnectScreen(screen, entry.getRemoteServer()));
        }
    }

    public boolean hasSelectedFriendServer(GuiMultiplayer screen) {
        return getSelectedFriendEntry(screen) != null;
    }

    private void refreshEntries() {
        if (multiplayerScreen == null || serverList == null) {
            DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] skipped friend-server refresh because multiplayerScreen={} serverList={}",
                    multiplayerScreen == null ? "<missing>" : "<present>",
                    serverList == null ? "<missing>" : "<present>");
            return;
        }

        boolean dirty = false;
        List<RemoteServer> remoteServers = new ArrayList<RemoteServer>(ConnectHandler.getRemoteServers());
        for (RemoteServer remoteServer : remoteServers) {
            Profile profile = ConnectHandler.getServerProfile(remoteServer);
            FriendServerEntry existing = serverEntries.get(remoteServer);
            if (existing == null || existing.getFriendProfile() != profile) {
                serverEntries.put(remoteServer, new FriendServerEntry(multiplayerScreen, remoteServer, profile));
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] added/updated friend-server row: friend={} node={} profile={}",
                        remoteServer.getFriendHash(),
                        remoteServer.getNode() == null ? "<auto>" : remoteServer.getNode(),
                        profile == null ? "<missing>" : profile.isStale() ? "<stale>" : profile.getDisplayName());
                dirty = true;
            }
        }

        Iterator<RemoteServer> iterator = serverEntries.keySet().iterator();
        while (iterator.hasNext()) {
            if (!remoteServers.contains(iterator.next())) {
                iterator.remove();
                dirty = true;
            }
        }

        if (dirty) {
            appendEntries();
        } else {
            ensureFriendRowsPresent();
        }
    }

    @SuppressWarnings("unchecked")
    private void appendEntries() {
        List<GuiListExtended.IGuiListEntry> entries = getInternetEntries();
        if (entries == null) return;
        removeFriendEntries(entries);
        entries.addAll(0, serverEntries.values());
        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] inserted MineTogether friend-server rows: rows={} vanillaEntriesAfter={}",
                Integer.valueOf(serverEntries.size()), Integer.valueOf(entries.size()));
    }

    private void ensureFriendRowsPresent() {
        if (serverEntries.isEmpty()) return;
        List<GuiListExtended.IGuiListEntry> entries = getInternetEntries();
        if (entries == null) return;
        if (entries.containsAll(serverEntries.values())) return;
        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] repairing missing MineTogether friend-server rows: rows={} vanillaEntriesBefore={}",
                Integer.valueOf(serverEntries.size()), Integer.valueOf(entries.size()));
        appendEntries();
    }

    private void removeEntriesFromList() {
        List<GuiListExtended.IGuiListEntry> entries = getInternetEntries();
        if (entries != null) {
            removeFriendEntries(entries);
        }
    }

    private void removeFriendEntries(List<GuiListExtended.IGuiListEntry> entries) {
        Iterator<GuiListExtended.IGuiListEntry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() instanceof FriendServerEntry) {
                iterator.remove();
            }
        }
    }

    private void logScreenState(String reason, boolean force) {
        long now = Minecraft.getSystemTime();
        if (!force && now - lastScreenDiagnostic < 5000L) return;
        lastScreenDiagnostic = now;

        List<GuiListExtended.IGuiListEntry> entries = getInternetEntries();
        int internetEntries = entries == null ? -1 : entries.size();
        int friendRowsInList = 0;
        if (entries != null) {
            for (GuiListExtended.IGuiListEntry entry : entries) {
                if (entry instanceof FriendServerEntry) {
                    friendRowsInList++;
                }
            }
        }

        int selected = -1;
        if (serverList != null) {
            try {
                selected = serverList.func_148193_k();
            } catch (RuntimeException ignored) {
            }
        }

        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] multiplayer friend-server screen state reason={} available={} rowCache={} internetEntries={} friendRowsInList={} selected={} connectState={}",
                reason, Integer.valueOf(ConnectHandler.getAvailableServerCount()), Integer.valueOf(serverEntries.size()),
                Integer.valueOf(internetEntries), Integer.valueOf(friendRowsInList), Integer.valueOf(selected),
                ConnectHandler.diagnosticState());
    }

    private FriendServerEntry getSelectedFriendEntry(GuiMultiplayer screen) {
        if (screen != multiplayerScreen || serverList == null) return null;
        int selected = serverList.func_148193_k();
        if (selected < 0) return null;
        GuiListExtended.IGuiListEntry entry;
        try {
            entry = serverList.getListEntry(selected);
        } catch (RuntimeException ex) {
            return null;
        }
        return entry instanceof FriendServerEntry ? (FriendServerEntry) entry : null;
    }

    public void pingServer(final RemoteServer server, final Profile profile) {
        PING_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                final NetworkManager networkManager;
                try {
                    ConnectHost endpoint = ConnectHandler.getSpecificEndpoint(server.getNode());
                    JWebToken token = ConnectHandler.requireSessionToken();
                    DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] pinging friend-server row friend={} node={} endpoint={}:{}",
                            server.getFriendHash(),
                            server.getNode() == null ? "<auto>" : server.getNode(),
                            endpoint.getAddress(),
                            Integer.valueOf(endpoint.getProxyPort()));
                    networkManager = NettyClient.connect(endpoint, token, server.getServerToken(), true);
                    pingConnections.add(networkManager);
                    networkManager.setNetHandler(new StatusHandler(networkManager, server, profile));
                    networkManager.sendPacket(new C00Handshake(RealmsSharedConstants.NETWORK_PROTOCOL_VERSION, endpoint.getAddress(), endpoint.getProxyPort(), EnumConnectionState.STATUS));
                    networkManager.sendPacket(new C00PacketServerQuery());
                } catch (Exception ex) {
                    LOGGER.warn("Failed to ping MineTogether friend server {}", server.getFriendHash(), ex);
                    markPingFailed(server, ex.getMessage());
                }
            }
        });
    }

    private void processPingConnections() {
        synchronized (pingConnections) {
            Iterator<NetworkManager> iterator = pingConnections.iterator();
            while (iterator.hasNext()) {
                NetworkManager manager = iterator.next();
                if (manager.isChannelOpen()) {
                    manager.processReceivedPackets();
                } else {
                    if (manager.getNetHandler() != null) {
                        IChatComponent reason = manager.getExitMessage();
                        if (reason == null) {
                            reason = new TextComponentTranslation("disconnect.endOfStream");
                        }
                        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] friend-server ping connection closed reason={}", reason.getUnformattedText());
                        manager.getNetHandler().onDisconnect(reason);
                    }
                    iterator.remove();
                }
            }
        }
    }

    private void closePingConnections() {
        synchronized (pingConnections) {
            Iterator<NetworkManager> iterator = pingConnections.iterator();
            while (iterator.hasNext()) {
                NetworkManager manager = iterator.next();
                iterator.remove();
                if (manager.isChannelOpen()) {
                    manager.closeChannel(new TextComponentTranslation("multiplayer.status.cancelled"));
                }
            }
        }
    }

    private static void markPingFailed(RemoteServer server, String reason) {
        server.setPing(-1L);
        server.setMotd(CANT_CONNECT);
        server.setStatus("");
        server.setPlayerList(Collections.<String>emptyList());
        if (reason != null && !reason.trim().isEmpty()) {
            LOGGER.debug("MineTogether friend server ping failed: {}", reason);
        }
    }

    private static String formatPlayerCount(int online, int max) {
        return online + "/" + (max == Integer.MAX_VALUE ? "\u221E" : Integer.toString(max));
    }

    private static String componentText(IChatComponent component) {
        return component == null ? "" : component.getFormattedText();
    }

    private static class StatusHandler implements INetHandlerStatusClient {
        private final NetworkManager networkManager;
        private final RemoteServer server;
        private final Profile profile;
        private boolean receivedInfo;
        private boolean completed;
        private long pingStart;

        private StatusHandler(NetworkManager networkManager, RemoteServer server, Profile profile) {
            this.networkManager = networkManager;
            this.server = server;
            this.profile = profile;
        }

        @Override
        public void handleServerInfo(S00PacketServerInfo packetIn) {
            if (receivedInfo) {
                networkManager.closeChannel(new TextComponentTranslation("multiplayer.status.unrequested"));
                return;
            }
            receivedInfo = true;
            ServerStatusResponse response = packetIn.getResponse();
            if (response == null) {
                markPingFailed(server, "empty response");
                networkManager.closeChannel(new TextComponentTranslation("multiplayer.status.cannot_connect"));
                return;
            }
            DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] received friend-server status info friend={}", server.getFriendHash());

            server.setMotd(componentText(response.getServerDescription()));
            ServerStatusResponse.MinecraftProtocolVersionIdentifier version = response.getProtocolVersionInfo();
            if (version != null) {
                server.setVersion(version.getName());
                server.setProtocol(version.getProtocol());
            } else {
                server.setVersion(new TextComponentTranslation("multiplayer.status.old").getFormattedText());
                server.setProtocol(0);
            }

            ServerStatusResponse.PlayerCountData players = response.getPlayerCountData();
            if (players != null) {
                server.setStatus(formatPlayerCount(players.getOnlinePlayerCount(), players.getMaxPlayers()));
                List<String> playerList = new ArrayList<String>();
                GameProfile[] samples = players.getPlayers();
                if (samples != null) {
                    for (GameProfile sample : samples) {
                        if (sample != null && sample.getName() != null) {
                            playerList.add(sample.getName());
                        }
                    }
                    int hidden = players.getOnlinePlayerCount() - playerList.size();
                    if (hidden > 0) {
                        playerList.add(new TextComponentTranslation("multiplayer.status.and_more", hidden).getFormattedText());
                    }
                }
                server.setPlayerList(playerList);
            } else {
                server.setStatus(new TextComponentTranslation("multiplayer.status.unknown").getFormattedText());
                server.setPlayerList(Collections.<String>emptyList());
            }

            pingStart = Minecraft.getSystemTime();
            networkManager.sendPacket(new C01PacketPing(pingStart));
        }

        @Override
        public void handlePong(S01PacketPong packetIn) {
            server.setPing(Minecraft.getSystemTime() - pingStart);
            completed = true;
            DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] friend-server ping complete friend={} ping={}ms",
                    server.getFriendHash(), Long.valueOf(server.getPing()));
            networkManager.closeChannel(new TextComponentTranslation("multiplayer.status.finished"));
        }

        @Override
        public void onDisconnect(IChatComponent reason) {
            if (!completed) {
                String name = profile == null ? server.getFriendHash() : profile.getDisplayName();
                LOGGER.warn("Could not ping MineTogether friend server for {}: {}", name, reason == null ? "unknown" : reason.getUnformattedText());
                markPingFailed(server, reason == null ? null : reason.getUnformattedText());
            }
        }

        public void onNetworkTick() {
        }

        public void onConnectionStateTransition(EnumConnectionState oldState, EnumConnectionState newState) {
        }
    }

    private ServerSelectionList getServerList(GuiMultiplayer screen) {
        try {
            return (ServerSelectionList) SERVER_LIST_SELECTOR.get(screen);
        } catch (IllegalAccessException ex) {
            LOGGER.error("Could not access multiplayer server list", ex);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<GuiListExtended.IGuiListEntry> getInternetEntries() {
        if (serverList == null) return null;
        try {
            return (List<GuiListExtended.IGuiListEntry>) SERVER_LIST_INTERNET.get(serverList);
        } catch (IllegalAccessException ex) {
            LOGGER.error("Could not access multiplayer server entries", ex);
            return null;
        }
    }

    private static Field findField(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new IllegalStateException("Could not find field on " + owner.getName());
    }
}
