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
import net.creeperhost.polylib.event.events.client.PolyClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.Set;

/**
 * CI-only multiplayer probe. This class is packaged in a separate jar and is never included
 * in MineTogether release artifacts.
 */
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
            if (role.startsWith("chat-") || role.startsWith("ctcp-")) {
                if (chatPort <= 0) throw new IllegalStateException("Local chat test requires MINETOGETHER_CI_CHAT_PORT");
                CiChatMock.install(chatPort, role, resultDirectory);
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

        if (minecraft.player == null || minecraft.level == null || minecraft.getConnection() == null) {
            connectIfNeeded(minecraft);
            stableTicks = 0;
            return;
        }

        // The sender exits immediately after acknowledging the receiver's stop marker.
        // Complete before the shared player-count guard treats that expected teardown as a failure.
        if ("ctcp-receiver".equals(role) && stopped && exists("ctcp-sender-success")) {
            success(minecraft);
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

    private static void connectIfNeeded(Minecraft minecraft) {
        // HeadlessMC can make the title screen visible before Minecraft's initial
        // resource reload has finished. Joining during that window races model
        // initialization and can crash while the login packet is applied.
        if (connectionStarted || serverAddress.isEmpty() || minecraft.screen == null
                || minecraft.getOverlay() != null || ticks < 200) return;

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

    private static void tickChatSender(Minecraft minecraft) throws ReflectiveOperationException {
        Object channel = primaryChannel();
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
        Object channel = primaryChannel();
        if (!started && chatReady(channel)) {
            started = true;
            marker("chat-receiver-ready");
            System.out.println(PREFIX + "Local MineTogether IRC channel is voiced and ready");
        }
        if (!started || channel == null) return;
        if (!stopped && channelContains(channel, TEST_CHAT_MESSAGE)) {
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
        if (!preparePeerProfile(minecraft) || !chatReady(primaryChannel())) return;
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
        if (!preparePeerProfile(minecraft) || !chatReady(primaryChannel())) return;
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

    private static Object ircClient() throws ReflectiveOperationException {
        Object chatState = MineTogetherChat.class.getField("CHAT_STATE").get(null);
        return chatState.getClass().getField("ircClient").get(chatState);
    }

    private static Object primaryChannel() throws ReflectiveOperationException {
        Object client = ircClient();
        return client.getClass().getMethod("getPrimaryChannel").invoke(client);
    }

    private static boolean chatReady(Object channel) throws ReflectiveOperationException {
        if (channel == null || MineTogetherChat.publicChat == null) return false;
        Object state = ircClient().getClass().getMethod("getState").invoke(ircClient());
        return "CONNECTED".equals(state.toString());
    }

    private static boolean channelContains(Object channel, String expected) throws ReflectiveOperationException {
        Object messages = channel.getClass().getMethod("getMessages").invoke(channel);
        if (!(messages instanceof Iterable<?> iterable)) return false;
        for (Object message : iterable) {
            Object contents = message.getClass().getMethod("getMessage").invoke(message);
            if (expected.equals(contents.toString())) return true;
        }
        return false;
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
            success(minecraft);
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
