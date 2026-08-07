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
import net.creeperhost.minetogethercommunity.gui.chat.PublicChatGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
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
            if (role.startsWith("chat-") || role.startsWith("ctcp-")) {
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

        if ("singleplayer".equals(role)) {
            try {
                tickSingleplayer(minecraft);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                fail("Role " + role + " failed: " + throwable);
            }
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
            else if ("late-sender".equals(role)) tickLateSender(minecraft);
            else if ("late-receiver".equals(role)) tickLateReceiver(minecraft);
            else if ("chat-sender".equals(role)) tickChatSender(minecraft);
            else if ("chat-receiver".equals(role)) tickChatReceiver(minecraft);
            else if ("ctcp-sender".equals(role)) tickCtcpSender(minecraft);
            else if ("ctcp-receiver".equals(role)) tickCtcpReceiver(minecraft);
            else fail("Unknown CI probe role: " + role);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            fail("Role " + role + " failed: " + throwable);
        }
    }

    private static void tickSingleplayer(Minecraft minecraft) throws IOException {
        if (!started) {
            if (minecraft.currentScreen == null || ticks < 200) return;
            minecraft.displayGuiScreen(new CiCreateWorldScreen(minecraft.currentScreen));
            started = true;
            phaseTicks = ticks;
            marker("singleplayer-create-screen");
            System.out.println(PREFIX + "Opened the vanilla create-world screen");
            return;
        }

        if (!stopped) {
            if (!(minecraft.currentScreen instanceof CiCreateWorldScreen) || ticks - phaseTicks < 20) return;
            if (!((CiCreateWorldScreen) minecraft.currentScreen).submitCreate()) {
                if (ticks - phaseTicks > 200) fail("Could not find the vanilla create-world button");
                return;
            }
            stopped = true;
            stableTicks = 0;
            marker("singleplayer-create-submitted");
            System.out.println(PREFIX + "Submitted world creation through the vanilla screen");
            return;
        }

        if (minecraft.thePlayer == null || minecraft.theWorld == null
                || !minecraft.isIntegratedServerRunning() || minecraft.getIntegratedServer() == null
                || !minecraft.getIntegratedServer().isServerRunning()) {
            stableTicks = 0;
            return;
        }

        if (++stableTicks >= 100) {
            marker("singleplayer-world-ready");
            success(minecraft);
        }
    }

    private static final class CiCreateWorldScreen extends GuiCreateWorld {
        private CiCreateWorldScreen(GuiScreen parent) {
            super(parent);
        }

        private boolean submitCreate() throws IOException {
            String createLabel = I18n.format("selectWorld.create");
            for (GuiButton button : buttonList) {
                if (createLabel.equals(button.displayString)) {
                    actionPerformed(button);
                    return true;
                }
            }
            return false;
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

    private static void tickLateSender(Minecraft minecraft) throws ReflectiveOperationException {
        if (!started && stableTicks >= 60) {
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
        boolean peerPresent = minecraft.theWorld.getPlayerEntityByUUID(peerId) != null;
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
            minecraft.getNetHandler().getNetworkManager().closeChannel(
                    new ChatComponentText("MineTogether CI disconnect cleanup")
            );
            role = "";
            System.out.println(PREFIX + "Disconnected without sending an emote stop packet");
        }
    }

    private static void findPeer(Minecraft minecraft) {
        if (peerId != null) return;
        for (Object playerObject : minecraft.theWorld.playerEntities) {
            EntityPlayer player = (EntityPlayer) playerObject;
            if (peerName.equals(player.getName())) {
                peerId = player.getUniqueID();
                marker(role + "-peer-found");
                break;
            }
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
            for (Object playerObject : minecraft.theWorld.playerEntities) {
                EntityPlayer player = (EntityPlayer) playerObject;
                if (peerName.equals(player.getName())) {
                    peerId = player.getUniqueID();
                    break;
                }
            }
        }
        if (peerId == null) return false;

        String fullHash = profileHash(peerId);
        Object chatState = MineTogetherChat.class.getField("CHAT_STATE").get(null);
        Object profileManager = chatState.getClass().getField("profileManager").get(chatState);
        Object knownProfiles = profileManager.getClass().getMethod("getKnownProfiles").invoke(profileManager);
        if (!(knownProfiles instanceof Iterable)) return false;
        for (Object profile : (Iterable<?>) knownProfiles) {
            Object aliasesValue = profile.getClass().getMethod("getAliases").invoke(profile);
            if (!(aliasesValue instanceof Set)) continue;
            boolean matches = false;
            for (Object aliasValue : (Set<?>) aliasesValue) {
                String alias = aliasValue.toString();
                if (alias.startsWith("MT")) alias = alias.substring(2);
                if (fullHash.startsWith(alias)) {
                    matches = true;
                    break;
                }
            }
            boolean online = ((Boolean) profile.getClass().getMethod("isOnline").invoke(profile)).booleanValue();
            if (!online || !matches) continue;
            boolean hasFullHash = ((Boolean) profile.getClass().getMethod("hasFullHash").invoke(profile)).booleanValue();
            if (!hasFullHash) {
                Field field = profile.getClass().getDeclaredField("fullHash");
                field.setAccessible(true);
                field.set(profile, fullHash);
            }
            return true;
        }
        return false;
    }

    private static boolean canSendEmoteToServer() throws ReflectiveOperationException {
        Method method = EmoteNetworking.class.getDeclaredMethod("canSendToServer");
        method.setAccessible(true);
        return ((Boolean) method.invoke(null)).booleanValue();
    }

    @SuppressWarnings("deprecation")
    private static String profileHash(UUID playerId) {
        return Hashing.sha256().hashString(playerId.toString(), StandardCharsets.UTF_8).toString().toUpperCase(Locale.ROOT);
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
