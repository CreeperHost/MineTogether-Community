package net.creeperhost.minetogethercommunity.ci;

import net.creeperhost.minetogethercommunity.chat.ChatTarget;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.emote.Emote;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteAnimation;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteNetworking;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteType;
import net.creeperhost.minetogethercommunity.gui.chat.PublicChatGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.entity.player.EntityPlayer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/** CI-only multiplayer probe, packaged separately from the release jar. */
public final class MineTogetherCiProbe {

    private static final String PREFIX = "[MT-CI] ";
    private static final String TEST_EMOTE = "ci_packet_probe";
    private static final String TEST_CHAT_MESSAGE = "MT-CI-OFFLINE-CHAT-RELAY";
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

        try {
            Files.createDirectories(resultDirectory);
            if ("chat-sender".equals(role) || "chat-receiver".equals(role)) {
                if (chatPort <= 0) throw new IllegalStateException("Local chat test requires MINETOGETHER_CI_CHAT_PORT");
                CiChatMock.install(chatPort, role, resultDirectory);
            }
            activeEmotes = activeEmotes();
            installTestEmote();
            marker(role + "-started");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not initialize MineTogether CI probe", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize MineTogether CI probe", exception);
        }

        System.out.println(PREFIX + "Started role=" + role + ", expectedPlayers=" + expectedPlayers);
    }

    public static void tick(Minecraft minecraft) {
        if (role == null || role.isEmpty()) return;
        if (++ticks > timeoutTicks) {
            fail("Timed out in role " + role + " after " + ticks + " ticks");
            return;
        }

        if (minecraft.thePlayer == null || minecraft.theWorld == null || minecraft.getNetHandler() == null) {
            connectIfNeeded(minecraft);
            stableTicks = 0;
            return;
        }

        int players = minecraft.theWorld.playerEntities.size();
        if (players < expectedPlayers) {
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
            if ("connect".equals(role)) tickConnect(minecraft);
            else if ("mixed-sender".equals(role)) tickMixedSender(minecraft);
            else if ("sender".equals(role)) tickSender(minecraft);
            else if ("receiver".equals(role)) tickReceiver(minecraft);
            else if ("chat-sender".equals(role)) tickChatSender(minecraft);
            else if ("chat-receiver".equals(role)) tickChatReceiver(minecraft);
            else fail("Unknown CI probe role: " + role);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            fail("Role " + role + " failed: " + throwable);
        }
    }

    private static void connectIfNeeded(Minecraft minecraft) {
        if (connectionStarted || serverAddress.isEmpty() || minecraft.currentScreen == null || ticks < 200) return;
        connectionStarted = true;
        marker(role + "-connecting");
        System.out.println(PREFIX + "Connecting role=" + role + " to " + serverAddress);
        minecraft.displayGuiScreen(new GuiConnecting(
                minecraft.currentScreen,
                minecraft,
                new ServerData("MineTogether CI", serverAddress, false)
        ));
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
        if (!started && stableTicks >= 60) {
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
        if (!started && stableTicks >= 20 && exists("receiver-network-ready")) {
            EmoteNetworking.tryBroadcastStart(TEST_EMOTE, true);
            started = true;
            marker("sender-emote-start-sent");
        } else if (started && !stopped && exists("receiver-remote-start")) {
            EmoteNetworking.tryBroadcastStop();
            stopped = true;
            marker("sender-emote-stop-sent");
        } else if (stopped && exists("receiver-remote-stop")) {
            success(minecraft);
        }
    }

    private static void tickReceiver(Minecraft minecraft) throws ReflectiveOperationException {
        installTestEmote();
        if (stableTicks >= 60 && !exists("receiver-network-ready")) marker("receiver-network-ready");
        if (peerId == null) {
            for (Object playerObject : minecraft.theWorld.playerEntities) {
                EntityPlayer player = (EntityPlayer) playerObject;
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
        } else if (started && !stopped && !activeEmotes.containsKey(peerId)) {
            stopped = true;
            marker("receiver-remote-stop");
        } else if (stopped && exists("sender-success")) {
            success(minecraft);
        }
    }

    private static void tickChatSender(Minecraft minecraft) throws ReflectiveOperationException {
        Object channel = primaryChannel();
        if (!started && chatReady(channel) && exists("chat-receiver-ready")) {
            channel.getClass().getMethod("sendMessage", String.class).invoke(channel, TEST_CHAT_MESSAGE);
            started = true;
            marker("chat-message-sent");
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
        Object channel = primaryChannel();
        if (!started && chatReady(channel)) {
            started = true;
            marker("chat-receiver-ready");
        }
        if (!started || channel == null) return;
        if (!stopped && containsMessage(channel, TEST_CHAT_MESSAGE)) {
            marker("chat-message-received");
            openPublicChat(minecraft);
            stopped = true;
            phaseTicks = ticks;
        } else if (stopped && ticks - phaseTicks >= 20) {
            assertChatScreen(minecraft);
            marker("chat-receiver-ui-ready");
            if (exists("chat-sender-ui-ready")) success(minecraft);
        }
    }

    private static boolean containsMessage(Object channel, String expected) throws ReflectiveOperationException {
        Object messages = channel.getClass().getMethod("getMessages").invoke(channel);
        if (!(messages instanceof Iterable)) return false;
        for (Object message : (Iterable<?>) messages) {
            Object contents = message.getClass().getMethod("getMessage").invoke(message);
            if (expected.equals(contents.toString())) return true;
        }
        return false;
    }

    private static Object ircClient() throws ReflectiveOperationException {
        Object chatState = MineTogetherChat.class.getField("CHAT_STATE").get(null);
        return chatState.getClass().getField("ircClient").get(chatState);
    }

    private static Object primaryChannel() throws ReflectiveOperationException {
        Object client = ircClient();
        return client.getClass().getMethod("getPrimaryChannel").invoke(client);
    }

    private static boolean chatReady(Object channel) throws ReflectiveOperationException {
        if (channel == null) return false;
        Object client = ircClient();
        Object state = client.getClass().getMethod("getState").invoke(client);
        return "CONNECTED".equals(state.toString());
    }

    private static void openPublicChat(Minecraft minecraft) {
        MineTogetherChat.setTarget(ChatTarget.PUBLIC);
        minecraft.displayGuiScreen(new PublicChatGui.Screen(minecraft.currentScreen));
    }

    private static void assertChatScreen(Minecraft minecraft) {
        if (!(minecraft.currentScreen instanceof PublicChatGui.Screen)) {
            fail("MineTogether public chat screen did not remain open during the local IRC test");
        }
    }

    private static void success(Minecraft minecraft) {
        marker(role + "-success");
        System.out.println(PREFIX + "PASS role=" + role);
        role = "";
        minecraft.shutdown();
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
                TEST_EMOTE, "CI packet probe", "MineTogether CI", "", false, "",
                EmoteType.SIMPLE, true, true, false, 0.0F, 1.8F, EmoteAnimation.WAVE
        ));
    }

    private MineTogetherCiProbe() {
    }
}
