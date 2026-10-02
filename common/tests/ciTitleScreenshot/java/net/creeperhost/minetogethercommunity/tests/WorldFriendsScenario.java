package net.creeperhost.minetogethercommunity.tests;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.chat.gui.FriendChatGui;
import net.creeperhost.minetogethercommunity.chat.gui.PublicChatGui;
import net.creeperhost.minetogethercommunity.chat.gui.MessageElement;
import net.creeperhost.polylib.client.modulargui.elements.GuiTextField;
import net.creeperhost.minetogethercommunity.polylib.gui.IconButton;
import net.creeperhost.polylib.client.modulargui.elements.GuiElement;
import net.creeperhost.polylib.client.modulargui.elements.GuiList;
import net.creeperhost.polylib.client.modulargui.elements.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Exercises vanilla world creation and the real pause-menu friends entry point. */
final class WorldFriendsScenario {
    private enum Phase { CREATE, SUBMIT, WORLD, PAUSE, FRIENDS, CAPTURE_FRIENDS, CHAT_MENU, CHAT, COMPLETE }
    record Outcome(List<TestResults.Check> checks, List<Path> screenshots, String details) {}

    private final Path output;
    private final Consumer<Outcome> completion;
    private Phase phase = Phase.CREATE;
    private long phaseSince = System.nanoTime();
    private long stableSince;
    private boolean worldLoaded;
    private boolean friendsOpened;
    private boolean friendsVisible;
    private int friendCount;
    private boolean friendsCaptured;
    private boolean chatOpened;
    private boolean messagesVisible;
    private int visibleMessages;
    private final List<Path> screenshots = new ArrayList<>();

    WorldFriendsScenario(Path output, Consumer<Outcome> completion) {
        this.output = output;
        this.completion = completion;
    }

    void afterFrame(Minecraft minecraft) {
        if (phase == Phase.COMPLETE) return;
        long now = System.nanoTime();
        if (now - phaseSince > TimeUnit.SECONDS.toNanos(phase == Phase.WORLD || phase == Phase.SUBMIT || phase == Phase.CHAT ? 180 : 90)) {
            finish(minecraft, false, "Client UI test timed out during " + phase + "."
                    + (phase == Phase.CHAT ? " No visible message from another user was observed. No chat message was sent by the test." : "")
                    + (friendsOpened && friendCount == 0 ? " No friends were returned for this account; this check requires an account with friends." : ""));
            return;
        }
        try {
            switch (phase) {
                case CREATE -> {
                    var parent = minecraft.gui.screen();
                    advance(Phase.SUBMIT);
                    CreateWorldScreen.openFresh(minecraft, () -> minecraft.gui.setScreen(parent));
                }
                case SUBMIT -> {
                    if (!(minecraft.gui.screen() instanceof CreateWorldScreen screen)) return;
                    if (now - phaseSince < TimeUnit.SECONDS.toNanos(2)) return;
                    screen.getUiState().setName("MineTogether CI");
                    for (var child : screen.children()) {
                        if (child instanceof Button button && button.active && button.visible
                                && button.getMessage().getString().equals(Component.translatable("selectWorld.create").getString())) {
                            advance(Phase.WORLD);
                            press(button);
                            return;
                        }
                    }
                }
                case WORLD -> {
                    if (minecraft.player == null || minecraft.level == null || minecraft.gui.screen() != null
                            || minecraft.gui.overlay() != null || minecraft.getSingleplayerServer() == null
                            || !minecraft.getSingleplayerServer().isRunning()) {
                        stableSince = 0;
                        return;
                    }
                    if (stableSince == 0) stableSince = now;
                    if (now - stableSince < TimeUnit.SECONDS.toNanos(5)) return;
                    worldLoaded = true;
                    minecraft.gui.setScreen(new PauseScreen(true));
                    advance(Phase.PAUSE);
                }
                case PAUSE -> {
                    if (!(minecraft.gui.screen() instanceof PauseScreen screen)) return;
                    for (var child : screen.children()) {
                        if (child instanceof IconButton button && button.active && button.visible
                                && (int) field(IconButton.class, "index", button) == 7) {
                            advance(Phase.FRIENDS);
                            press(button);
                            return;
                        }
                    }
                }
                case FRIENDS -> {
                    if (!(minecraft.gui.screen() instanceof FriendChatGui.Screen screen)) return;
                    friendsOpened = true;
                    var chat = MineTogetherChat.CHAT_STATE;
                    if (chat == null) return;
                    var expected = new HashSet<Profile>();
                    chat.profileManager.getKnownProfiles().stream().filter(Profile::isFriend).forEach(expected::add);
                    friendCount = expected.size();
                    var gui = screen.getModularGui();
                    var list = (GuiList<?>) field(FriendChatGui.class, "friendList", gui.getProvider());
                    var actual = new HashSet<Profile>();
                    boolean namedRowVisible = false;
                    for (Object entry : list.getList()) {
                        var row = (GuiElement<?>) entry;
                        Object profile = field(row.getClass(), "profile", row);
                        // Requests and divider rows do not count as accepted friends.
                        if (!(profile instanceof Profile friend) || !friend.isFriend()
                                || field(row.getClass(), "request", row) != null) continue;
                        actual.add(friend);
                        if (row.isEnabled() && row.ySize() > 0 && row.yMin() < list.yMax()
                                && row.yMax() > list.yMin() && row.xMin() < list.xMax() && row.xMax() > list.xMin()) {
                            namedRowVisible |= row.getChildren().stream().anyMatch(child -> child instanceof GuiText text
                                    && text.isEnabled() && !text.getText().getString().isBlank()
                                    && text.getText().getString().equals(FriendChatGui.displayName(friend)));
                        }
                    }
                    friendsVisible = !expected.isEmpty() && actual.equals(expected) && namedRowVisible;
                    if (!friendsVisible) {
                        stableSince = 0;
                        return;
                    }
                    if (stableSince == 0) stableSince = now;
                    if (now - stableSince >= TimeUnit.SECONDS.toNanos(3)) {
                        advance(Phase.CAPTURE_FRIENDS);
                        capture(minecraft, output.resolve("friends-screen.png"), saved -> {
                            friendsCaptured = saved;
                            if (!saved) {
                                finish(minecraft, false, "Could not capture the friends screen.");
                                return;
                            }
                            minecraft.gui.setScreen(new PauseScreen(true));
                            advance(Phase.CHAT_MENU);
                        });
                    }
                }
                case CHAT_MENU -> {
                    if (!(minecraft.gui.screen() instanceof PauseScreen screen)) return;
                    for (var child : screen.children()) {
                        if (child instanceof IconButton button && button.active && button.visible
                                && (int) field(IconButton.class, "index", button) == 1) {
                            advance(Phase.CHAT);
                            // Invoke only the navigation button; never dispatch keys to the chat screen.
                            press(button);
                            return;
                        }
                    }
                }
                case CHAT -> {
                    if (!(minecraft.gui.screen() instanceof PublicChatGui.Screen screen)
                            || !(screen.getModularGui().getProvider() instanceof PublicChatGui provider)) return;
                    chatOpened = true;
                    GuiTextField input = (GuiTextField) field(PublicChatGui.class, "textField", provider);
                    if (!input.getValue().isEmpty()) {
                        finish(minecraft, false, "Chat input was unexpectedly nonempty. Stopped without submitting it.");
                        return;
                    }
                    var channel = provider.chatMonitor.getChannel();
                    visibleMessages = channel == null ? 0 : countVisibleMessages(screen.getModularGui().getRoot(), channel.getMessages());
                    messagesVisible = visibleMessages > 0;
                    if (!messagesVisible) {
                        stableSince = 0;
                        return;
                    }
                    if (stableSince == 0) stableSince = now;
                    if (now - stableSince >= TimeUnit.SECONDS.toNanos(3)) {
                        finish(minecraft, true, "Created and entered a single-player world. Friends screen displays " + friendCount
                                + " friends. Public chat displays " + visibleMessages + " received messages. Read-only check; no messages sent.");
                    }
                }
                default -> { }
            }
        } catch (Exception error) {
            LogUtils.getLogger().error("World/friends test failed.", error);
            finish(minecraft, false, "World/friends test failed during " + phase + ": " + error.getClass().getSimpleName() + ".");
        }
    }

