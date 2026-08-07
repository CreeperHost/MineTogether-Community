package net.creeperhost.minetogethercommunity.ci;

import com.google.common.hash.Hashing;
import net.creeperhost.minetogethercommunity.chat.ChatTarget;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.emote.Emote;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteAnimation;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteNetworking;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteType;
import net.creeperhost.minetogethercommunity.connect.ConnectHandler;
import net.creeperhost.minetogethercommunity.connect.RemoteServer;
import net.creeperhost.minetogethercommunity.connect.gui.ConnectPackWarningScreen;
import net.creeperhost.minetogethercommunity.connect.gui.FriendConnectScreen;
import net.creeperhost.minetogethercommunity.connect.gui.FriendServerEntry;
import net.creeperhost.minetogethercommunity.connect.gui.GuiShareToFriends;
import net.creeperhost.minetogethercommunity.connect.gui.ServerListAppender;
import net.creeperhost.minetogethercommunity.modulargui.GuiElement;
import net.creeperhost.minetogethercommunity.modulargui.ModularGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Java 8 CI-only runtime probe. It is built into a separate reobfuscated jar and is never
 * included in MineTogether release artifacts.
 */
public final class MineTogetherCiProbe {

    private static final String PREFIX = "[MT-CI] ";
    private static final String TEST_EMOTE = "ci_packet_probe";
    private static final String TEST_CHAT_MESSAGE = "MT-CI-OFFLINE-CHAT-RELAY";
    private static final String CONNECT_NOT_FRIEND = "You cannot join as you are not MineTogether friends with this user!";
    private static final String CONNECT_FULL = "MineTogether friends' server is full. Please contact your friend!";
    private static final int DEFAULT_TIMEOUT_TICKS = 20 * 180;

    private static boolean initialized;
    private static String role;
    private static String peerName;
    private static String serverAddress;
    private static int expectedPlayers;
    private static int timeoutTicks;
    private static Path resultDirectory;
    private static int ticks;
    private static int stableTicks;
    private static int phaseTicks;
    private static boolean joined;
    private static boolean connectionStarted;
    private static boolean started;
    private static boolean stopped;
    private static UUID peerId;
    private static Map<UUID, ?> activeEmotes;
    private static int chatPort;
    private static int shutdownTicks;
    private static int chatPhase;
    private static UUID connectUuid;
    private static String connectServerToken;
    private static String connectHostHash;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        role = setting("minetogether.ci.role", "MINETOGETHER_CI_ROLE", "").trim();
        if (role.isEmpty()) return;

        peerName = setting("minetogether.ci.peerName", "MINETOGETHER_CI_PEER_NAME", "CiSender").trim();
        serverAddress = setting("minetogether.ci.serverAddress", "MINETOGETHER_CI_SERVER_ADDRESS", "").trim();
        expectedPlayers = integerSetting("minetogether.ci.expectedPlayers", "MINETOGETHER_CI_EXPECTED_PLAYERS", 1);
        timeoutTicks = integerSetting("minetogether.ci.timeoutTicks", "MINETOGETHER_CI_TIMEOUT_TICKS", DEFAULT_TIMEOUT_TICKS);
        resultDirectory = Paths.get(setting("minetogether.ci.results", "MINETOGETHER_CI_RESULTS", "build/ci-multiplayer/results"))
                .toAbsolutePath().normalize();
        chatPort = integerSetting("minetogether.ci.chatPort", "MINETOGETHER_CI_CHAT_PORT", 0);
        connectServerToken = setting("minetogether.ci.connectServerToken", "MINETOGETHER_CI_CONNECT_SERVER_TOKEN", "server-0001").trim();
        connectHostHash = setting("minetogether.ci.connectHostHash", "MINETOGETHER_CI_CONNECT_HOST_HASH", "").trim();

        try {
            Files.createDirectories(resultDirectory);
            if (role.startsWith("chat-") || role.startsWith("ctcp-")) {
                if (chatPort <= 0) throw new IllegalStateException("Local chat test requires MINETOGETHER_CI_CHAT_PORT");
                CiChatMock.install(chatPort, role, resultDirectory);
            }
            if (role.startsWith("connect-") && System.getenv("MINETOGETHER_CI_CONNECT_UUID") != null) {
                if (chatPort <= 0) throw new IllegalStateException("Local Connect test requires MINETOGETHER_CI_CHAT_PORT");
                connectUuid = UUID.fromString(System.getenv("MINETOGETHER_CI_CONNECT_UUID"));
                CiConnectMock.install(chatPort, role, connectUuid, resultDirectory);
            }
            if (role.equals("connect-ui") || role.equals("connect-host")) {
                GuiShareToFriends.configureLocalForTesting(role.equals("connect-host") ? 2 : 8);
            }
            if (role.equals("connect-unavailable")) {
                ConnectHandler.configureUnavailableForTesting();
            }
            activeEmotes = activeEmotes();
            installTestEmote();
            marker(role + "-started");
        } catch (Exception exception) {
            throw new IllegalStateException("Could not initialize MineTogether CI probe", exception);
        }

