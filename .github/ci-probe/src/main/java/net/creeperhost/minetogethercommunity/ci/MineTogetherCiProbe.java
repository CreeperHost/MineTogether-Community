package net.creeperhost.minetogethercommunity.ci;

import com.google.common.hash.Hashing;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.emote.Emote;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteAnimation;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteNetworking;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteType;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.chat.ChatTarget;
import net.creeperhost.minetogethercommunity.connect.MineTogetherConnect;
import net.creeperhost.minetogethercommunity.connect.ConnectHandler;
import net.creeperhost.minetogethercommunity.connect.RemoteServer;
import net.creeperhost.minetogethercommunity.connect.gui.ConnectPackSelectionScreen;
import net.creeperhost.minetogethercommunity.connect.gui.ConnectPackWarningScreen;
import net.creeperhost.minetogethercommunity.connect.gui.FriendConnectScreen;
import net.creeperhost.minetogethercommunity.connect.gui.FriendServerEntry;
import net.creeperhost.minetogethercommunity.connect.gui.GuiShareToFriends;
import net.creeperhost.minetogethercommunity.connect.gui.ServerListAppender;
import net.creeperhost.minetogether.lib.chat.irc.IrcChannel;
import net.creeperhost.minetogether.lib.chat.irc.IrcState;
import net.creeperhost.polylib.client.modulargui.ModularGuiScreen;
import net.creeperhost.polylib.client.modulargui.elements.GuiButton;
import net.creeperhost.polylib.client.modulargui.elements.GuiElement;
import net.creeperhost.polylib.event.events.client.PolyClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.LanServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * CI-only multiplayer probe. This class is packaged in a separate jar and is never included
 * in MineTogether release artifacts.
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
        } catch (ReflectiveOperationException | IOException exception) {
            throw new IllegalStateException("Could not initialize MineTogether CI probe", exception);
        }

        System.out.println(PREFIX + "Started role=" + role + ", expectedPlayers=" + expectedPlayers);
        PolyClientTickEvents.CLIENT_TICK_END.register(MineTogetherCiProbe::tick);
    }

    private static void tick(Minecraft minecraft) {
        if (role == null || role.isEmpty()) return;
        if (++ticks > timeoutTicks) {
            fail("Timed out in role " + role + " after " + ticks + " ticks");
            return;
        }

        if (minecraft.screen instanceof ConnectPackSelectionScreen.Screen) {
            minecraft.screen.onClose();
            if (role.equals("singleplayer") || role.equals("connect-ui") || role.equals("connect-host")) {
                started = false;
                phaseTicks = ticks;
            }
            return;
        }

        if (role.equals("singleplayer") || role.equals("connect-ui") || role.equals("connect-host")) {
            try {
                tickSingleplayer(minecraft);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                fail("Role " + role + " failed: " + throwable);
            }
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
            try {
                tickConnectUnavailable(minecraft);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                fail("Role " + role + " failed: " + throwable);
            }
            return;
        }

        if (role.equals("ctcp-receiver") && stopped && exists("ctcp-sender-success")) {
            success(minecraft);
            return;
        }

        if (minecraft.player == null || minecraft.level == null || minecraft.getConnection() == null) {
            connectIfNeeded(minecraft);
            stableTicks = 0;
            return;
        }

        int players = minecraft.level.players().size();
        if (players < expectedPlayers) {
            if (joined) {
                fail("Player count dropped from " + expectedPlayers + " to " + players);
            }
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
            switch (role) {
                case "connect" -> tickConnect(minecraft);
                case "mixed-sender" -> tickMixedSender(minecraft);
                case "sender" -> tickSender(minecraft);
                case "receiver" -> tickReceiver(minecraft);
                case "late-sender" -> tickLateSender(minecraft);
                case "late-receiver" -> tickLateReceiver(minecraft);
                case "chat-sender" -> tickChatSender(minecraft);
                case "chat-receiver" -> tickChatReceiver(minecraft);
                case "ctcp-sender" -> tickCtcpSender(minecraft);
                case "ctcp-receiver" -> tickCtcpReceiver(minecraft);
                default -> fail("Unknown CI probe role: " + role);
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            fail("Role " + role + " failed: " + throwable);
        }
    }

    private static void tickSingleplayer(Minecraft minecraft) throws ReflectiveOperationException {
        if (!started) {
            if (minecraft.screen == null || ticks < 40) return;
            Screen parent = minecraft.screen;
            CreateWorldScreen.openFresh(minecraft, () -> minecraft.setScreen(parent));
            started = true;
            phaseTicks = ticks;
            marker("singleplayer-create-screen");
            System.out.println(PREFIX + "Opened the vanilla create-world screen");
            return;
        }

        if (!stopped) {
            if (minecraft.screen instanceof TitleScreen && ticks - phaseTicks > 100) {
                started = false;
                phaseTicks = ticks;
                return;
            }
            if (!(minecraft.screen instanceof CreateWorldScreen) || ticks - phaseTicks < 20) return;
            String createLabel = Component.translatable("selectWorld.create").getString();
            Button createButton = null;
            for (GuiEventListener child : minecraft.screen.children()) {
                if (child instanceof Button button && createLabel.equals(button.getMessage().getString())) {
                    createButton = button;
                    break;
                }
            }
            if (createButton == null) {
                if (ticks - phaseTicks > 200) fail("Could not find the vanilla create-world button");
                return;
            }
            createButton.onPress(new KeyEvent(257, 0, 0));
            stopped = true;
            stableTicks = 0;
            marker("singleplayer-create-submitted");
            System.out.println(PREFIX + "Submitted world creation through the vanilla screen");
            return;
        }

        if (minecraft.player == null || minecraft.level == null || !minecraft.hasSingleplayerServer()
                || minecraft.getSingleplayerServer() == null || !minecraft.getSingleplayerServer().isRunning()) {
            stableTicks = 0;
            return;
        }

        if (++stableTicks < 100) return;

        if (role.equals("singleplayer")) {
            marker("singleplayer-world-ready");
            success(minecraft);
            return;
        }

        if (role.equals("connect-host")) {
            tickConnectHost(minecraft);
        } else {
            tickConnectUi(minecraft);
        }
    }

    private static void tickConnectUi(Minecraft minecraft) {
        if (chatPhase == 0) {
            if (!MineTogetherConnect.isInitted) {
                fail("MineTogether Connect was not initialized in singleplayer");
                return;
            }
            marker("connect-ui-world-ready");
            minecraft.setScreen(new PauseScreen(true));
            phaseTicks = ticks;
            chatPhase = 1;
            return;
        }

        if (chatPhase == 1) {
            if (ticks - phaseTicks < 20 || !(minecraft.screen instanceof PauseScreen)) return;
            String openLabel = translated("minetogether.connect.open");
            Button openButton = minecraft.screen.children().stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .filter(button -> openLabel.equals(button.getMessage().getString()))
                    .findFirst()
                    .orElse(null);
            if (openButton == null) {
                fail("The singleplayer pause menu did not contain the Open to friends button");
                return;
            }
            marker("connect-ui-pause-control");
            openButton.onPress(new KeyEvent(257, 0, 0));
            phaseTicks = ticks;
            chatPhase = 2;
            return;
        }

        if (chatPhase == 2 && ticks - phaseTicks >= 40) {
            if (!(minecraft.screen instanceof GuiShareToFriends.Screen)) {
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

    private static void tickConnectHost(Minecraft minecraft) throws ReflectiveOperationException {
        switch (chatPhase) {
            case 0 -> {
                // The runtime clients intentionally use offline HMC accounts. Disable the
                // integrated server's session-server challenge before publishing it.
                minecraft.getSingleplayerServer().setUsesAuthentication(false);
                minecraft.setScreen(new PauseScreen(true));
                phaseTicks = ticks;
                chatPhase = 1;
            }
            case 1 -> {
                if (ticks - phaseTicks < 20 || !(minecraft.screen instanceof PauseScreen)) return;
                clickVanillaButton(minecraft.screen, "minetogether.connect.open");
                phaseTicks = ticks;
                chatPhase = 2;
            }
            case 2 -> {
                if (ticks - phaseTicks < 40 || !(minecraft.screen instanceof GuiShareToFriends.Screen screen)) return;
                clickModularButton(screen, "minetogether.connect.open.start");
                chatPhase = 3;
            }
            case 3 -> {
                if (!ConnectHandler.isPublished()) return;
                minecraft.getSingleplayerServer().setUsesAuthentication(false);
                marker("connect-host-published");
                System.out.println(PREFIX + "Published the integrated world through the real Connect UI");
                chatPhase = 4;
            }
            case 4 -> {
                if (exists("connect-friend-joined") && minecraft.getSingleplayerServer().getPlayerList().getPlayerCount() >= 2
                        && !exists("connect-host-friend-visible")) {
                    marker("connect-host-friend-visible");
                }
                if (!exists("connect-host-close-request")) return;
                minecraft.setScreen(new PauseScreen(true));
                phaseTicks = ticks;
                chatPhase = 5;
            }
            case 5 -> {
                if (ticks - phaseTicks < 20 || !(minecraft.screen instanceof PauseScreen)) return;
                clickVanillaButton(minecraft.screen, "minetogether.connect.close");
                chatPhase = 6;
            }
            case 6 -> {
                if (ConnectHandler.isPublished()) return;
                marker("connect-host-closed");
                chatPhase = 7;
            }
            case 7 -> {
                if (!exists("connect-host-republish-request")) return;
                minecraft.setScreen(new PauseScreen(true));
                phaseTicks = ticks;
                chatPhase = 8;
            }
            case 8 -> {
                if (ticks - phaseTicks < 20 || !(minecraft.screen instanceof PauseScreen)) return;
                clickVanillaButton(minecraft.screen, "minetogether.connect.open");
                phaseTicks = ticks;
                chatPhase = 9;
            }
            case 9 -> {
                if (ticks - phaseTicks < 40 || !(minecraft.screen instanceof GuiShareToFriends.Screen screen)) return;
                clickModularButton(screen, "minetogether.connect.open.start");
                chatPhase = 10;
            }
            case 10 -> {
                if (!ConnectHandler.isPublished()) return;
                marker("connect-host-republished");
                chatPhase = 11;
            }
            case 11 -> {
                if (!exists("connect-host-fault-request") || ConnectHandler.isPublished()) return;
                marker("connect-host-fault-observed");
                chatPhase = 12;
            }
            case 12 -> {
                if (!exists("connect-host-final-republish-request")) return;
                minecraft.setScreen(new PauseScreen(true));
                phaseTicks = ticks;
                chatPhase = 13;
            }
            case 13 -> {
                if (ticks - phaseTicks < 20 || !(minecraft.screen instanceof PauseScreen)) return;
                clickVanillaButton(minecraft.screen, "minetogether.connect.open");
                phaseTicks = ticks;
                chatPhase = 14;
            }
            case 14 -> {
                if (ticks - phaseTicks < 40 || !(minecraft.screen instanceof GuiShareToFriends.Screen screen)) return;
                clickModularButton(screen, "minetogether.connect.open.start");
                chatPhase = 15;
            }
            case 15 -> {
                if (!ConnectHandler.isPublished()) return;
                marker("connect-host-final-republished");
                chatPhase = 16;
            }
            case 16 -> {
                if (exists("connect-host-stop-request")) success(minecraft);
            }
        }
    }

    private static void tickConnectFriend(Minecraft minecraft) throws ReflectiveOperationException {
        if (chatPhase == 0) {
            if (minecraft.screen == null || ticks < 40) return;
            minecraft.setScreen(new JoinMultiplayerScreen(minecraft.screen));
            phaseTicks = ticks;
            chatPhase = 1;
            return;
        }

        if (chatPhase == 1) {
            if (minecraft.screen instanceof TitleScreen title) {
                minecraft.setScreen(new JoinMultiplayerScreen(title));
                return;
            }
            if (!(minecraft.screen instanceof JoinMultiplayerScreen multiplayer)) return;
            Map<?, ?> entries = friendServerEntries();
            if (entries.isEmpty()) return;
            FriendServerEntry entry = (FriendServerEntry) entries.values().iterator().next();
            if (!connectHostHash.equalsIgnoreCase(entry.remoteServer.friend)) {
                fail("Connect listing host hash differed: " + entry.remoteServer.friend);
                return;
            }
            if (!connectServerToken.equals(entry.remoteServer.serverToken)) {
                fail("Connect listing token differed: " + entry.remoteServer.serverToken);
                return;
            }
            marker("connect-friend-listing");
            entry.join();
            phaseTicks = ticks;
            chatPhase = 2;
            return;
        }

        if (chatPhase == 2) {
            if (minecraft.screen instanceof ConnectPackWarningScreen.Screen warning) {
                if (ticks - phaseTicks < 20) return;
                clickModularButton(warning, "minetogether.connect.pack_warning.proceed");
                chatPhase = 3;
                return;
            }
            if (minecraft.screen instanceof FriendConnectScreen) chatPhase = 3;
        }

        if (chatPhase == 3 && minecraft.player != null && minecraft.level != null
                && minecraft.level.players().size() >= 2) {
            marker("connect-friend-joined");
            System.out.println(PREFIX + "Friend discovered and joined the integrated world through Connect");
            chatPhase = 4;
        }

        if (chatPhase == 4 && exists("connect-friend-release")) success(minecraft);
    }

    private static void tickConnectRejected(Minecraft minecraft, String expectedMessage) throws ReflectiveOperationException {
        if (!started) {
            if (minecraft.screen == null || ticks < 40) return;
            RemoteServer remote = new RemoteServer(connectHostHash, connectServerToken, null);
            FriendConnectScreen.startConnecting(
                    minecraft.screen,
                    minecraft,
                    remote,
                    new LanServer("MineTogether CI", "127.0.0.1")
            );
            started = true;
            return;
        }

        if (!(minecraft.screen instanceof DisconnectedScreen)) return;
        if (!screenContains(minecraft.screen, expectedMessage)) {
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

    private static void clickVanillaButton(Screen screen, String translationKey) {
        String expected = Component.translatable(translationKey).getString();
        Button button = screen.children().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(candidate -> expected.equals(candidate.getMessage().getString()))
                .findFirst()
                .orElse(null);
        if (button == null) {
            fail("Could not find vanilla button " + translationKey);
            return;
        }
        button.onPress(new KeyEvent(257, 0, 0));
    }

    private static void clickModularButton(ModularGuiScreen screen, String translationKey) {
        String expected = Component.translatable(translationKey).getString();
        GuiButton button = findModularButton(screen.getModularGui().getRoot().getChildren(), expected);
        if (button == null) {
            fail("Could not find modular button " + translationKey);
            return;
        }
        double x = button.xCenter();
        double y = button.yCenter();
        screen.getModularGui().getRoot().updateMouseOver(x, y, false);
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    private static GuiButton findModularButton(Iterable<GuiElement<?>> elements, String expected) {
        for (GuiElement<?> element : elements) {
            if (element instanceof GuiButton button && button.getLabel() != null
                    && expected.equals(button.getLabel().getText().getString())) {
                return button;
            }
            GuiButton child = findModularButton(element.getChildren(), expected);
            if (child != null) return child;
        }
        return null;
    }

    private static boolean screenContains(Screen screen, String expected) throws IllegalAccessException {
        Class<?> type = screen.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object value = field.get(screen);
                if (value instanceof Component component && component.getString().contains(expected)) return true;
                if (value instanceof DisconnectionDetails details && details.reason().getString().contains(expected)) return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static String translated(String key) {
        String value = Component.translatable(key).getString();
        if (key.equals(value)) {
            fail("Missing translation for " + key);
        }
        return value;
    }

    private static void tickConnectUnavailable(Minecraft minecraft) {
        if (!started) {
            if (minecraft.screen == null || ticks < 40) return;
            minecraft.setScreen(new JoinMultiplayerScreen(minecraft.screen));
            started = true;
            phaseTicks = ticks;
            marker("connect-unavailable-screen-open");
            return;
        }

        if (!(minecraft.screen instanceof JoinMultiplayerScreen)) {
            fail("Multiplayer screen closed after Connect discovery failed");
            return;
        }
        if (ConnectHandler.isEnabled()) return;
        if (ticks - phaseTicks < 240) return;
        int attempts = ConnectHandler.getTestSearchAttempts();
        if (attempts != 1) {
            fail("Connect discovery made " + attempts + " attempts during one backoff window");
            return;
        }
        marker("connect-unavailable-backoff");
        success(minecraft);
    }

    private static void connectIfNeeded(Minecraft minecraft) {
        if (connectionStarted || serverAddress.isEmpty() || minecraft.screen == null || ticks < 200) return;

        connectionStarted = true;
        marker(role + "-connecting");
        System.out.println(PREFIX + "Connecting role=" + role + " to " + serverAddress);
        ConnectScreen.startConnecting(
                minecraft.screen,
                minecraft,
                ServerAddress.parseString(serverAddress),
                new ServerData("MineTogether CI", serverAddress, ServerData.Type.OTHER),
                true,
                null
        );
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

    private static void tickChatSender(Minecraft minecraft) {
        IrcChannel channel = MineTogetherChat.CHAT_STATE.ircClient.getPrimaryChannel();
        switch (chatPhase) {
            case 0 -> {
                if (!chatReady(channel) || !exists("chat-receiver-ready")) return;
                openPublicChat(minecraft);
                phaseTicks = ticks;
                chatPhase = 1;
            }
            case 1 -> {
                if (ticks - phaseTicks < 20) return;
                assertChatScreen(minecraft);
                assertMineTogetherChatControls(minecraft, true);
                marker("chat-controls-visible");
                minecraft.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                phaseTicks = ticks;
                chatPhase = 2;
            }
            case 2 -> {
                if (MineTogetherChat.isChatEnabled()
                        || MineTogetherChat.CHAT_STATE.ircClient.getState() != IrcState.DISCONNECTED) return;
                minecraft.setScreen(new ChatScreen("", false));
                phaseTicks = ticks;
                chatPhase = 3;
            }
            case 3 -> {
                if (ticks - phaseTicks < 20) return;
                assertChatScreen(minecraft);
                assertMineTogetherChatControls(minecraft, false);
                marker("chat-hidden-blocked");
                minecraft.options.chatVisibility().set(ChatVisiblity.FULL);
                phaseTicks = ticks;
                chatPhase = 4;
            }
            case 4 -> {
                if (!chatReady(channel) || !MineTogetherChat.isChatEnabled()) return;
                openPublicChat(minecraft);
                phaseTicks = ticks;
                chatPhase = 5;
            }
            case 5 -> {
                if (ticks - phaseTicks < 20) return;
                assertMineTogetherChatControls(minecraft, true);
                submitChatThroughUi(minecraft, TEST_CHAT_MESSAGE);
                marker("chat-message-sent");
                System.out.println(PREFIX + "Submitted local IRC message through the real chat input");
                chatPhase = 6;
            }
            case 6 -> {
                if (!exists("chat-message-received")) return;
                openPublicChat(minecraft);
                phaseTicks = ticks;
                chatPhase = 7;
            }
            case 7 -> {
                if (ticks - phaseTicks < 20) return;
                assertChatScreen(minecraft);
                marker("chat-sender-ui-ready");
                if (exists("chat-receiver-ui-ready")) success(minecraft);
            }
        }
    }

    private static void tickChatReceiver(Minecraft minecraft) {
        IrcChannel channel = MineTogetherChat.CHAT_STATE.ircClient.getPrimaryChannel();
        if (!started && chatReady(channel)) {
            started = true;
            marker("chat-receiver-ready");
            System.out.println(PREFIX + "Local MineTogether IRC channel is voiced and ready");
        }
        if (!started || channel == null) return;
        if (!stopped && channel.getMessages().stream().anyMatch(message -> TEST_CHAT_MESSAGE.equals(message.getMessage().toString()))) {
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
        if (!preparePeerProfile(minecraft)
                || !chatReady(MineTogetherChat.CHAT_STATE.ircClient.getPrimaryChannel())) return;
        if (!started && stableTicks >= 60 && exists("ctcp-receiver-ready")) {
            if (canSendEmoteToServer()) {
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
            marker("ctcp-emote-stop-sent");
            System.out.println(PREFIX + "Sent emote stop through MineTogether CTCP");
        } else if (stopped && exists("ctcp-receiver-remote-stop")) {
            success(minecraft);
        }
    }

    private static void tickCtcpReceiver(Minecraft minecraft) throws ReflectiveOperationException {
        installTestEmote();
        if (!preparePeerProfile(minecraft)
                || !chatReady(MineTogetherChat.CHAT_STATE.ircClient.getPrimaryChannel())) return;
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
            for (Player player : minecraft.level.players()) {
                if (peerName.equals(player.getName().getString())) {
                    peerId = player.getUUID();
                    break;
                }
            }
        }
        if (peerId == null) return false;

        String fullHash = profileHash(peerId);
        Object chatState = MineTogetherChat.class.getField("CHAT_STATE").get(null);
        Object profileManager = chatState.getClass().getField("profileManager").get(chatState);
        Object knownProfiles = profileManager.getClass().getMethod("getKnownProfiles").invoke(profileManager);
        if (!(knownProfiles instanceof Iterable<?> profiles)) return false;
        for (Object profile : profiles) {
            Object aliasesValue = profile.getClass().getMethod("getAliases").invoke(profile);
            if (!(aliasesValue instanceof Set<?> aliases)) continue;
            boolean matches = aliases.stream()
                    .map(Object::toString)
                    .map(alias -> alias.startsWith("MT") ? alias.substring(2) : alias)
                    .anyMatch(fullHash::startsWith);
            boolean online = (boolean) profile.getClass().getMethod("isOnline").invoke(profile);
            if (!online || !matches) continue;
            boolean hasFullHash = (boolean) profile.getClass().getMethod("hasFullHash").invoke(profile);
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

    private static boolean chatReady(IrcChannel channel) {
        return channel != null && MineTogetherChat.publicChat != null
                && MineTogetherChat.CHAT_STATE.ircClient.getState() == IrcState.CONNECTED;
    }

    private static void openPublicChat(Minecraft minecraft) {
        MineTogetherChat.setTarget(ChatTarget.PUBLIC);
        minecraft.setScreen(new ChatScreen("", false));
    }

    private static void assertChatScreen(Minecraft minecraft) {
        if (!(minecraft.screen instanceof ChatScreen)) {
            fail("MineTogether chat screen did not remain open during the local IRC test");
        }
    }

    private static void assertMineTogetherChatControls(Minecraft minecraft, boolean expected) {
        long radioButtons = minecraft.screen.children().stream()
                .filter(child -> child.getClass().getName().endsWith(".RadioButton"))
                .count();
        long sliders = minecraft.screen.children().stream()
                .filter(child -> child.getClass().getName().endsWith(".SlideButton"))
                .count();
        long iconButtons = minecraft.screen.children().stream()
                .filter(child -> child.getClass().getName().endsWith(".IconButton"))
                .count();
        boolean present = radioButtons >= 2 && sliders >= 3 && iconButtons >= 1;
        if (present != expected) {
            fail("MineTogether chat controls " + (expected ? "were missing" : "remained visible")
                    + " (radio=" + radioButtons + ", sliders=" + sliders + ", icons=" + iconButtons + ")");
        }
    }

    private static void submitChatThroughUi(Minecraft minecraft, String message) {
        if (!(minecraft.screen instanceof ChatScreen screen)) {
            fail("Cannot submit chat because the vanilla ChatScreen is not open");
            return;
        }
        EditBox input = screen.children().stream()
                .filter(EditBox.class::isInstance)
                .map(EditBox.class::cast)
                .findFirst()
                .orElse(null);
        if (input == null) {
            fail("The vanilla chat input was not present");
            return;
        }
        input.setValue(message);
        if (!screen.keyPressed(new KeyEvent(257, 0, 0))) { // GLFW_KEY_ENTER
            fail("The vanilla ChatScreen did not handle the Enter key");
        }
    }

    private static void tickMixedSender(Minecraft minecraft) throws ReflectiveOperationException {
        if (!started && stableTicks >= 60 && canSendEmoteToServer()) {
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

    private static void tickSender(Minecraft minecraft) throws ReflectiveOperationException {
        if (!started && stableTicks >= 20 && exists("receiver-network-ready") && canSendEmoteToServer()) {
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
            for (Player player : minecraft.level.players()) {
                if (peerName.equals(player.getName().getString())) {
                    peerId = player.getUUID();
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
        if (!started && stableTicks >= 60 && canSendEmoteToServer()) {
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
        boolean peerPresent = minecraft.level.getPlayerByUUID(peerId) != null;
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
            minecraft.getConnection().getConnection().disconnect(Component.literal("MineTogether CI disconnect cleanup"));
            role = "";
            System.out.println(PREFIX + "Disconnected without sending an emote stop packet");
        }
    }

    private static void findPeer(Minecraft minecraft) {
        if (peerId != null) return;
        for (Player player : minecraft.level.players()) {
            if (peerName.equals(player.getName().getString())) {
                peerId = player.getUUID();
                marker(role + "-peer-found");
                break;
            }
        }
    }

    private static void success(Minecraft minecraft) {
        marker(role + "-success");
        System.out.println(PREFIX + "PASS role=" + role);
        role = "";
        minecraft.stop();
    }

    private static void fail(String message) {
        System.err.println(PREFIX + "FAIL " + message);
        marker("failure");
        try {
            Files.writeString(resultDirectory.resolve("failure.txt"), message + System.lineSeparator(),
                    StandardCharsets.UTF_8);
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
            Files.writeString(resultDirectory.resolve(name), "ok" + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write CI marker " + name, exception);
        }
    }

    private static String setting(String property, String environment, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) value = System.getenv(environment);
        return value == null || value.isBlank() ? fallback : value;
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

    private static boolean canSendEmoteToServer() {
        return EmoteNetworking.canSendToServer();
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
                EmoteAnimation.DEFAULT
        ));
    }

    private MineTogetherCiProbe() {
    }
}
