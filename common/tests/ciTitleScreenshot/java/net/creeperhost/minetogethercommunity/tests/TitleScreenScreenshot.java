package net.creeperhost.minetogethercommunity.tests;

import com.mojang.logging.LogUtils;
import net.creeperhost.minetogether.lib.chat.irc.IrcState;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class TitleScreenScreenshot {
    private final Path output = Path.of(System.getProperty("minetogether.ci.titleScreenOutput"));
    private final String loader = System.getProperty("minetogether.ci.loader");
    private long titleVisibleSince;
    private long firstFrameAt;
    private String lastScreen;
    private final boolean requireIrc = Boolean.getBoolean("minetogether.ci.requireIrc");
    private String lastIrcState;
    private boolean captureRequested;
    private boolean titleReached;
    private WorldFriendsScenario worldFriends;

    public void afterFrame() {
        if (worldFriends != null) {
            worldFriends.afterFrame(Minecraft.getInstance());
            return;
        }
        if (captureRequested) return;
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.nanoTime();
        if (firstFrameAt == 0) firstFrameAt = now;
        var chat = MineTogetherChat.CHAT_STATE;
        IrcState ircState = chat == null ? null : chat.ircClient.getState();
        String ircStatus = ircState == null ? "NOT_STARTED" : ircState.name();
        if (requireIrc && !ircStatus.equals(lastIrcState)) {
            LogUtils.getLogger().info("Title screen test: IRC state is {}", ircStatus);
            lastIrcState = ircStatus;
        }
        String screen = minecraft.gui.screen() == null ? "none" : minecraft.gui.screen().getClass().getName();
        if (!screen.equals(lastScreen)) {
            LogUtils.getLogger().info("Title screen test: current screen is {}", screen);
            lastScreen = screen;
        }
        if (minecraft.gui.screen() instanceof TitleScreen && minecraft.gui.overlay() == null) {
            titleReached = true;
        }
        if (now - firstFrameAt >= TimeUnit.SECONDS.toNanos(90)) {
            capture(minecraft, false, "Title screen test timed out. Current screen: " + screen
                    + (requireIrc ? "; IRC state: " + ircStatus : ""));
            return;
        }
        if (!(minecraft.gui.screen() instanceof TitleScreen) || minecraft.gui.overlay() != null) {
            titleVisibleSince = 0;
            return;
        }
        if (titleVisibleSince == 0) titleVisibleSince = now;
        // Allow the title-screen fade and resource loading to finish before capture.
        if (now - titleVisibleSince < TimeUnit.SECONDS.toNanos(5)) return;

        boolean authenticated = Boolean.getBoolean("minetogether.ci.requireAuthentication");
        var user = minecraft.getUser();
        if (authenticated && !isAuthenticated(minecraft)) {
            capture(minecraft, false, "Title screen reached, but the client has an offline Minecraft session.");
            return;
        }
        // The socket-connect message only means VERIFYING. CONNECTED requires server-granted voice.
        if (requireIrc && ircState != IrcState.CONNECTED) {
            if (ircState == IrcState.BANNED || ircState == IrcState.CRASHED) {
                capture(minecraft, false, "Title screen reached, but IRC failed: " + ircStatus + ".");
            }
            return;
        }
        capture(minecraft, true, authenticated
                ? "MineTogether authenticated " + loader + " client reached the title screen as " + user.getName() + "."
                    + (requireIrc ? " IRC verified and connected." : "")
                : "MineTogether offline " + loader + " client reached the title screen.");
    }

    private void capture(Minecraft minecraft, boolean success, String summary) {
        captureRequested = true;
        // Snapshot checks on the render thread; uploading must not read mutable game state.
        var checks = new ArrayList<TestResults.Check>();
        checks.add(new TestResults.Check("Game started", firstFrameAt != 0));
        checks.add(new TestResults.Check("Title screen reached", titleReached));
        if (Boolean.getBoolean("minetogether.ci.requireAuthentication")) {
            checks.add(new TestResults.Check("Minecraft authenticated", isAuthenticated(minecraft)));
        }
        if (requireIrc) {
            var chat = MineTogetherChat.CHAT_STATE;
            checks.add(new TestResults.Check("IRC connected", chat != null && chat.ircClient.getState() == IrcState.CONNECTED));
        }
        Path screenshot = output.resolve(success ? "title-screen.png" : "failure.png");
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            boolean screenshotSaved = false;
            try (image) {
                Files.createDirectories(output);
                image.writeToFile(screenshot);
                screenshotSaved = true;
                LogUtils.getLogger().info("Title screen test captured result: {}", summary);
            } catch (Exception error) {
                LogUtils.getLogger().error("Could not save the title screen screenshot.", error);
            }
            checks.add(new TestResults.Check("Title screenshot captured", screenshotSaved));
            if (success && screenshotSaved && Boolean.getBoolean("minetogether.ci.worldFriends")) {
                worldFriends = new WorldFriendsScenario(output, outcome -> {
                    checks.addAll(outcome.checks());
                    var attachments = new ArrayList<Path>();
                    // Show the friends screen in the main embed; retain the title-screen capture too.
                    attachments.addAll(outcome.screenshots());
                    attachments.add(screenshot);
                    report(minecraft, new TestResults(loader, checks, summary + "\n" + outcome.details()), attachments);
                });
                return;
            }
            var results = new TestResults(loader, checks, screenshotSaved ? summary : summary + " Screenshot capture failed.");
            var attachments = new ArrayList<Path>();
            if (screenshotSaved) attachments.add(screenshot);
            // A timed-out test must fail even if it happened to reach the title screen at the deadline.
            if (!success) checks.add(new TestResults.Check("Scenario completed", false));
            report(minecraft, new TestResults(loader, checks, results.details()), attachments);
        });
    }

    private void report(Minecraft minecraft, TestResults results, ArrayList<Path> attachments) {
        boolean success = results.checks().stream().allMatch(TestResults.Check::passed);
        Path result = output.resolve(success ? "success.txt" : "failure.txt");
        try {
            Files.createDirectories(output);
            Files.writeString(result, results.asText());
            results.write(output.resolve("results.json"));
            attachments.add(result);
        } catch (Exception error) {
            LogUtils.getLogger().error("Could not save the test result file.", error);
        }
        // File capture has finished; upload on a worker while Minecraft continues rendering.
        CompletableFuture.runAsync(() -> {
            try {
                if (Boolean.parseBoolean(System.getenv("MT_CI_DEFER_REPORTS"))) {
                    LogUtils.getLogger().info("Client results saved for the combined Discord report.");
                    return;
                }
                var reporter = DiscordReporter.fromEnvironment();
                if (reporter.isPresent()) {
                    reporter.get().send(results, attachments);
                    Files.writeString(output.resolve("discord-sent.txt"), "Discord report delivered.\n");
                    LogUtils.getLogger().info("Title screen report delivered to Discord.");
                } else {
                    LogUtils.getLogger().info("Local capture complete; Discord reporting skipped (no webhook configured).");
                }
            } catch (Exception error) {
                LogUtils.getLogger().error("CI reporting failed: {}", error.getMessage());
            } finally {
                minecraft.execute(minecraft::stop);
            }
        });
    }

    private static boolean isAuthenticated(Minecraft minecraft) {
        var user = minecraft.getUser();
        return user.getProfileId().version() != 3 && !user.getAccessToken().isBlank()
                && !user.getAccessToken().equals("0");
    }
}
