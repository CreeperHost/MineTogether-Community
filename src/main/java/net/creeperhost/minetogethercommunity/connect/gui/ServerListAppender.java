package net.creeperhost.minetogethercommunity.connect.gui;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.authlib.GameProfile;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogether.session.JWebToken;
import net.creeperhost.minetogethercommunity.connect.ConnectHandler;
import net.creeperhost.minetogethercommunity.connect.ConnectHost;
import net.creeperhost.minetogethercommunity.connect.RemoteServer;
import net.creeperhost.minetogethercommunity.connect.netty.NettyClient;
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
import net.minecraft.network.status.client.CPacketPing;
import net.minecraft.network.status.client.CPacketServerQuery;
import net.minecraft.network.status.server.SPacketPong;
import net.minecraft.network.status.server.SPacketServerInfo;
import net.minecraft.util.text.ITextComponent;
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
        ConnectHandler.clearAndReset();
        ConnectHandler.updateFriendsSearch();
        refreshEntries();
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
    }

    public void remove() {
        removeEntriesFromList();
        serverEntries.clear();
        closePingConnections();
        multiplayerScreen = null;
        serverList = null;
        tick = 0;
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
        if (multiplayerScreen == null || serverList == null) return;

        boolean dirty = false;
        List<RemoteServer> remoteServers = new ArrayList<RemoteServer>(ConnectHandler.getRemoteServers());
        for (RemoteServer remoteServer : remoteServers) {
            if (!serverEntries.containsKey(remoteServer)) {
                Profile profile = ConnectHandler.getServerProfile(remoteServer);
                if (profile == null || profile.isStale()) {
                    continue;
                }
                serverEntries.put(remoteServer, new FriendServerEntry(multiplayerScreen, remoteServer, profile));
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
        }
    }

    @SuppressWarnings("unchecked")
    private void appendEntries() {
        List<GuiListExtended.IGuiListEntry> entries = getInternetEntries();
        if (entries == null) return;
        removeFriendEntries(entries);
        entries.addAll(serverEntries.values());
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

    private FriendServerEntry getSelectedFriendEntry(GuiMultiplayer screen) {
        if (screen != multiplayerScreen || serverList == null) return null;
        int selected = serverList.getSelected();
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
                    networkManager = NettyClient.connect(endpoint, token, server.getServerToken(), true);
                    pingConnections.add(networkManager);
                    networkManager.setNetHandler(new StatusHandler(networkManager, server, profile));
                    networkManager.sendPacket(new C00Handshake(endpoint.getAddress(), endpoint.getProxyPort(), EnumConnectionState.STATUS, true));
                    networkManager.sendPacket(new CPacketServerQuery());
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
                    iterator.remove();
                    manager.handleDisconnection();
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

    private static String componentText(ITextComponent component) {
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
        public void handleServerInfo(SPacketServerInfo packetIn) {
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

            server.setMotd(componentText(response.getServerDescription()));
            ServerStatusResponse.Version version = response.getVersion();
            if (version != null) {
                server.setVersion(version.getName());
                server.setProtocol(version.getProtocol());
            } else {
                server.setVersion(new TextComponentTranslation("multiplayer.status.old").getFormattedText());
                server.setProtocol(0);
            }

            ServerStatusResponse.Players players = response.getPlayers();
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
            networkManager.sendPacket(new CPacketPing(pingStart));
        }

        @Override
        public void handlePong(SPacketPong packetIn) {
            server.setPing(Minecraft.getSystemTime() - pingStart);
            completed = true;
            networkManager.closeChannel(new TextComponentTranslation("multiplayer.status.finished"));
        }

        @Override
        public void onDisconnect(ITextComponent reason) {
            if (!completed) {
                String name = profile == null ? server.getFriendHash() : profile.getDisplayName();
                LOGGER.warn("Could not ping MineTogether friend server for {}: {}", name, reason == null ? "unknown" : reason.getUnformattedText());
                markPingFailed(server, reason == null ? null : reason.getUnformattedText());
            }
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
