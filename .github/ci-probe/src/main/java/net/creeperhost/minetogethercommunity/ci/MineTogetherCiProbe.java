package net.creeperhost.minetogethercommunity.ci;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.creeperhost.minetogethercommunity.chat.ChatTarget;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.emote.Emote;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteAnimation;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteNetworking;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/** CI-only Java 8 multiplayer probe, packaged separately from release artifacts. */
public final class MineTogetherCiProbe {

    private static final String PREFIX = "[MT-CI] ";
    private static final String TEST_EMOTE = "ci_packet_probe";
    private static final String TEST_CHAT_MESSAGE = "MT-CI-OFFLINE-CHAT-RELAY";
    private static final int DEFAULT_TIMEOUT_TICKS = 20 * 180;

    private static String role;
    private static String peerName;
    private static String serverAddress;
    private static int expectedPlayers;
    private static int timeoutTicks;
    private static Path resultDirectory;
    private static int chatPort;
    private static int ticks;
    private static int stableTicks;
    private static int phaseTicks;
    private static boolean joined;
    private static boolean connectionStarted;
    private static boolean chatInstalled;
    private static boolean started;
    private static boolean stopped;
    private static UUID peerId;
    private static Map<UUID, ?> activeEmotes;

    public static void init() {
        role = setting("minetogether.ci.role", "MINETOGETHER_CI_ROLE", "").trim();
        if (role.isEmpty()) return;
        peerName = setting("minetogether.ci.peerName", "MINETOGETHER_CI_PEER_NAME", "CiSender").trim();
        serverAddress = setting("minetogether.ci.serverAddress", "MINETOGETHER_CI_SERVER_ADDRESS", "").trim();
        expectedPlayers = integerSetting("minetogether.ci.expectedPlayers", "MINETOGETHER_CI_EXPECTED_PLAYERS", 1);
        timeoutTicks = integerSetting("minetogether.ci.timeoutTicks", "MINETOGETHER_CI_TIMEOUT_TICKS", DEFAULT_TIMEOUT_TICKS);
        resultDirectory = Paths.get(setting("minetogether.ci.results", "MINETOGETHER_CI_RESULTS", "build/ci-multiplayer/results")).toAbsolutePath().normalize();
        chatPort = integerSetting("minetogether.ci.chatPort", "MINETOGETHER_CI_CHAT_PORT", 0);
        try {
            Files.createDirectories(resultDirectory);
            activeEmotes = activeEmotes();
            installTestEmote();
            marker(role + "-started");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not initialize MineTogether CI probe", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize MineTogether CI probe", exception);
        }
        System.out.println(PREFIX + "Started role=" + role + ", expectedPlayers=" + expectedPlayers);
        FMLCommonHandler.instance().bus().register(new MineTogetherCiProbe());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || role == null || role.isEmpty()) return;
        tick(Minecraft.getMinecraft());
    }

    private static void tick(Minecraft minecraft) {
        if (++ticks > timeoutTicks) {
            fail("Timed out in role " + role + " after " + ticks + " ticks");
            return;
        }
        try {
            installChatIfNeeded();
            if (minecraft.thePlayer == null || minecraft.theWorld == null) {
                connectIfNeeded(minecraft);
                stableTicks = 0;
                return;
            }
            int players = ((World) minecraft.theWorld).playerEntities.size();
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
            if ("connect".equals(role)) tickConnect();
            else if ("mixed-sender".equals(role)) tickMixedSender();
            else if ("sender".equals(role)) tickSender();
            else if ("receiver".equals(role)) tickReceiver(minecraft);
            else if ("chat-sender".equals(role)) tickChatSender(minecraft);
            else if ("chat-receiver".equals(role)) tickChatReceiver(minecraft);
            else fail("Unknown CI probe role: " + role);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            fail("Role " + role + " failed: " + throwable);
        }
    }

    private static void installChatIfNeeded() {
        if (chatInstalled || !("chat-sender".equals(role) || "chat-receiver".equals(role))) return;
        if (chatPort <= 0) throw new IllegalStateException("Local chat test requires MINETOGETHER_CI_CHAT_PORT");
        CiChatMock.install(chatPort, role, resultDirectory);
        chatInstalled = true;
    }

    private static void connectIfNeeded(Minecraft minecraft) {
        if (connectionStarted || serverAddress.isEmpty() || minecraft.currentScreen == null || ticks < 40) return;
        int separator = serverAddress.lastIndexOf(':');
        if (separator <= 0 || separator == serverAddress.length() - 1) throw new IllegalArgumentException("Expected host:port server address, got " + serverAddress);
        String host = serverAddress.substring(0, separator);
        int port = Integer.parseInt(serverAddress.substring(separator + 1));
        connectionStarted = true;
        marker(role + "-connecting");
        System.out.println(PREFIX + "Connecting role=" + role + " to " + serverAddress);
        FMLClientHandler.instance().connectToServerAtStartup(host, port);
    }

    private static void tickConnect() {
        if (!started && stableTicks >= 60) {
            EmoteNetworking.tryBroadcastStart(TEST_EMOTE, false);
            started = true;
            phaseTicks = ticks;
            marker(role + "-emote-attempted");
        } else if (started && ticks - phaseTicks >= 60) success();
    }

    private static void tickMixedSender() {
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
        } else if (stopped && ticks - phaseTicks >= 60) success();
    }

    private static void tickSender() {
        if (!started && stableTicks >= 20 && exists("receiver-network-ready")) {
            EmoteNetworking.tryBroadcastStart(TEST_EMOTE, true);
            started = true;
            marker("sender-emote-start-sent");
            System.out.println(PREFIX + "Sent persistent emote start");
        } else if (started && !stopped && exists("receiver-remote-start")) {
            EmoteNetworking.tryBroadcastStop();
            stopped = true;
            marker("sender-emote-stop-sent");
            System.out.println(PREFIX + "Sent emote stop");
        } else if (stopped && exists("receiver-remote-stop")) success();
    }

    private static void tickReceiver(Minecraft minecraft) throws ReflectiveOperationException {
        installTestEmote();
        if (stableTicks >= 60 && !exists("receiver-network-ready")) marker("receiver-network-ready");
        if (peerId == null) {
            for (Object playerObject : ((World) minecraft.theWorld).playerEntities) {
                EntityPlayer player = (EntityPlayer) playerObject;
                if (peerName.equals(player.getCommandSenderName())) {
                    peerId = ((Entity) player).getUniqueID();
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
        } else if (stopped && exists("sender-success")) success();
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
            if (exists("chat-receiver-ui-ready")) success();
        }
    }

    private static void tickChatReceiver(Minecraft minecraft) throws ReflectiveOperationException {
        Object channel = primaryChannel();
        if (!started && chatReady(channel)) {
            started = true;
            marker("chat-receiver-ready");
            System.out.println(PREFIX + "Local MineTogether IRC channel is ready");
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
            if (exists("chat-sender-ui-ready")) success();
        }
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
        Object client = ircClient();
        Object state = client.getClass().getMethod("getState").invoke(client);
        return channel != null && "CONNECTED".equals(state.toString());
    }

    private static boolean channelContains(Object channel, String expected) throws ReflectiveOperationException {
        Object messages = channel.getClass().getMethod("getMessages").invoke(channel);
        if (!(messages instanceof Iterable)) return false;
        for (Object message : (Iterable<?>) messages) {
            Method getter = message.getClass().getMethod("getMessage");
            Object contents = getter.invoke(message);
            if (contents != null && expected.equals(contents.toString())) return true;
        }
        return false;
    }

    private static void openPublicChat(Minecraft minecraft) {
        MineTogetherChat.setTarget(ChatTarget.PUBLIC);
        minecraft.displayGuiScreen(new GuiChat(""));
    }

    private static void assertChatScreen(Minecraft minecraft) {
        if (!(minecraft.currentScreen instanceof GuiChat)) fail("MineTogether chat screen did not remain open during the local IRC test");
    }

    private static void success() {
        marker(role + "-success");
        System.out.println(PREFIX + "PASS role=" + role);
        role = "";
        Minecraft.getMinecraft().shutdown();
    }

    private static void fail(String message) {
        System.err.println(PREFIX + "FAIL " + message);
        marker("failure");
        try {
            Files.write(resultDirectory.resolve("failure.txt"), Collections.singletonList(message), StandardCharsets.UTF_8);
        } catch (IOException ignored) { }
        role = "";
        Minecraft.getMinecraft().shutdown();
    }

    private static boolean exists(String name) { return Files.isRegularFile(resultDirectory.resolve(name)); }

    private static void marker(String name) {
        try {
            Files.createDirectories(resultDirectory);
            Files.write(resultDirectory.resolve(name), Collections.singletonList("ok"), StandardCharsets.UTF_8);
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
        try { return Integer.parseInt(setting(property, environment, Integer.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
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
        loaded.put(TEST_EMOTE, new Emote(TEST_EMOTE, "CI packet probe", "MineTogether CI", "", false, "",
                EmoteType.SIMPLE, true, true, false, 0.0F, 1.8F, EmoteAnimation.WAVE));
    }

    private MineTogetherCiProbe() { }
}