    private void advance(Phase next) {
        phase = next;
        phaseSince = System.nanoTime();
        stableSince = 0;
        LogUtils.getLogger().info("World/friends test phase: {}", next);
    }

    private void finish(Minecraft minecraft, boolean success, String details) {
        phase = Phase.COMPLETE;
        boolean onFriendsScreen = minecraft.gui.screen() instanceof FriendChatGui.Screen;
        boolean onChatScreen = minecraft.gui.screen() instanceof PublicChatGui.Screen;
        Path screenshot = output.resolve(onChatScreen ? "chat-screen.png" : onFriendsScreen ? "friends-screen.png" : "failure.png");
        var checks = new ArrayList<TestResults.Check>();
        checks.add(new TestResults.Check("World loaded", worldLoaded));
        checks.add(new TestResults.Check("Friends screen opened", friendsOpened));
        checks.add(new TestResults.Check("Friends visible", friendsVisible));
        checks.add(new TestResults.Check("Friends screenshot captured", friendsCaptured));
        checks.add(new TestResults.Check("Public chat opened", chatOpened));
        checks.add(new TestResults.Check("Received messages visible", messagesVisible && onChatScreen));
        if (!success) checks.add(new TestResults.Check("World/friends scenario completed", false));
        capture(minecraft, screenshot, saved -> {
            checks.add(new TestResults.Check("Chat screenshot captured", saved && onChatScreen));
            completion.accept(new Outcome(List.copyOf(checks), List.copyOf(screenshots), details));
        });
    }

    private void capture(Minecraft minecraft, Path screenshot, Consumer<Boolean> callback) {
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            boolean saved = false;
            try (image) {
                Files.createDirectories(output);
                image.writeToFile(screenshot);
                saved = true;
            } catch (Exception error) {
                LogUtils.getLogger().error("Could not save client UI screenshot.", error);
            }
            if (saved && !screenshots.contains(screenshot)) screenshots.add(screenshot);
            callback.accept(saved);
        });
    }

    private static int countVisibleMessages(GuiElement<?> element,
            List<net.creeperhost.minetogether.lib.chat.message.Message> received) throws ReflectiveOperationException {
        if (!element.isEnabled()) return 0;
        int count = 0;
        if (element instanceof MessageElement row && row.getParent() instanceof GuiList<?> list) {
            var message = row.getMessage();
            // Local welcome/status lines and our own messages cannot satisfy the receiving check.
            if (message.sender != null && message.sender != MineTogetherChat.getOurProfile()
                    && received.contains(message) && !message.getMessage().getMessage().isBlank()
                    && row.ySize() > 0 && row.yMin() < list.yMax() && row.yMax() > list.yMin()
                    && row.xMin() < list.xMax() && row.xMax() > list.xMin()
                    && !((List<?>) field(MessageElement.class, "wrappedLines", row)).isEmpty()) count++;
        }
        for (var child : element.getChildren()) count += countVisibleMessages(child, received);
        return count;
    }

    private static Object field(Class<?> owner, String name, Object instance) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static void press(Button button) {
        button.onPress(new KeyEvent(InputConstants.KEY_RETURN, InputConstants.KEYCODE_RETURN, 0));
    }
}