        System.out.println(PREFIX + "Started role=" + role + ", expectedPlayers=" + expectedPlayers);
        MinecraftForge.EVENT_BUS.register(new MineTogetherCiProbe());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick(Minecraft.getMinecraft());
    }

    private static void tick(Minecraft minecraft) {
        if (shutdownTicks > 0) {
            if (--shutdownTicks == 0) minecraft.shutdown();
            return;
        }
        if (role == null || role.isEmpty()) return;
        if (++ticks > timeoutTicks) {
            fail("Timed out in role " + role + " after " + ticks + " ticks");
            return;
        }

        if (role.equals("connect-friend")) {
            try {
                tickConnectFriend(minecraft);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                fail("Role " + role + " failed: " + throwable);
            }
            return;
        }
        if (role.equals("connect-outsider") || role.equals("connect-extra")) {
            try {
                tickConnectRejected(minecraft, role.equals("connect-outsider") ? CONNECT_NOT_FRIEND : CONNECT_FULL);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                fail("Role " + role + " failed: " + throwable);
            }
            return;
        }
        if (role.equals("connect-unavailable")) {
            tickConnectUnavailable(minecraft);
            return;
        }

        if (minecraft.player == null || minecraft.world == null) {
            if (role.equals("singleplayer") || role.equals("connect-ui") || role.equals("connect-host")) {
                createSingleplayerIfNeeded(minecraft);
            } else {
                connectIfNeeded(minecraft);
            }
            stableTicks = 0;
            return;
        }

        int players = minecraft.world.playerEntities.size();
        if (players < expectedPlayers) {
            if (role.equals("ctcp-receiver") && stopped && exists("ctcp-sender-success")) {
                success(minecraft);
                return;
            }
            if (joined) fail("Player count dropped from " + expectedPlayers + " to " + players);
            stableTicks = 0;
            return;
        }

        stableTicks++;
        if (!joined) {
            joined = true;
            marker(role + "-joined");
            System.out.println(PREFIX + role + " joined with " + players + " visible player(s)");
        }

        try {
            if (role.equals("singleplayer")) tickSingleplayer(minecraft);
            else if (role.equals("connect-ui")) tickConnectUi(minecraft);
            else if (role.equals("connect-host")) tickConnectHost(minecraft);
            else if (role.equals("connect")) tickConnect(minecraft);
            else if (role.equals("mixed-sender")) tickMixedSender(minecraft);
            else if (role.equals("sender")) tickSender(minecraft);
            else if (role.equals("receiver")) tickReceiver(minecraft);
            else if (role.equals("late-sender")) tickLateSender(minecraft);
            else if (role.equals("late-receiver")) tickLateReceiver(minecraft);
            else if (role.equals("chat-sender")) tickChatSender(minecraft);
            else if (role.equals("chat-receiver")) tickChatReceiver(minecraft);
            else if (role.equals("ctcp-sender")) tickCtcpSender(minecraft);
            else if (role.equals("ctcp-receiver")) tickCtcpReceiver(minecraft);
            else fail("Unknown CI probe role: " + role);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            fail("Role " + role + " failed: " + throwable);
        }
    }

    private static void connectIfNeeded(Minecraft minecraft) {
        if (connectionStarted || serverAddress.isEmpty() || minecraft.currentScreen == null || ticks < 40) return;
        String[] address = serverAddress.split(":", 2);
        int port = address.length == 2 ? Integer.parseInt(address[1]) : 25565;
        connectionStarted = true;
        marker(role + "-connecting");
        System.out.println(PREFIX + "Connecting role=" + role + " to " + serverAddress);
        minecraft.displayGuiScreen(new GuiConnecting(
                minecraft.currentScreen,
                minecraft,
                new ServerData("MineTogether CI", address[0] + ":" + port, false)
        ));
    }

    private static void createSingleplayerIfNeeded(Minecraft minecraft) {
        if (connectionStarted || minecraft.currentScreen == null || ticks < 40) return;
        connectionStarted = true;
        marker("singleplayer-creating");
        WorldSettings settings = new WorldSettings(0L, GameType.CREATIVE, true, false, WorldType.DEFAULT);
        minecraft.launchIntegratedServer("minetogether-ci", "MineTogether CI", settings);
    }

    private static void tickSingleplayer(Minecraft minecraft) {
        if (stableTicks >= 80 && minecraft.isSingleplayer()) {
            marker("singleplayer-world-ready");
            success(minecraft);
        }
    }

    private static void tickConnectUi(Minecraft minecraft) throws IOException {
        if (stableTicks < 80) return;
        if (chatPhase == 0) {
            minecraft.displayGuiScreen(new GuiIngameMenu());
            phaseTicks = ticks;
            chatPhase = 1;
            return;
        }
        if (chatPhase == 1 && ticks - phaseTicks >= 20 && minecraft.currentScreen instanceof GuiIngameMenu) {
            clickVanillaButton(minecraft.currentScreen, "minetogether.connect.open");
            phaseTicks = ticks;
            chatPhase = 2;
            return;
        }
        if (chatPhase == 2 && ticks - phaseTicks >= 40) {
            if (!(minecraft.currentScreen instanceof GuiShareToFriends.Screen)) {
                fail("Open to friends did not open its settings screen");
                return;
            }
            translated("minetogether.connect.open.settings");
            translated("minetogether.connect.open.max_players");
            translated("minetogether.connect.open.start");
            marker("connect-ui-settings-ready");
            success(minecraft);
        }
    }

    private static void tickConnectHost(Minecraft minecraft) throws IOException {
        if (stableTicks < 80) return;
        switch (chatPhase) {
            case 0:
                minecraft.getIntegratedServer().setOnlineMode(false);
                minecraft.displayGuiScreen(new GuiIngameMenu());
                phaseTicks = ticks;
                chatPhase = 1;
                return;
            case 1:
                if (ticks - phaseTicks < 20 || !(minecraft.currentScreen instanceof GuiIngameMenu)) return;
                clickVanillaButton(minecraft.currentScreen, "minetogether.connect.open");
                phaseTicks = ticks;
                chatPhase = 2;
                return;
            case 2:
                if (ticks - phaseTicks < 40 || !(minecraft.currentScreen instanceof GuiShareToFriends.Screen)) return;
                clickModularButton((ModularGuiScreen) minecraft.currentScreen, "minetogether.connect.open.start");
                chatPhase = 3;
                return;
            case 3:
                if (!ConnectHandler.isPublished()) return;
                minecraft.getIntegratedServer().setOnlineMode(false);
                marker("connect-host-published");
                chatPhase = 4;
                return;
            case 4:
                if (exists("connect-friend-joined") && minecraft.getIntegratedServer().getPlayerList().getCurrentPlayerCount() >= 2
                        && !exists("connect-host-friend-visible")) marker("connect-host-friend-visible");
                if (!exists("connect-host-close-request")) return;
                minecraft.displayGuiScreen(new GuiIngameMenu());
                phaseTicks = ticks;
                chatPhase = 5;
                return;
            case 5:
                if (ticks - phaseTicks < 20 || !(minecraft.currentScreen instanceof GuiIngameMenu)) return;
                clickVanillaButton(minecraft.currentScreen, "minetogether.connect.close_server");
                chatPhase = 6;
                return;
            case 6:
                if (ConnectHandler.isPublished()) return;
                marker("connect-host-closed");
                chatPhase = 7;
                return;
            case 7:
                if (!exists("connect-host-republish-request")) return;
                minecraft.displayGuiScreen(new GuiIngameMenu());
                phaseTicks = ticks;
                chatPhase = 8;
                return;
            case 8:
                if (ticks - phaseTicks < 20 || !(minecraft.currentScreen instanceof GuiIngameMenu)) return;
                clickVanillaButton(minecraft.currentScreen, "minetogether.connect.open");
                phaseTicks = ticks;
                chatPhase = 9;
                return;
            case 9:
                if (ticks - phaseTicks < 40 || !(minecraft.currentScreen instanceof GuiShareToFriends.Screen)) return;
                clickModularButton((ModularGuiScreen) minecraft.currentScreen, "minetogether.connect.open.start");
                chatPhase = 10;
                return;
            case 10:
                if (!ConnectHandler.isPublished()) return;
                marker("connect-host-republished");
                chatPhase = 11;
                return;
            case 11:
                if (!exists("connect-host-fault-request") || ConnectHandler.isPublished()) return;
                marker("connect-host-fault-observed");
                chatPhase = 12;
                return;
            case 12:
                if (!exists("connect-host-final-republish-request")) return;
                minecraft.displayGuiScreen(new GuiIngameMenu());
                phaseTicks = ticks;
                chatPhase = 13;
                return;
            case 13:
                if (ticks - phaseTicks < 20 || !(minecraft.currentScreen instanceof GuiIngameMenu)) return;
                clickVanillaButton(minecraft.currentScreen, "minetogether.connect.open");
                phaseTicks = ticks;
                chatPhase = 14;
                return;
            case 14:
                if (ticks - phaseTicks < 40 || !(minecraft.currentScreen instanceof GuiShareToFriends.Screen)) return;
                clickModularButton((ModularGuiScreen) minecraft.currentScreen, "minetogether.connect.open.start");
                chatPhase = 15;
                return;
            case 15:
                if (!ConnectHandler.isPublished()) return;
                marker("connect-host-final-republished");
                chatPhase = 16;
                return;
            case 16:
                if (exists("connect-host-stop-request")) success(minecraft);
                return;
            default:
        }
    }

    private static void tickConnectFriend(Minecraft minecraft) throws IOException, ReflectiveOperationException {
        if (chatPhase == 0) {
            if (minecraft.currentScreen == null || ticks < 40) return;
            minecraft.displayGuiScreen(new GuiMultiplayer(minecraft.currentScreen));
            chatPhase = 1;
            return;
        }
        if (chatPhase == 1) {
            if (!(minecraft.currentScreen instanceof GuiMultiplayer)) return;
            Map<?, ?> entries = friendServerEntries();
            if (entries.isEmpty()) return;
            FriendServerEntry entry = (FriendServerEntry) entries.values().iterator().next();
            RemoteServer remote = entry.getRemoteServer();
            if (!connectHostHash.equalsIgnoreCase(remote.getFriendHash())) {
                fail("Connect listing host hash differed: " + remote.getFriendHash());
                return;
            }
            if (!connectServerToken.equals(remote.getServerToken())) {
                fail("Connect listing token differed: " + remote.getServerToken());
                return;
            }
            marker("connect-friend-listing");
            minecraft.displayGuiScreen(remote.shouldWarnBeforeJoin()
                    ? new ConnectPackWarningScreen.Screen(minecraft.currentScreen, remote)
                    : new FriendConnectScreen(minecraft.currentScreen, remote));
            chatPhase = 2;
            phaseTicks = ticks;
            return;
        }
        if (chatPhase == 2 && minecraft.currentScreen instanceof ConnectPackWarningScreen.Screen) {
            if (ticks - phaseTicks < 20) return;
            clickModularButton((ModularGuiScreen) minecraft.currentScreen, "minetogether.connect.pack_warning.proceed");
            chatPhase = 3;
            return;
        }
        if (chatPhase == 2 && minecraft.currentScreen instanceof FriendConnectScreen) chatPhase = 3;
        if (chatPhase == 3 && minecraft.player != null && minecraft.world != null && minecraft.world.playerEntities.size() >= 2) {
            marker("connect-friend-joined");
            chatPhase = 4;
        }
        if (chatPhase == 4 && exists("connect-friend-release")) success(minecraft);
    }

    private static void tickConnectRejected(Minecraft minecraft, String expectedMessage) throws ReflectiveOperationException {
        if (!started) {
            if (minecraft.currentScreen == null || ticks < 40) return;
            minecraft.displayGuiScreen(new FriendConnectScreen(
                    minecraft.currentScreen,
                    new RemoteServer(connectHostHash, connectServerToken, "ci-local")
            ));
            started = true;
            return;
        }
        if (!(minecraft.currentScreen instanceof GuiDisconnected)) return;
        if (!screenContains(minecraft.currentScreen, expectedMessage)) {
            fail("Connect rejection screen did not contain: " + expectedMessage);
            return;
        }
        marker(role + "-rejected");
        success(minecraft);
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> friendServerEntries() throws ReflectiveOperationException {
        Field field = ServerListAppender.class.getDeclaredField("serverEntries");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(ServerListAppender.INSTANCE);
    }

    @SuppressWarnings("unchecked")
    private static void clickVanillaButton(GuiScreen screen, String translationKey) {
        String expected = I18n.format(translationKey);
        Iterable<GuiButton> buttons;
        try {
            Field field = GuiScreen.class.getDeclaredField("buttonList");
            field.setAccessible(true);
            buttons = (Iterable<GuiButton>) field.get(screen);
        } catch (Exception exception) {
            fail("Could not inspect vanilla buttons: " + exception);
            return;
        }
        for (GuiButton button : buttons) {
            if (expected.equals(button.displayString)) {
                button.playPressSound(Minecraft.getMinecraft().getSoundHandler());
                try {
                    java.lang.reflect.Method action = GuiScreen.class.getDeclaredMethod("actionPerformed", GuiButton.class);
                    action.setAccessible(true);
                    action.invoke(screen, button);
                } catch (Exception exception) {
                    fail("Could not press vanilla button " + translationKey + ": " + exception);
                }
                return;
            }
        }
        fail("Could not find vanilla button " + translationKey);
    }

    private static void clickModularButton(ModularGuiScreen screen, String translationKey) throws IOException {
        String expected = I18n.format(translationKey);
        net.creeperhost.minetogethercommunity.modulargui.GuiButton button = findModularButton(
                screen.getModularGui().getRoot().getChildren(), expected);
        if (button == null) {
            fail("Could not find modular button " + translationKey);
            return;
        }
        int x = button.x() + button.width() / 2;
        int y = button.y() + button.height() / 2;
        screen.getModularGui().mouseClicked(x, y, 0);
        screen.getModularGui().mouseReleased(x, y, 0);
    }

    private static net.creeperhost.minetogethercommunity.modulargui.GuiButton findModularButton(
            Iterable<GuiElement<?>> elements, String expected) {
        for (GuiElement<?> element : elements) {
            if (element instanceof net.creeperhost.minetogethercommunity.modulargui.GuiButton) {
                net.creeperhost.minetogethercommunity.modulargui.GuiButton button =
                        (net.creeperhost.minetogethercommunity.modulargui.GuiButton) element;
                if (expected.equals(button.getLabel())) return button;
            }
            net.creeperhost.minetogethercommunity.modulargui.GuiButton child = findModularButton(element.getChildren(), expected);
            if (child != null) return child;
        }
        return null;
    }

    private static boolean screenContains(GuiScreen screen, String expected) throws IllegalAccessException {
        Class<?> type = screen.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !ITextComponent.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                ITextComponent component = (ITextComponent) field.get(screen);
                if (component != null && component.getUnformattedText().contains(expected)) return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static String translated(String key) {
        String value = I18n.format(key);
        if (key.equals(value)) fail("Missing translation for " + key);
        return value;
    }

    private static void tickConnectUnavailable(Minecraft minecraft) {
        if (!started) {
            if (minecraft.currentScreen == null || ticks < 40) return;
            minecraft.displayGuiScreen(new GuiMultiplayer(minecraft.currentScreen));
            started = true;
            phaseTicks = ticks;
            marker("connect-unavailable-screen-open");
            return;
        }
        if (!(minecraft.currentScreen instanceof GuiMultiplayer)) {
            fail("Multiplayer screen closed after Connect discovery failed");
            return;
        }
        if (ConnectHandler.isEnabled() || ticks - phaseTicks < 240) return;
        int attempts = ConnectHandler.getTestSearchAttempts();
        if (attempts != 1) {
            fail("Connect discovery made " + attempts + " attempts during one backoff window");
            return;
        }
        marker("connect-unavailable-backoff");
        success(minecraft);
    }

    private static void tickConnect(Minecraft minecraft) {
        if (!started && stableTicks >= 60) {
            EmoteNetworking.tryBroadcastStart(TEST_EMOTE, false);
            started = true;
            phaseTicks = ticks;
            marker(role + "-emote-attempted");
        } else if (started && ticks - phaseTicks >= 60) {
            success(minecraft);
        }
    }

    private static void tickMixedSender(Minecraft minecraft) {
        if (!started && stableTicks >= 60 && EmoteNetworking.canSendToServer()) {
            EmoteNetworking.tryBroadcastStart(TEST_EMOTE, false);
            started = true;
            phaseTicks = ticks;
            marker("mixed-emote-start-sent");
        } else if (started && !stopped && ticks - phaseTicks >= 40) {
            EmoteNetworking.tryBroadcastStop();
            stopped = true;
            phaseTicks = ticks;
            marker("mixed-emote-stop-sent");
        } else if (stopped && ticks - phaseTicks >= 60) {
            success(minecraft);
        }
    }

    private static void tickSender(Minecraft minecraft) {
        if (!started && stableTicks >= 20 && exists("receiver-network-ready") && EmoteNetworking.canSendToServer()) {
            EmoteNetworking.tryBroadcastStart(TEST_EMOTE, true);
            started = true;
            marker("sender-emote-start-sent");
            System.out.println(PREFIX + "Sent persistent emote start");
        } else if (started && !stopped && exists("receiver-remote-start")) {
            EmoteNetworking.tryBroadcastStop();
            stopped = true;
            marker("sender-emote-stop-sent");
            System.out.println(PREFIX + "Sent emote stop");
        } else if (stopped && exists("receiver-remote-stop")) {
            success(minecraft);
        }
    }

    private static void tickReceiver(Minecraft minecraft) throws ReflectiveOperationException {
        installTestEmote();
        if (stableTicks >= 60 && EmoteNetworking.canSendToServer() && !exists("receiver-network-ready")) {
            marker("receiver-network-ready");
        }
        if (peerId == null) {
            for (EntityPlayer player : minecraft.world.playerEntities) {
                if (peerName.equals(player.getName())) {
                    peerId = player.getUniqueID();
                    marker("receiver-peer-found");
                    System.out.println(PREFIX + "Found peer " + peerName + " as " + peerId);
                    break;
                }
            }
        }
        if (peerId == null) return;
        if (!started && activeEmotes.containsKey(peerId)) {
            started = true;
            marker("receiver-remote-start");
            System.out.println(PREFIX + "Observed remote emote start for " + peerId);
        } else if (started && !stopped && !activeEmotes.containsKey(peerId)) {
            stopped = true;
            marker("receiver-remote-stop");
            System.out.println(PREFIX + "Observed remote emote stop for " + peerId);
        } else if (stopped && exists("sender-success")) {
            success(minecraft);
        }
    }

    private static void tickLateSender(Minecraft minecraft) throws ReflectiveOperationException {
        if (!started && stableTicks >= 60 && EmoteNetworking.canSendToServer()) {
            EmoteNetworking.tryBroadcastStart(TEST_EMOTE, true);
            started = true;
            marker("late-sender-emote-started");
            System.out.println(PREFIX + "Started persistent emote before the late peer joined");
        }

        findPeer(minecraft);
        if (!started || peerId == null) return;

        if (!stopped && activeEmotes.containsKey(peerId)) {
            stopped = true;
            marker("late-sender-observed-receiver");
            System.out.println(PREFIX + "Observed the late peer's persistent emote");
            return;
        }

        if (!stopped || !exists("late-receiver-disconnect-requested")) return;
        boolean peerPresent = minecraft.world.getPlayerEntityByUUID(peerId) != null;
        boolean staleEmote = activeEmotes.containsKey(peerId);
        if (peerPresent || staleEmote) {
            phaseTicks = 0;
            return;
        }

        if (++phaseTicks >= 40) {
            EmoteNetworking.tryBroadcastStop();
            marker("late-sender-disconnect-clean");
            success(minecraft);
        }
    }

    private static void tickLateReceiver(Minecraft minecraft) throws ReflectiveOperationException {
        findPeer(minecraft);
        if (peerId == null) return;

        if (!started && activeEmotes.containsKey(peerId)) {
            marker("late-receiver-observed-existing");
            EmoteNetworking.tryBroadcastStart(TEST_EMOTE, true);
            started = true;
            marker("late-receiver-emote-started");
            System.out.println(PREFIX + "Late peer received existing emote state and started its own emote");
        } else if (started && exists("late-sender-observed-receiver")) {
            marker("late-receiver-disconnect-requested");
            role = "";
            requestShutdown(minecraft);
            System.out.println(PREFIX + "Disconnected without sending an emote stop packet");
        }
    }

    private static void findPeer(Minecraft minecraft) {
        if (peerId != null) return;
        for (EntityPlayer player : minecraft.world.playerEntities) {
            if (peerName.equals(player.getName())) {
                peerId = player.getUniqueID();
                marker(role + "-peer-found");
                break;
            }
        }
    }

    private static void tickChatSender(Minecraft minecraft) throws ReflectiveOperationException {
        Object channel = primaryChatChannel();
        if (!started && chatReady(channel) && exists("chat-receiver-ready")) {
            channel.getClass().getMethod("sendMessage", String.class).invoke(channel, TEST_CHAT_MESSAGE);
            started = true;
            marker("chat-message-sent");
            System.out.println(PREFIX + "Sent local IRC message through MineTogether");
        } else if (started && !stopped && exists("chat-message-received")) {
            openPublicChat(minecraft);
            stopped = true;
            phaseTicks = ticks;
        } else if (stopped && ticks - phaseTicks >= 20) {
            assertChatScreen(minecraft);
            marker("chat-sender-ui-ready");
            if (exists("chat-receiver-ui-ready")) success(minecraft);
        }
    }

    private static void tickChatReceiver(Minecraft minecraft) throws ReflectiveOperationException {
        Object channel = primaryChatChannel();
        if (!started && chatReady(channel)) {
            started = true;
            marker("chat-receiver-ready");
            System.out.println(PREFIX + "Local MineTogether IRC channel is voiced and ready");
        }
        if (!started || channel == null) return;
        if (!stopped && receivedTestMessage(channel)) {
            marker("chat-message-received");
            openPublicChat(minecraft);
            stopped = true;
            phaseTicks = ticks;
            System.out.println(PREFIX + "Received local IRC message through MineTogether");
        } else if (stopped && ticks - phaseTicks >= 20) {
            assertChatScreen(minecraft);
            marker("chat-receiver-ui-ready");
            if (exists("chat-sender-ui-ready")) success(minecraft);
        }
    }

    private static void tickCtcpSender(Minecraft minecraft) throws ReflectiveOperationException {
        if (!preparePeerProfile(minecraft) || !chatReady(primaryChatChannel())) return;
        if (!started && stableTicks >= 60 && exists("ctcp-receiver-ready")) {
            if (EmoteNetworking.canSendToServer()) {
                fail("Vanilla server unexpectedly accepted MineTogether emote packets");
                return;
            }
            EmoteNetworking.tryBroadcastStart(TEST_EMOTE, true);
            started = true;
            marker("ctcp-emote-start-sent");
            System.out.println(PREFIX + "Sent emote start through MineTogether CTCP");
        } else if (started && !stopped && exists("ctcp-receiver-remote-start")) {
            EmoteNetworking.tryBroadcastStop();
            stopped = true;
            phaseTicks = ticks;
            marker("ctcp-emote-stop-sent");
            System.out.println(PREFIX + "Sent emote stop through MineTogether CTCP");
        // Let the legacy client finish its initial chunk stream before closing the vanilla socket.
        } else if (stopped && exists("ctcp-receiver-remote-stop") && ticks - phaseTicks >= 200) {
            success(minecraft);
        }
    }

    private static void tickCtcpReceiver(Minecraft minecraft) throws ReflectiveOperationException {
        installTestEmote();
        if (!preparePeerProfile(minecraft) || !chatReady(primaryChatChannel())) return;
        if (!exists("ctcp-receiver-ready")) marker("ctcp-receiver-ready");
        if (!started && activeEmotes.containsKey(peerId)) {
            started = true;
            marker("ctcp-receiver-remote-start");
            System.out.println(PREFIX + "Observed CTCP emote start for " + peerId);
        } else if (started && !stopped && !activeEmotes.containsKey(peerId)) {
            stopped = true;
            marker("ctcp-receiver-remote-stop");
            System.out.println(PREFIX + "Observed CTCP emote stop for " + peerId);
        } else if (stopped && exists("ctcp-sender-success")) {
            success(minecraft);
        }
    }

    private static boolean preparePeerProfile(Minecraft minecraft) throws ReflectiveOperationException {
        if (peerId == null) {
            for (EntityPlayer player : minecraft.world.playerEntities) {
                if (peerName.equals(player.getName())) {
                    peerId = player.getUniqueID();
                    break;
                }
            }
        }
        if (peerId == null) return false;

        String fullHash = profileHash(peerId);
        Field stateField = MineTogetherChat.class.getDeclaredField("CHAT_STATE");
        stateField.setAccessible(true);
        Object chatState = stateField.get(null);
        if (chatState == null) return false;
        Field managerField = chatState.getClass().getField("profileManager");
        Object profileManager = managerField.get(chatState);
        Object knownProfiles = profileManager.getClass().getMethod("getKnownProfiles").invoke(profileManager);
        if (!(knownProfiles instanceof Iterable)) return false;
        for (Object profile : (Iterable<?>) knownProfiles) {
            Object aliasesValue = profile.getClass().getMethod("getAliases").invoke(profile);
            if (!(aliasesValue instanceof Set)) continue;
            boolean matches = false;
            for (Object value : (Set<?>) aliasesValue) {
                String alias = String.valueOf(value);
                if (alias.startsWith("MT")) alias = alias.substring(2);
                if (fullHash.startsWith(alias)) {
                    matches = true;
                    break;
                }
            }
            boolean online = (Boolean) profile.getClass().getMethod("isOnline").invoke(profile);
            if (!online || !matches) continue;
            boolean hasFullHash = (Boolean) profile.getClass().getMethod("hasFullHash").invoke(profile);
            if (!hasFullHash) {
                Field field = profile.getClass().getDeclaredField("fullHash");
                field.setAccessible(true);
                field.set(profile, fullHash);
            }
            return true;
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private static String profileHash(UUID playerId) {
        return Hashing.sha256()
                .hashString(playerId.toString(), StandardCharsets.UTF_8)
                .toString()
                .toUpperCase(Locale.ROOT);
    }

    private static boolean receivedTestMessage(Object channel) throws ReflectiveOperationException {
        Object messages = channel.getClass().getMethod("getMessages").invoke(channel);
        if (!(messages instanceof Iterable)) return false;
        for (Object message : (Iterable<?>) messages) {
            Object component = message.getClass().getMethod("getMessage").invoke(message);
            if (TEST_CHAT_MESSAGE.equals(String.valueOf(component))) return true;
        }
        return false;
    }

    private static boolean chatReady(Object channel) throws ReflectiveOperationException {
        if (channel == null) return false;
        Object client = chatClient();
        Object state = client.getClass().getMethod("getState").invoke(client);
        return "CONNECTED".equals(String.valueOf(state));
    }

    private static Object primaryChatChannel() throws ReflectiveOperationException {
        Object client = chatClient();
        return client == null ? null : client.getClass().getMethod("getPrimaryChannel").invoke(client);
    }

    private static Object chatClient() throws ReflectiveOperationException {
        Field stateField = MineTogetherChat.class.getDeclaredField("CHAT_STATE");
        stateField.setAccessible(true);
        Object state = stateField.get(null);
        if (state == null) return null;
        Field clientField = state.getClass().getField("ircClient");
        return clientField.get(state);
    }

    private static void openPublicChat(Minecraft minecraft) {
        MineTogetherChat.setTarget(ChatTarget.PUBLIC);
        minecraft.displayGuiScreen(new GuiChat(""));
    }

    private static void assertChatScreen(Minecraft minecraft) {
        if (!(minecraft.currentScreen instanceof GuiChat)) {
            fail("MineTogether chat screen did not remain open during the local IRC test");
        }
    }

    private static void success(Minecraft minecraft) {
        marker(role + "-success");
        System.out.println(PREFIX + "PASS role=" + role);
        role = "";
        MineTogetherChat.disableChat();
        requestShutdown(minecraft);
    }

    private static void requestShutdown(Minecraft minecraft) {
        if (minecraft.world != null && !minecraft.isSingleplayer()) {
            minecraft.world.sendQuittingDisconnectingPacket();
            shutdownTicks = 20;
        } else {
            minecraft.shutdown();
        }
    }

    private static void fail(String message) {
        System.err.println(PREFIX + "FAIL " + message);
        marker("failure");
        try {
            Files.write(resultDirectory.resolve("failure.txt"),
                    (message + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
        role = "";
        System.exit(42);
    }

    private static boolean exists(String name) {
        return Files.isRegularFile(resultDirectory.resolve(name));
    }

    private static void marker(String name) {
        try {
            Files.createDirectories(resultDirectory);
            Files.write(resultDirectory.resolve(name), "ok\n".getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write CI marker " + name, exception);
        }
    }

    private static String setting(String property, String environment, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.trim().isEmpty()) value = System.getenv(environment);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static int integerSetting(String property, String environment, int fallback) {
        try {
            return Integer.parseInt(setting(property, environment, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, ?> activeEmotes() throws ReflectiveOperationException {
        Field field = EmotePlayer.class.getDeclaredField("ACTIVE");
        field.setAccessible(true);
        return (Map<UUID, ?>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static void installTestEmote() throws ReflectiveOperationException {
        Field field = CosmeticDownloader.class.getDeclaredField("loadedEmotes");
        field.setAccessible(true);
        Map<String, Emote> loaded = (Map<String, Emote>) field.get(CosmeticDownloader.instance());
        loaded.put(TEST_EMOTE, new Emote(
                TEST_EMOTE,
                "CI packet probe",
                "MineTogether CI",
                "",
                false,
                "",
                EmoteType.SIMPLE,
                true,
                true,
                false,
                0.0F,
                1.8F,
                EmoteAnimation.WAVE
        ));
    }

    private MineTogetherCiProbe() {
    }
}
