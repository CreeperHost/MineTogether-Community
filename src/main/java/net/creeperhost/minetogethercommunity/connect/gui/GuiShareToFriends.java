package net.creeperhost.minetogethercommunity.connect.gui;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.creeperhost.minetogether.session.JWebToken;
import net.creeperhost.minetogethercommunity.compat.MTPartners;
import net.creeperhost.minetogethercommunity.connect.ConnectHandler;
import net.creeperhost.minetogethercommunity.connect.netty.NettyClient;
import net.creeperhost.minetogethercommunity.gui.chat.MTStyle;
import net.creeperhost.minetogethercommunity.modulargui.GuiButton;
import net.creeperhost.minetogethercommunity.modulargui.GuiElement;
import net.creeperhost.minetogethercommunity.modulargui.GuiProvider;
import net.creeperhost.minetogethercommunity.modulargui.GuiRectangle;
import net.creeperhost.minetogethercommunity.modulargui.GuiSlider;
import net.creeperhost.minetogethercommunity.modulargui.GuiText;
import net.creeperhost.minetogethercommunity.modulargui.GuiTexture;
import net.creeperhost.minetogethercommunity.modulargui.ModularGui;
import net.creeperhost.minetogethercommunity.modulargui.ModularGuiScreen;
import net.creeperhost.minetogethercommunity.oauth.KeycloakOAuth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.GameType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class GuiShareToFriends implements GuiProvider {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Connect GUI");
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("MT Connect GUI").build());
    private static final ResourceLocation CONNECT_LOGO = new ResourceLocation("minetogethercommunity", "textures/gui/minetogether_connect.png");

    private GameType gameMode = GameType.SURVIVAL;
    private boolean commands;
    private int maxPlayers = 2;
    private double sliderValue = 1D;
    private boolean playerCheckStarted;
    private boolean playerCheckFailed;
    private boolean noPlayerLimit;
    private CompletableFuture<?> playerCheckTask;

    @Override
    public GuiElement<?> createRootElement(final ModularGui gui) {
        gui.setPauseScreen(true);
        gui.renderScreenBackground(false);
        initFromServer(gui.mc().getIntegratedServer());

        GuiElement<?> root = new GuiElement<>(gui);
        int screenWidth = gui.getScreen().width;
        int screenHeight = gui.getScreen().height;
        int w = Math.min(340, Math.max(300, screenWidth - 24));
        int left = (screenWidth - w) / 2;
        boolean shortScreen = screenHeight < 260;
        int logoWidth = shortScreen ? 192 : 256;
        int logoHeight = logoWidth / 4;
        int bodyHeight = 168;
        int bodyTop = Math.max(logoHeight + 18, (screenHeight - bodyHeight) / 2 + (shortScreen ? 18 : 30));
        int logoTop = Math.max(0, bodyTop - logoHeight - 16);
        int buttonGap = 10;
        int buttonWidth = (w - buttonGap) / 2;

        new GuiRectangle(root, 0xC0000000).setBounds(0, 0, screenWidth, screenHeight);
        new GuiTexture(root, CONNECT_LOGO).textureSize(512, 128).setBounds((screenWidth - logoWidth) / 2, logoTop, logoWidth, logoHeight);
        new GuiText(root, () -> I18n.format("minetogether.connect.open.connect_intro"))
                .centered()
                .setWrap(true)
                .setColor(0x5555FF)
                .setBounds(left, bodyTop, w, 20);
        new GuiText(root, () -> I18n.format("minetogether.connect.open.connect_security"))
                .centered()
                .setWrap(true)
                .setColor(0xAAAAAA)
                .setBounds(left, bodyTop + 20, w, 22);

        new GuiText(root, () -> I18n.format("minetogether.connect.open.settings")).centered().setBounds(left, bodyTop + 45, w, 10);

        final GuiButton gameModeButton = new GuiButton(root, () -> I18n.format("selectWorld.gameMode") + ": " + I18n.format("selectWorld.gameMode." + gameMode.getName()))
                .setBounds(left, bodyTop + 59, buttonWidth, 16)
                .onPress(() -> gameMode = nextGameMode(gameMode));

        new GuiButton(root, () -> I18n.format("selectWorld.allowCommands") + ": " + I18n.format(commands ? "options.on" : "options.off"))
                .setBounds(left + buttonWidth + buttonGap, bodyTop + 59, buttonWidth, 16)
                .onPress(() -> commands = !commands);

        new GuiRectangle(root, 0xFF000000).setBounds(left, bodyTop + 80, w, 16);
        new GuiRectangle(root, 0xFF909090).setBounds(left, bodyTop + 80, w, 1);
        new GuiRectangle(root, 0xFF909090).setBounds(left, bodyTop + 95, w, 1);
        new GuiRectangle(root, 0xFF909090).setBounds(left, bodyTop + 80, 1, 16);
        new GuiRectangle(root, 0xFF909090).setBounds(left + w - 1, bodyTop + 80, 1, 16);
        new GuiSlider(root)
                .setBounds(left + 6, bodyTop + 82, w - 12, 12)
                .bindValue(() -> sliderValue, value -> sliderValue = Math.max(0D, Math.min(1D, value)));
        new GuiText(root, () -> I18n.format("minetogether.connect.open.max_players") + ": " + playersDisplay(getPlayersSetting()))
                .centered()
                .setBounds(left, bodyTop + 83, w, 10);

        new GuiText(root, () -> playerCheckFailed ? I18n.format("minetogether.connect.open.max_players_error") : playerLimitText())
                .centered()
                .setWrap(true)
                .setColor(playerCheckFailed ? 0xFF5555 : 0xAAAAAA)
                .setBounds(left, bodyTop + 101, w, 24);

        new TextLink(root, () -> I18n.format("minetogether.connect.open.mt_supporter_info"), () -> openUrl("https://minetogether.io/profile/subscriptions"))
                .setBounds(left, bodyTop + 128, w, 10);
        new TextLink(root, () -> I18n.format("minetogether.connect.open.order_info"), () -> MTPartners.openOrderUI(gui))
                .setBounds(left, bodyTop + 142, w, 10);

        new GuiButton(root, () -> ConnectHandler.isPublished()
                ? I18n.format("minetogether.connect.close_server")
                : ConnectHandler.isPublishing()
                ? I18n.format("minetogether.connect.open.opening")
                : I18n.format("minetogether.connect.open.start"))
                .primary()
                .setBounds(left, bodyTop + 162, buttonWidth, 16)
                .onPress(() -> {
                    if (ConnectHandler.isPublished() || ConnectHandler.isPublishing()) {
                        ConnectHandler.closeSharing();
                    } else {
                        openWorld(gui);
                    }
                });

        new GuiButton(root, () -> I18n.format("minetogether.gui.button.cancel"))
                .setBounds(left + buttonWidth + buttonGap, bodyTop + 162, buttonWidth, 16)
                .onPress(() -> gui.mc().displayGuiScreen(gui.getParentScreen()));

        if (!playerCheckStarted) {
            startPlayersCheck();
        }
        return root;
    }

    @Override
    public void tick(ModularGui gui) {
        if (playerCheckTask != null && playerCheckTask.isDone()) {
            playerCheckTask = null;
        }
    }

    private void initFromServer(IntegratedServer server) {
        if (server == null) return;
        gameMode = server.getGameType() == GameType.NOT_SET ? GameType.SURVIVAL : server.getGameType();
        if (server.getEntityWorld() != null) {
            commands = server.getEntityWorld().getWorldInfo().areCommandsAllowed();
        }
    }

    private GameType nextGameMode(GameType current) {
        if (current == GameType.SURVIVAL) return GameType.CREATIVE;
        if (current == GameType.CREATIVE) return GameType.ADVENTURE;
        if (current == GameType.ADVENTURE) return GameType.SPECTATOR;
        return GameType.SURVIVAL;
    }

    private int getPlayersSetting() {
        if (noPlayerLimit) {
            int value = 2 + (int) Math.round(sliderValue * 98D);
            return value >= 100 ? Integer.MAX_VALUE : value;
        }
        int cap = Math.max(2, maxPlayers);
        return 2 + (int) Math.round(sliderValue * (cap - 2));
    }

    private String playersDisplay(int players) {
        return players == Integer.MAX_VALUE ? I18n.format("minetogether.connect.open.max_players.unlimited") : String.valueOf(players);
    }

    private String playerLimitText() {
        if (playerCheckTask != null) {
            return I18n.format("minetogether.connect.open.max_players_checking");
        }
        if (noPlayerLimit) {
            return I18n.format("minetogether.connect.open.no_player_limit");
        }
        return "";
    }

    private void openUrl(String url) {
        try {
            if (!KeycloakOAuth.openURL(new URL(url))) {
                LOGGER.warn("Could not open URL {}", url);
            }
        } catch (MalformedURLException ex) {
            LOGGER.error("Invalid URL {}", url, ex);
        }
    }

    private void openWorld(ModularGui gui) {
        Minecraft mc = gui.mc();
        mc.displayGuiScreen(null);
        if (mc.ingameGUI != null) {
            mc.ingameGUI.getChatGUI().printChatMessage(new TextComponentTranslation("minetogether.connect.open.attempting"));
        }
        ConnectHandler.publishToFriends(gameMode, commands, getPlayersSetting());
    }

    private void startPlayersCheck() {
        playerCheckStarted = true;
        playerCheckFailed = false;
        playerCheckTask = CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    JWebToken token = ConnectHandler.requireSessionToken();
                    int result = NettyClient.getMaxPlayers(ConnectHandler.getEndpoint(), token);
                    if (result == -1) {
                        noPlayerLimit = true;
                        maxPlayers = 100;
                    } else {
                        noPlayerLimit = false;
                        maxPlayers = Math.max(2, result);
                    }
                    sliderValue = 1D;
                    playerCheckFailed = false;
                } catch (Throwable ex) {
                    LOGGER.error("Failed to check MineTogether Connect player limit", ex);
                    playerCheckFailed = true;
                    noPlayerLimit = false;
                    maxPlayers = 2;
                    sliderValue = 1D;
                }
            }
        }, EXECUTOR);
    }

    private static class TextLink extends GuiElement<TextLink> {
        private final Supplier<String> label;
        private final Runnable action;

        private TextLink(GuiElement<?> parent, Supplier<String> label, Runnable action) {
            super(parent);
            this.label = label;
            this.action = action;
        }

        @Override
        protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
            String text = label.get();
            int color = isMouseOver(mouseX, mouseY) ? 0x5555FF : 0xAAAAAA;
            drawCenteredString(font(), trim(text), x + width / 2, y, color);
            if (isMouseOver(mouseX, mouseY)) {
                int textWidth = Math.min(font().getStringWidth(text), width);
                int underlineY = y + font().FONT_HEIGHT;
                drawRect(x + (width - textWidth) / 2, underlineY, x + (width + textWidth) / 2, underlineY + 1, color | 0xFF000000);
            }
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
            if (mouseButton != 0 || !isMouseOver(mouseX, mouseY)) return false;
            action.run();
            return true;
        }

        private String trim(String text) {
            if (text == null) return "";
            if (font().getStringWidth(text) <= width) return text;
            int dots = font().getStringWidth("...");
            return font().trimStringToWidth(text, Math.max(1, width - dots)) + "...";
        }
    }

    public static class Screen extends ModularGuiScreen {
        public Screen(GuiScreen parentScreen) {
            super(new GuiShareToFriends(), parentScreen);
        }
    }
}
