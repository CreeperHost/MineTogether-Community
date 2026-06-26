package net.creeperhost.minetogethercommunity.client;

import net.creeperhost.minetogether.lib.chat.message.Message;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogether.lib.chat.profile.ProfileManager;
import net.creeperhost.minetogethercommunity.Constants;
import net.creeperhost.minetogethercommunity.activity.ActivityTelemetry;
import net.creeperhost.minetogethercommunity.chat.ChatStatistics;
import net.creeperhost.minetogethercommunity.chat.ChatTarget;
import net.creeperhost.minetogethercommunity.chat.FriendChatNotifier;
import net.creeperhost.minetogethercommunity.chat.InGameChatBridge;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.connect.ConnectHandler;
import net.creeperhost.minetogethercommunity.connect.gui.GuiShareToFriends;
import net.creeperhost.minetogethercommunity.connect.gui.ServerListAppender;
import net.creeperhost.minetogethercommunity.config.Config;
import net.creeperhost.minetogethercommunity.config.LocalConfig;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticApiClient;
import net.creeperhost.minetogethercommunity.cosmetic.PlayerCosmeticCache;
import net.creeperhost.minetogethercommunity.gui.IconButton;
import net.creeperhost.minetogethercommunity.gui.PreviewElement;
import net.creeperhost.minetogethercommunity.gui.SettingGui;
import net.creeperhost.minetogethercommunity.gui.chat.FriendChatGui;
import net.creeperhost.minetogethercommunity.gui.chat.MTStyle;
import net.creeperhost.minetogethercommunity.gui.chat.PublicChatGui;
import net.creeperhost.minetogethercommunity.oauth.KeycloakOAuth;
import net.creeperhost.minetogethercommunity.oauth.ServerAuthTest;
import net.creeperhost.minetogethercommunity.proxy.ClientProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.resources.I18n;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.function.Supplier;

public class ClientEvents {

    private static final int BUTTON_SETTINGS = -812001;
    private static final int BUTTON_FRIENDS = -812002;
    private static final int BUTTON_CHAT = -812003;
    private static final int BUTTON_CONNECT = -812004;
    private static final int BUTTON_ISSUE_TRACKER = -812005;
    private static final int BUTTON_CHAT_WIDTH = -812010;
    private static final int BUTTON_CHAT_HEIGHT = -812011;
    private static final int BUTTON_CHAT_SCALE = -812012;
    private static final int BUTTON_TARGET_VANILLA = -812020;
    private static final int BUTTON_TARGET_PUBLIC = -812021;
    private static final int BUTTON_TARGET_GROUP = -812022;
    private static final int BUTTON_NEW_USER_ACCEPT = -812030;
    private static final int BUTTON_NEW_USER_REJECT = -812031;
    private static final int VANILLA_BUTTON_SHARE_TO_LAN = 7;
    private static final Field CHAT_INPUT_FIELD = findField(GuiChat.class, "inputField", "field_146415_a");
    private static final Field DRAWN_CHAT_LINES = findField(GuiNewChat.class, "drawnChatLines", "field_146253_i");
    private static final Field CHAT_SCROLL_POS = findField(GuiNewChat.class, "scrollPos", "field_146250_j");
    private static final Field CHAT_IS_SCROLLED = findField(GuiNewChat.class, "isScrolled", "field_146251_k");
    private static final Field GUI_BUTTON_LIST = findField(GuiScreen.class, "buttonList", "field_146292_n");

    private boolean hadWorld;
    private int cosmeticScanTicks;
    private ChatActionPopup chatActionPopup;

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Keybindings.handleInput();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean hasWorld = Minecraft.getMinecraft().world != null;
        if (hasWorld && !hadWorld) {
            ClientProxy.onClientWorldJoin();
        } else if (!hasWorld && hadWorld) {
            ClientProxy.onClientWorldLeave();
        }
        hadWorld = hasWorld;
        if (hasWorld) {
            scanRemoteCosmetics(Minecraft.getMinecraft());
        } else {
            cosmeticScanTicks = 0;
        }
        ServerAuthTest.processPackets();
        FriendChatNotifier.tick();
        InGameChatBridge.tick();
        if (Minecraft.getMinecraft().currentScreen instanceof GuiChat && LocalConfig.instance().chatEnabled) {
            clampFocusedChatHeight(Minecraft.getMinecraft());
        }
        if (!(Minecraft.getMinecraft().currentScreen instanceof GuiChat) || MineTogetherChat.getTarget() == ChatTarget.VANILLA) {
            chatActionPopup = null;
        }
        ServerListAppender.INSTANCE.tick(Minecraft.getMinecraft().currentScreen);
        ActivityTelemetry.clientTick();
    }

    @SubscribeEvent
    public void onClientChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (message == null || message.trim().isEmpty()) return;
        if (message.startsWith("/")) {
            if (MineTogetherChat.getTarget() != ChatTarget.VANILLA) {
                MineTogetherChat.setTarget(ChatTarget.VANILLA);
            }
            return;
        }
        ChatTarget target = MineTogetherChat.getTarget();
        if (target == ChatTarget.VANILLA) return;

        event.setCanceled(true);
        if (target == ChatTarget.PUBLIC && MineTogetherChat.isNewUser()) {
            ChatStatistics.pollStats();
            MineTogetherChat.localStatus("minetogether.gui.chat.accept_first");
            return;
        }
        if (!MineTogetherChat.sendMessageToTarget(target, message)) {
            MineTogetherChat.localStatus("minetogether.gui.chat.send_failed");
        }
    }

    @SubscribeEvent
    public void onClientChatReceived(ClientChatReceivedEvent event) {
        if (event.getType() == ChatType.GAME_INFO) return;
        InGameChatBridge.captureVanillaMessage(event.getMessage());
        if (MineTogetherChat.getTarget() != ChatTarget.VANILLA) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderChatOverlay(RenderGameOverlayEvent.Chat event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof GuiChat)
                || !LocalConfig.instance().chatEnabled
                || mc.gameSettings.hideGUI
                || mc.ingameGUI == null) {
            return;
        }

        if (mc.gameSettings.chatVisibility == EntityPlayer.EnumChatVisibility.HIDDEN) {
            return;
        }

        GuiNewChat chat = mc.ingameGUI.getChatGUI();
        try {
            drawFocusedChat(mc, chat, event);
            event.setCanceled(true);
        } catch (IllegalAccessException ignored) {
            drawFocusedChatBackdrop(mc, chat, event.getResolution().getScaledHeight());
        }
    }

    @SubscribeEvent
    public void onChatMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        GuiScreen gui = event.getGui();
        int button = Mouse.getEventButton();
        if (!(gui instanceof GuiChat)
                || !LocalConfig.instance().chatEnabled
                || MineTogetherChat.getTarget() == ChatTarget.VANILLA
                || (button != 0 && button != 1)
                || !Mouse.getEventButtonState()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        int rawMouseX = Mouse.getEventX();
        int rawMouseY = Mouse.getEventY();
        int mouseX = rawMouseX * gui.width / mc.displayWidth;
        int mouseY = gui.height - rawMouseY * gui.height / mc.displayHeight - 1;

        if (isOverChatControl(gui, mouseX, mouseY)) {
            chatActionPopup = null;
            return;
        }

        if (chatActionPopup != null) {
            chatActionPopup.mouseClicked(mouseX, mouseY);
            event.setCanceled(true);
            return;
        }

        Message message = button == 1 || button == 0 && GuiScreen.isShiftKeyDown()
                ? InGameChatBridge.getMessageUnderMouse(mouseX, mouseY)
                : InGameChatBridge.getClickedMessage(mouseX, mouseY);
        PreviewElement.URLInfo urlInfo = InGameChatBridge.getUrlUnderMouse(mouseX, mouseY);
        URL clickedUrl = urlInfo == null ? null : urlInfo.getUrl();
        if (!isActionableMessage(message) && clickedUrl == null) return;

        event.setCanceled(true);
        if (button == 0 && clickedUrl != null && !GuiScreen.isShiftKeyDown()) {
            if (!KeycloakOAuth.openURL(clickedUrl)) {
                MineTogetherChat.localStatus("minetogether.gui.chat.action.open_failed");
            }
            return;
        }
        if (isActionableMessage(message) && LocalConfig.instance().shiftClickMention && button == 0 && GuiScreen.isShiftKeyDown()) {
            mentionInChat((GuiChat) gui, message.sender);
            return;
        }
        chatActionPopup = new ChatActionPopup(message, clickedUrl, mouseX, mouseY, gui.width, gui.height);
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.getGui();
        if (gui instanceof GuiChat && LocalConfig.instance().chatEnabled) {
            clampFocusedChatHeight(Minecraft.getMinecraft());
            addChatTargetButtons(event, gui);
            if (LocalConfig.instance().chatSettingsSliders) {
                addChatOptionSliders(event, gui);
            }
            if (MineTogetherChat.isNewUser() && MineTogetherChat.getTarget() == ChatTarget.PUBLIC) {
                ChatStatistics.pollStats();
                addNewUserButtons(event, gui);
            }
        } else if (gui instanceof GuiMainMenu && LocalConfig.instance().mainMenuButtons) {
            addMenuButtons(event, gui);
        } else if (gui instanceof GuiIngameMenu) {
            replaceIssueTrackerButton(event);
            if (Config.instance().pauseScreenButtons) {
                addMenuButtons(event, gui);
            }
            if (Minecraft.getMinecraft().isSingleplayer()) {
                addPauseConnectButton(event, gui);
            }
        } else if (gui instanceof GuiMultiplayer && ConnectHandler.isEnabled()) {
            ServerListAppender.INSTANCE.init((GuiMultiplayer) gui);
        }
    }

    @SubscribeEvent
    public void onActionPre(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.getGui() instanceof GuiMultiplayer && event.getButton().id == 1
                && ServerListAppender.INSTANCE.hasSelectedFriendServer((GuiMultiplayer) event.getGui())) {
            event.setCanceled(true);
            ServerListAppender.INSTANCE.openSelected((GuiMultiplayer) event.getGui());
        }
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Post event) {
        GuiButton button = event.getButton();
        Minecraft mc = Minecraft.getMinecraft();
        if (button.id == BUTTON_SETTINGS) {
            mc.displayGuiScreen(new SettingGui.Screen(event.getGui()));
        } else if (button.id == BUTTON_FRIENDS) {
            mc.displayGuiScreen(new FriendChatGui.Screen(event.getGui()));
        } else if (button.id == BUTTON_CHAT) {
            mc.displayGuiScreen(new PublicChatGui.Screen(event.getGui()));
        } else if (button.id == BUTTON_CONNECT) {
            if (isIntegratedSharingOpen(mc)) {
                ConnectHandler.closeSharing();
                mc.displayGuiScreen(new GuiIngameMenu());
            } else {
                mc.displayGuiScreen(new GuiShareToFriends.Screen(event.getGui()));
            }
        } else if (button.id == BUTTON_ISSUE_TRACKER) {
            openIssueTracker();
        } else if (button.id == BUTTON_TARGET_VANILLA) {
            MineTogetherChat.setTarget(ChatTarget.VANILLA);
        } else if (button.id == BUTTON_TARGET_PUBLIC) {
            MineTogetherChat.setTarget(ChatTarget.PUBLIC);
        } else if (button.id == BUTTON_TARGET_GROUP) {
            MineTogetherChat.setTarget(ChatTarget.GROUP);
        } else if (button.id == BUTTON_NEW_USER_ACCEPT) {
            MineTogetherChat.setNewUserResponded();
            button.visible = false;
        } else if (button.id == BUTTON_NEW_USER_REJECT) {
            MineTogetherChat.disableChat();
            LocalConfig.instance().chatEnabled = false;
            LocalConfig.save();
            MineTogetherChat.setNewUserResponded();
            button.visible = false;
        }
    }

    private void replaceIssueTrackerButton(GuiScreenEvent.InitGuiEvent.Post event) {
        String url = Config.instance().issueTrackerUrl;
        if (url == null || url.trim().isEmpty()) return;
        String label = I18n.format("menu.reportBugs");
        for (int i = 0; i < event.getButtonList().size(); i++) {
            GuiButton button = event.getButtonList().get(i);
            if (!label.equals(button.displayString)) continue;
            event.getButtonList().set(i, new GuiButton(BUTTON_ISSUE_TRACKER, button.x, button.y, button.width, button.height, button.displayString));
            return;
        }
    }

    private void openIssueTracker() {
        try {
            String url = Config.instance().issueTrackerUrl;
            if (url != null && !url.trim().isEmpty()) {
                KeycloakOAuth.openURL(new URL(url));
            }
        } catch (MalformedURLException ignored) {
        }
    }

    private void addPauseConnectButton(GuiScreenEvent.InitGuiEvent.Post event, GuiScreen gui) {
        GuiButton lanButton = findButton(event.getButtonList(), VANILLA_BUTTON_SHARE_TO_LAN);
        boolean sharingOpen = isIntegratedSharingOpen(Minecraft.getMinecraft());
        if (lanButton != null) {
            if (sharingOpen) {
                int index = event.getButtonList().indexOf(lanButton);
                event.getButtonList().set(index, new TrimmingButton(BUTTON_CONNECT, lanButton.x, lanButton.y,
                        lanButton.width, lanButton.height, I18n.format("minetogether.connect.close_server")));
            } else {
                int gap = 4;
                int totalWidth = lanButton.width;
                int leftWidth = Math.max(50, (totalWidth - gap) / 2);
                int rightWidth = Math.max(50, totalWidth - leftWidth - gap);
                lanButton.width = leftWidth;
                event.getButtonList().add(new TrimmingButton(BUTTON_CONNECT,
                        lanButton.x + leftWidth + gap, lanButton.y, rightWidth, lanButton.height,
                        I18n.format("minetogether.connect.open")));
            }
            return;
        }

        String label = I18n.format(sharingOpen ? "minetogether.connect.close_server" : "minetogether.connect.open");
        int x = gui.width - 105;
        int y = 25;
        int width = 100;
        int height = 20;
        if (Config.instance().moveButtonsOnPauseMenu) {
            int preferredX = gui.width / 2 - 100;
            int preferredY = gui.height / 4 + 144;
            for (GuiButton button : event.getButtonList()) {
                if (intersects(preferredX, preferredY, width, height, button.x, button.y, button.width, button.height)) {
                    preferredY = Math.max(preferredY, button.y + button.height + 4);
                }
            }
            if (preferredY + height <= gui.height - 6 && !collides(event.getButtonList(), preferredX, preferredY, width, height)) {
                x = preferredX;
                y = preferredY;
            }
        }
        event.getButtonList().add(new TrimmingButton(BUTTON_CONNECT, x, y, width, height, label));
    }

    private GuiButton findButton(List<GuiButton> buttons, int id) {
        for (GuiButton button : buttons) {
            if (button.id == id) return button;
        }
        return null;
    }

    private boolean isIntegratedSharingOpen(Minecraft mc) {
        if (ConnectHandler.isPublished() || ConnectHandler.isPublishing()) return true;
        if (mc == null || !mc.isSingleplayer()) return false;
        IntegratedServer server = mc.getIntegratedServer();
        return server != null && server.getPublic();
    }

    private boolean collides(List<GuiButton> buttons, int x, int y, int width, int height) {
        for (GuiButton button : buttons) {
            if (intersects(x, y, width, height, button.x, button.y, button.width, button.height)) {
                return true;
            }
        }
        return false;
    }

    private boolean intersects(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Pre event) {
        GuiScreen gui = event.getGui();
        if (gui instanceof GuiChat && LocalConfig.instance().chatEnabled && !Minecraft.getMinecraft().gameSettings.hideGUI) {
            syncChatControlBounds(gui);
        }
        if (!(gui instanceof GuiChat)
                || !LocalConfig.instance().chatEnabled
                || Minecraft.getMinecraft().gameSettings.hideGUI
                || MineTogetherChat.getTarget() != ChatTarget.PUBLIC
                || !MineTogetherChat.isNewUser()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        int x = newUserPanelX(gui);
        int y = newUserPanelY(gui);
        int width = newUserPanelWidth(gui);
        Gui.drawRect(x, y, x + width, y + 122, MTStyle.Flat.BACKGROUND);
        drawCentered(mc, I18n.format("minetogether.new_user.1"), x, y + 12, width, MTStyle.Flat.TEXT);
        drawCentered(mc, I18n.format("minetogether.new_user.2"), x, y + 30, width, 0xCCCCCC);
        drawCentered(mc, I18n.format("minetogether.new_user.3"), x, y + 44, width, 0xCCCCCC);
        drawCentered(mc, I18n.format("minetogether.new_user.4", ChatStatistics.userCount), x, y + 60, width, 0xCCCCCC);
    }

    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.getGui() instanceof GuiChat)
                || MineTogetherChat.getTarget() == ChatTarget.VANILLA
                || Minecraft.getMinecraft().gameSettings.hideGUI) {
            return;
        }
        if (chatActionPopup != null) {
            chatActionPopup.draw(event.getMouseX(), event.getMouseY());
            return;
        }
        PreviewElement.URLInfo info = InGameChatBridge.getUrlUnderMouse(event.getMouseX(), event.getMouseY());
        if (info != null) {
            PreviewElement.renderPreview(Minecraft.getMinecraft(), info, event.getMouseX(), event.getMouseY(),
                    event.getGui().width, event.getGui().height, 80, false);
        }
    }

    private void addMenuButtons(GuiScreenEvent.InitGuiEvent.Post event, GuiScreen gui) {
        int x = gui.width - 25;
        int y = 5;
        event.getButtonList().add(new IconButton(BUTTON_SETTINGS, x, y, 3, Constants.WIDGETS_SHEET, I18n.format("minetogether.gui.button.settings.info")));
        event.getButtonList().add(new IconButton(BUTTON_FRIENDS, x - 21, y, 7, Constants.WIDGETS_SHEET, I18n.format("minetogether.gui.button.friends.info")));
        if (LocalConfig.instance().chatEnabled) {
            event.getButtonList().add(new IconButton(BUTTON_CHAT, x - 42, y, 1, Constants.WIDGETS_SHEET, I18n.format("minetogether.gui.button.global_chat.info")));
        }
    }

    private void addChatOptionSliders(GuiScreenEvent.InitGuiEvent.Post event, GuiScreen gui) {
        if (Minecraft.getMinecraft().gameSettings.hideGUI) return;

        int chatRight = chatContentRightEdge();
        int y = gui.height - 38;
        int sliderWidth = Math.max(44, chatRight / 3);
        event.getButtonList().add(new CompactChatSlider(BUTTON_CHAT_WIDTH, 0, y, sliderWidth, GameSettings.Options.CHAT_WIDTH));
        event.getButtonList().add(new CompactChatSlider(BUTTON_CHAT_HEIGHT, sliderWidth + 2, y, sliderWidth, GameSettings.Options.CHAT_HEIGHT_FOCUSED));
        event.getButtonList().add(new CompactChatSlider(BUTTON_CHAT_SCALE, (sliderWidth * 2) + 4, y, Math.max(44, chatRight - (sliderWidth * 2) - 4), GameSettings.Options.CHAT_SCALE));
    }

    private boolean isOverChatControl(GuiScreen gui, int mouseX, int mouseY) {
        if (LocalConfig.instance().chatSettingsSliders && isOverChatOptionSliders(gui, mouseX, mouseY)) return true;
        ChatLayout layout = chatLayout(gui);
        if (mouseX >= layout.x && mouseX < layout.x + 12) {
            if (mouseY >= layout.vanillaY && mouseY < layout.vanillaY + layout.vanillaHeight) return true;
            if (mouseY >= layout.publicY && mouseY < layout.publicY + layout.publicHeight) return true;
            if (layout.groupHeight > 0 && mouseY >= layout.groupY && mouseY < layout.groupY + layout.groupHeight) return true;
            if (mouseY >= layout.settingsY && mouseY < layout.settingsY + 12) return true;
        }
        return false;
    }

    private boolean isOverChatOptionSliders(GuiScreen gui, int mouseX, int mouseY) {
        int chatRight = chatContentRightEdge();
        int y = gui.height - 38;
        int sliderWidth = Math.max(44, chatRight / 3);
        int thirdWidth = Math.max(44, chatRight - (sliderWidth * 2) - 4);
        int sliderRight = (sliderWidth * 2) + 4 + thirdWidth;
        return mouseY >= y && mouseY < y + 7 && mouseX >= 0 && mouseX < sliderRight;
    }

    @SuppressWarnings("unchecked")
    private void syncChatControlBounds(GuiScreen gui) {
        List<GuiButton> buttons;
        try {
            buttons = (List<GuiButton>) GUI_BUTTON_LIST.get(gui);
        } catch (IllegalAccessException ignored) {
            return;
        }
        ChatLayout layout = chatLayout(gui);
        boolean groupChat = hasGroupChat();
        int chatRight = chatContentRightEdge();
        int sliderY = gui.height - 38;
        int sliderWidth = Math.max(44, chatRight / 3);
        int thirdWidth = Math.max(44, chatRight - (sliderWidth * 2) - 4);
        for (GuiButton button : buttons) {
            switch (button.id) {
                case BUTTON_TARGET_VANILLA:
                    setButtonBounds(button, layout.x, layout.vanillaY, 12, layout.vanillaHeight);
                    button.visible = true;
                    button.enabled = true;
                    break;
                case BUTTON_TARGET_PUBLIC:
                    setButtonBounds(button, layout.x, layout.publicY, 12, layout.publicHeight);
                    button.visible = true;
                    button.enabled = true;
                    break;
                case BUTTON_TARGET_GROUP:
                    setButtonBounds(button, layout.x, layout.groupY, 12, layout.groupHeight);
                    button.visible = groupChat;
                    button.enabled = groupChat;
                    break;
                case BUTTON_SETTINGS:
                    setButtonBounds(button, layout.x, layout.settingsY, 12, 12);
                    button.visible = true;
                    button.enabled = true;
                    break;
                case BUTTON_CHAT_WIDTH:
                    setButtonBounds(button, 0, sliderY, sliderWidth, 7);
                    break;
                case BUTTON_CHAT_HEIGHT:
                    setButtonBounds(button, sliderWidth + 2, sliderY, sliderWidth, 7);
                    break;
                case BUTTON_CHAT_SCALE:
                    setButtonBounds(button, (sliderWidth * 2) + 4, sliderY, thirdWidth, 7);
                    break;
                default:
                    break;
            }
        }
    }

    private void setButtonBounds(GuiButton button, int x, int y, int width, int height) {
        button.x = x;
        button.y = y;
        button.width = Math.max(0, width);
        button.height = Math.max(0, height);
    }

    private void addChatTargetButtons(GuiScreenEvent.InitGuiEvent.Post event, GuiScreen gui) {
        if (Minecraft.getMinecraft().gameSettings.hideGUI) return;

        ChatLayout layout = chatLayout(gui);
        boolean groupChat = hasGroupChat();
        event.getButtonList().add(new ChatTargetButton(BUTTON_TARGET_VANILLA, layout.x, layout.vanillaY, 12, layout.vanillaHeight,
                new Supplier<String>() {
                    @Override
                    public String get() {
                        return vanillaChatTargetLabel();
                    }
                }, ChatTarget.VANILLA));
        event.getButtonList().add(new ChatTargetButton(BUTTON_TARGET_PUBLIC, layout.x, layout.publicY, 12, layout.publicHeight,
                label("minetogether:ingame.chat.global"), ChatTarget.PUBLIC));

        ChatTargetButton group = new ChatTargetButton(BUTTON_TARGET_GROUP, layout.x, layout.groupY, 12, layout.groupHeight,
                label("minetogether:ingame.chat.group"), ChatTarget.GROUP);
        group.visible = groupChat;
        group.enabled = groupChat;
        event.getButtonList().add(group);

        event.getButtonList().add(new IconButton(BUTTON_SETTINGS, layout.x, layout.settingsY, 12, 12, Constants.GEAR_BUTTON, I18n.format("minetogether.gui.button.settings.info")));
    }

    private String vanillaChatTargetLabel() {
        Minecraft mc = Minecraft.getMinecraft();
        String key = shouldShowServerChatTarget(mc) ? "minetogether:ingame.chat.server" : "minetogether:ingame.chat.local";
        return I18n.format(key);
    }

    private boolean shouldShowServerChatTarget(Minecraft mc) {
        if (ConnectHandler.isPublished() || ConnectHandler.isPublishing()) return true;
        if (!mc.isSingleplayer()) return true;
        IntegratedServer server = mc.getIntegratedServer();
        return server != null && server.getPublic();
    }

    private Supplier<String> label(final String key) {
        return new Supplier<String>() {
            @Override
            public String get() {
                return I18n.format(key);
            }
        };
    }

    private void addNewUserButtons(GuiScreenEvent.InitGuiEvent.Post event, GuiScreen gui) {
        int x = newUserPanelX(gui);
        int y = newUserPanelY(gui);
        int width = newUserPanelWidth(gui);
        event.getButtonList().add(new TrimmingButton(BUTTON_NEW_USER_ACCEPT, x + 8, y + 78, width - 16, 20,
                I18n.format("minetogether.gui.join.button.accept", ChatStatistics.onlineCount)));
        event.getButtonList().add(new TrimmingButton(BUTTON_NEW_USER_REJECT, x + 8, y + 100, width - 16, 20,
                I18n.format("minetogether.gui.join.button.reject")));
    }

    private int newUserPanelWidth(GuiScreen gui) {
        return Math.min(310, Math.max(220, gui.width - 12));
    }

    private int newUserPanelX(GuiScreen gui) {
        int width = newUserPanelWidth(gui);
        return Math.max(6, gui.width - width - 6);
    }

    private int newUserPanelY(GuiScreen gui) {
        return Math.max(24, Math.min(gui.height - 154, 42));
    }

    private void drawCentered(Minecraft mc, String text, int x, int y, int width, int color) {
        String trimmed = trimToWidth(mc, text, width - 8);
        mc.fontRenderer.drawStringWithShadow(trimmed, x + (width - mc.fontRenderer.getStringWidth(trimmed)) / 2, y, color);
    }

    private void drawFocusedChat(Minecraft mc, GuiNewChat chat, RenderGameOverlayEvent.Chat event) throws IllegalAccessException {
        drawFocusedChatBackdrop(mc, chat, event.getResolution().getScaledHeight());

        @SuppressWarnings("unchecked")
        List<ChatLine> lines = (List<ChatLine>) DRAWN_CHAT_LINES.get(chat);
        if (lines == null || lines.isEmpty()) return;

        int updateCounter = mc.ingameGUI.getUpdateCounter();
        int maxLines = chat.getLineCount();
        int scrollPos = ((Integer) CHAT_SCROLL_POS.get(chat)).intValue();
        boolean isScrolled = ((Boolean) CHAT_IS_SCROLLED.get(chat)).booleanValue();
        float opacity = mc.gameSettings.chatOpacity * 0.9F + 0.1F;
        float scale = Math.max(0.1F, chat.getChatScale());
        int chatWidth = MathHelper.ceil(chat.getChatWidth() / scale);
        int renderedLines = 0;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.translate((float) event.getPosX(), (float) event.getPosY(), 0.0F);
        GlStateManager.translate(2.0F, 8.0F, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        for (int lineIndex = 0; lineIndex + scrollPos < lines.size() && lineIndex < maxLines; lineIndex++) {
            ChatLine line = lines.get(lineIndex + scrollPos);
            if (line == null) continue;
            int age = updateCounter - line.getUpdatedCounter();
            if (age >= 200 && !chat.getChatOpen()) continue;
            int alpha = chatLineAlpha(age, opacity, chat.getChatOpen());
            renderedLines++;
            if (alpha <= 3) continue;

            String text = line.getChatComponent().getFormattedText();
            int y = -lineIndex * 9;
            GlStateManager.enableBlend();
            mc.fontRenderer.drawStringWithShadow(text, 0.0F, (float) (y - 8), 0xFFFFFF + (alpha << 24));
        }
        drawFocusedChatScrollBar(lines.size(), renderedLines, scrollPos, isScrolled, mc.fontRenderer.FONT_HEIGHT);
        GlStateManager.disableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private int chatLineAlpha(int age, float opacity, boolean chatOpen) {
        int alpha;
        if (chatOpen) {
            alpha = 255;
        } else {
            double fade = 1.0D - (double) age / 200.0D;
            fade = MathHelper.clamp(fade * 10.0D, 0.0D, 1.0D);
            alpha = (int) (255.0D * fade * fade);
        }
        return (int) (alpha * opacity);
    }

    private void drawFocusedChatScrollBar(int totalLines, int renderedLines, int scrollPos, boolean isScrolled, int fontHeight) {
        if (totalLines <= 0 || renderedLines <= 0 || totalLines == renderedLines) return;
        GlStateManager.translate(-3.0F, 0.0F, 0.0F);
        int totalHeight = totalLines * fontHeight + totalLines;
        int visibleHeight = renderedLines * fontHeight + renderedLines;
        int barOffset = scrollPos * visibleHeight / totalLines;
        int barHeight = visibleHeight * visibleHeight / totalHeight;
        int alpha = barOffset > 0 ? 170 : 96;
        int color = isScrolled ? 0xCC3333 : 0x3333AA;
        Gui.drawRect(0, -barOffset, 2, -barOffset - barHeight, color + (alpha << 24));
        Gui.drawRect(2, -barOffset, 1, -barOffset - barHeight, 0xCCCCCC + (alpha << 24));
    }

    private void drawFocusedChatBackdrop(Minecraft mc, GuiNewChat chat, int screenHeight) {
        float scale = Math.max(0.1F, chat.getChatScale());
        int width = chatContentRightEdge();
        int height = Math.max(minChatTargetHeight(), MathHelper.ceil(chat.getChatHeight() * scale));
        int maxY = screenHeight - 40;
        int y = maxY - height;
        Gui.drawRect(0, y, width, maxY, focusedChatBackgroundColor(mc));
        if (MineTogetherChat.getTarget() != ChatTarget.VANILLA) {
            drawFocusedChatLogo(mc, width, y, height);
        }
    }

    private void drawFocusedChatLogo(Minecraft mc, int x, int y, int height) {
        if (x <= 0 || height <= 0) return;
        int logoSize = (int) (Math.min(x, height) * 0.9D);
        if (logoSize < 24) return;
        int logoX = -4 + x / 2 - logoSize / 2;
        int logoY = y + height / 2 - logoSize / 2;
        drawLogo(mc, logoX, logoY, logoSize, logoSize);
    }

    private int focusedChatBackgroundColor(Minecraft mc) {
        int alpha = (int) (128.0F * (mc.gameSettings.chatOpacity * 0.9F + 0.1F));
        return MathHelper.clamp(alpha, 0, 255) << 24;
    }

    private void drawLogo(Minecraft mc, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        FontRenderer font = mc.fontRenderer;
        String created = "Created by";
        int creeperHeight = 19;
        int creeperWidth = (int) (creeperHeight * (960D / 266D));
        int createdWidth = font.getStringWidth(created) + 2 + creeperWidth;
        float footerMaxHeight = Math.max(1.0F, height * 0.24F);
        float createdScale = Math.min(width / (float) createdWidth, footerMaxHeight / (float) creeperHeight);
        int createdScaledWidth = Math.round(createdWidth * createdScale);
        int creeperOffset = (int) ((font.FONT_HEIGHT / 2D) - (creeperHeight / 2D));
        int creeperScaledHeight = (int) (creeperHeight * createdScale);

        GlStateManager.enableBlend();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + (width - createdScaledWidth) / 2.0F, y + height - creeperScaledHeight - creeperOffset, 0.0F);
        GlStateManager.scale(createdScale, createdScale, 1.0F);
        mc.getTextureManager().bindTexture(Constants.CREEPERHOST_LOGO);
        Gui.drawScaledCustomSizeModalRect(createdWidth - creeperWidth, creeperOffset, 0.0F, 0.0F,
                960, 266, creeperWidth, creeperHeight, 960, 266);
        font.drawString(created, 0, 0, 0x40FFFFFF);
        GlStateManager.popMatrix();

        int mtAvailableHeight = Math.max(1, height - creeperScaledHeight - 4);
        int mtHeight = Math.min(mtAvailableHeight, (int) Math.floor(width / (348D / 318D)));
        int mtWidth = Math.max(1, (int) Math.round(mtHeight * (348D / 318D)));
        mc.getTextureManager().bindTexture(Constants.MINETOGETHER_LOGO_25);
        Gui.drawScaledCustomSizeModalRect(x + (width / 2) - (mtWidth / 2), y, 0.0F, 0.0F,
                348, 318, mtWidth, mtHeight, 348, 318);
        GlStateManager.disableBlend();
    }

    private boolean hasGroupChat() {
        return hasGroupChatStatic();
    }

    private boolean isActionableMessage(Message message) {
        return message != null && message.sender != null && message.sender != MineTogetherChat.getOurProfile();
    }

    private ChatLayout chatLayout(GuiScreen gui) {
        int x = chatTabX();
        int minimumHeight = minChatTargetHeight();
        int height = Math.max(minimumHeight - 12, chatFocusedHeight() - 12);
        int maxY = gui.height - 40;
        int topY = Math.max(4, maxY - height - 12);
        boolean groupChat = hasGroupChat();

        int settingsHeight = 12;
        int available = Math.max(36, maxY - topY - settingsHeight);
        int vanillaHeight = groupChat ? available / 3 : available / 2;
        int publicY = topY + vanillaHeight;
        int publicHeight = groupChat ? available / 3 : Math.max(12, maxY - publicY - settingsHeight);
        int groupY = publicY + publicHeight;
        int groupHeight = groupChat ? Math.max(12, maxY - groupY - settingsHeight) : 0;
        int settingsY = groupChat ? groupY + groupHeight : publicY + publicHeight;
        return new ChatLayout(x, topY, vanillaHeight, publicY, publicHeight, groupY, groupHeight, settingsY);
    }

    private static String trimToWidth(Minecraft mc, String text, int width) {
        if (text == null) return "";
        if (width <= 0 || mc.fontRenderer.getStringWidth(text) <= width) return text;
        int ellipsis = mc.fontRenderer.getStringWidth("...");
        return mc.fontRenderer.trimStringToWidth(text, Math.max(1, width - ellipsis)) + "...";
    }

    private int chatContentRightEdge() {
        Minecraft mc = Minecraft.getMinecraft();
        GuiNewChat chat = mc.ingameGUI.getChatGUI();
        float scale = Math.max(0.1F, mc.gameSettings.chatScale);
        return Math.max(0, MathHelper.ceil(chat.getChatWidth() + (12.0F * scale)));
    }

    private int chatTabX() {
        return chatContentRightEdge();
    }

    private int chatFocusedHeight() {
        Minecraft mc = Minecraft.getMinecraft();
        GuiNewChat chat = mc.ingameGUI.getChatGUI();
        float scale = Math.max(0.1F, mc.gameSettings.chatScale);
        return Math.max(minChatTargetHeight(), MathHelper.ceil(chat.getChatHeight() * scale));
    }

    private int minChatTargetHeight() {
        int tabs = hasGroupChatStatic() ? 3 : 2;
        return tabs * 12 + 12;
    }

    private static boolean hasGroupChatStatic() {
        return MineTogetherChat.CHAT_STATE != null
                && MineTogetherChat.CHAT_STATE.profileManager != null
                && MineTogetherChat.CHAT_STATE.profileManager.getPrivateGroup() != null;
    }

    private static void clampFocusedChatHeight(Minecraft mc) {
        if (mc == null || mc.ingameGUI == null) return;
        float minimum = minFocusedChatHeightValue(mc);
        if (mc.gameSettings.getOptionFloatValue(GameSettings.Options.CHAT_HEIGHT_FOCUSED) < minimum) {
            mc.gameSettings.setOptionFloatValue(GameSettings.Options.CHAT_HEIGHT_FOCUSED, minimum);
        }
    }

    private static float minFocusedChatHeightValue(Minecraft mc) {
        int tabs = hasGroupChatStatic() ? 3 : 2;
        int targetHeight = tabs * 12 + 12;
        float scale = Math.max(0.1F, mc.gameSettings.chatScale);
        int requiredChatHeight = MathHelper.ceil(targetHeight / scale);
        return MathHelper.clamp((requiredChatHeight - 20) / 160.0F, 0.0F, 1.0F);
    }

    private void mentionInChat(GuiChat gui, Profile profile) {
        if (profile == null || profile == MineTogetherChat.getOurProfile()) return;
        try {
            GuiTextField input = (GuiTextField) CHAT_INPUT_FIELD.get(gui);
            String value = input.getText();
            if (!value.isEmpty() && value.charAt(value.length() - 1) != ' ') {
                value += " ";
            }
            input.setText(value + MineTogetherChat.displayName(profile));
            input.setCursorPositionEnd();
            input.setFocused(true);
        } catch (IllegalAccessException ignored) {
        }
    }

    private void toggleMute(Profile profile) {
        if (profile == null || profile == MineTogetherChat.getOurProfile()) return;
        if (profile.isMuted()) {
            profile.unmute();
            MineTogetherChat.localStatus("minetogether.gui.chat.action.unmuted", MineTogetherChat.displayName(profile));
        } else {
            profile.mute();
            MineTogetherChat.localStatus("minetogether.gui.chat.action.muted", MineTogetherChat.displayName(profile));
        }
    }

    private void sendFriendRequest(final Profile profile) {
        if (profile == null || profile == MineTogetherChat.getOurProfile() || MineTogetherChat.CHAT_STATE == null) return;
        if (!profile.hasFriendCode()) {
            MineTogetherChat.localStatus("minetogether.gui.chat.action.friend_missing_code");
            return;
        }
        ProfileManager manager = MineTogetherChat.CHAT_STATE.profileManager;
        manager.sendFriendRequest(profile.getFriendCode(), MineTogetherChat.displayName(profile), success -> {
            MineTogetherChat.localStatus(success ? "minetogether.gui.friends.request_sent" : "minetogether.gui.friends.request_fail");
        });
    }

    private class ChatActionPopup {
        private final Message message;
        private final URL url;
        private final int x;
        private final int y;
        private final int width;
        private static final int PADDING = 4;
        private static final int ROW_HEIGHT = 13;
        private static final int MAX_WIDTH = 300;
        private final List<PopupOption> options = new java.util.ArrayList<PopupOption>();

        private ChatActionPopup(Message message, URL url, int mouseX, int mouseY, int screenWidth, int screenHeight) {
            this.message = message;
            this.url = url;
            final Profile sender = message == null ? null : message.sender;
            if (url != null) {
                options.add(new PopupOption(I18n.format("minetogether.gui.chat.action.open_link"), 0x55AAFF, new Runnable() {
                    @Override
                    public void run() {
                        if (!KeycloakOAuth.openURL(ChatActionPopup.this.url)) {
                            MineTogetherChat.localStatus("minetogether.gui.chat.action.open_failed");
                        }
                    }
                }));
            }
            if (isActionableMessage(message)) {
                options.add(new PopupOption(I18n.format("minetogether.gui.chat.action.mention"), 0x55FFFF, new Runnable() {
                    @Override
                    public void run() {
                        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                        if (screen instanceof GuiChat) {
                            mentionInChat((GuiChat) screen, ChatActionPopup.this.message.sender);
                        }
                    }
                }));
                if (sender != null && !sender.isFriend() && sender.hasFriendCode()) {
                    options.add(new PopupOption(I18n.format("minetogether.gui.chat.action.friend"), 0x55FFFF, new Runnable() {
                        @Override
                        public void run() {
                            sendFriendRequest(ChatActionPopup.this.message.sender);
                        }
                    }));
                }
                options.add(new PopupOption(sender != null && sender.isMuted()
                        ? I18n.format("minetogether.gui.chat.action.unmute")
                        : I18n.format("minetogether.gui.chat.action.mute"), 0xFF5555, new Runnable() {
                    @Override
                    public void run() {
                        toggleMute(ChatActionPopup.this.message.sender);
                    }
                }));
            }

            Minecraft mc = Minecraft.getMinecraft();
            int measuredWidth = mc.fontRenderer.getStringWidth(title());
            for (PopupOption option : options) {
                measuredWidth = Math.max(measuredWidth, mc.fontRenderer.getStringWidth(option.label));
            }
            width = Math.max(80, Math.min(Math.min(MAX_WIDTH, screenWidth - 8), measuredWidth + PADDING * 2));
            int height = height();
            int popupHeight = height();
            int left = mouseX + width + 4 > screenWidth ? mouseX - width - 4 : mouseX;
            int top = mouseY + popupHeight + 4 > screenHeight ? mouseY - popupHeight - 4 : mouseY;
            this.x = Math.max(4, Math.min(left, screenWidth - width - 4));
            this.y = Math.max(4, Math.min(top, screenHeight - popupHeight - 4));
        }

        private int height() {
            return PADDING * 2 + ROW_HEIGHT * (options.size() + 1);
        }

        private void draw(int mouseX, int mouseY) {
            Minecraft mc = Minecraft.getMinecraft();
            int height = height();
            drawTooltipBackground(height);
            int rowY = y + PADDING;
            String title = TextFormatting.UNDERLINE + trimToWidth(mc, title(), width - PADDING * 2);
            mc.fontRenderer.drawStringWithShadow(title, x + PADDING, rowY + 2, MTStyle.Flat.TEXT_WARN);
            rowY += ROW_HEIGHT;

            for (int i = 0; i < options.size(); i++) {
                PopupOption option = options.get(i);
                if (isOption(mouseX, mouseY, i)) {
                    Gui.drawRect(x + 2, rowY, x + width - 2, rowY + ROW_HEIGHT, MTStyle.Flat.CONTENT_AREA_HOVER);
                }
                mc.fontRenderer.drawStringWithShadow(trimToWidth(mc, option.label, width - PADDING * 2), x + PADDING, rowY + 2, option.color);
                rowY += ROW_HEIGHT;
            }
        }

        private void mouseClicked(int mouseX, int mouseY) {
            for (int i = 0; i < options.size(); i++) {
                if (isOption(mouseX, mouseY, i)) {
                    options.get(i).action.run();
                    break;
                }
            }
            chatActionPopup = null;
            refocusChatInput();
        }

        private boolean isOption(int mouseX, int mouseY, int index) {
            int oy = y + PADDING + ROW_HEIGHT + index * ROW_HEIGHT;
            return mouseX >= x + 2 && mouseX < x + width - 2 && mouseY >= oy && mouseY < oy + ROW_HEIGHT;
        }

        private void drawTooltipBackground(int height) {
            Gui.drawRect(x, y, x + width, y + height, 0xF0101010);
            Gui.drawRect(x, y, x + width, y + 1, 0x88505050);
            Gui.drawRect(x + width - 1, y, x + width, y + height, 0x55202020);
            Gui.drawRect(x, y + height - 1, x + width, y + height, 0x55202020);
            Gui.drawRect(x, y, x + 1, y + height, 0x88505050);
        }

        private String title() {
            if (message != null && message.sender != null) {
                String name = MineTogetherChat.displayName(message.sender);
                if (name != null && !name.trim().isEmpty()) return name;
            }
            return I18n.format("minetogether.gui.chat.message");
        }
    }

    private static class PopupOption {
        private final String label;
        private final int color;
        private final Runnable action;

        private PopupOption(String label, int color, Runnable action) {
            this.label = label;
            this.color = color;
            this.action = action;
        }
    }

    private void refocusChatInput() {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (!(screen instanceof GuiChat)) return;
        try {
            GuiTextField input = (GuiTextField) CHAT_INPUT_FIELD.get(screen);
            input.setFocused(true);
        } catch (IllegalAccessException ignored) {
        }
    }

    private void scanRemoteCosmetics(Minecraft mc) {
        if (mc.world == null || mc.player == null) return;
        cosmeticScanTicks++;
        if (cosmeticScanTicks < 20) return;
        cosmeticScanTicks = 0;

        for (EntityPlayer player : mc.world.playerEntities) {
            if (player == null || player == mc.player) continue;
            if (PlayerCosmeticCache.get(player.getUniqueID()) != null) continue;
            CosmeticApiClient.fetchProfileForPlayerAsync(player.getUniqueID());
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

    private static class ChatLayout {
        private final int x;
        private final int vanillaY;
        private final int vanillaHeight;
        private final int publicY;
        private final int publicHeight;
        private final int groupY;
        private final int groupHeight;
        private final int settingsY;

        private ChatLayout(int x, int vanillaY, int vanillaHeight, int publicY, int publicHeight, int groupY, int groupHeight, int settingsY) {
            this.x = x;
            this.vanillaY = vanillaY;
            this.vanillaHeight = vanillaHeight;
            this.publicY = publicY;
            this.publicHeight = publicHeight;
            this.groupY = groupY;
            this.groupHeight = groupHeight;
            this.settingsY = settingsY;
        }
    }

    private static class TrimmingButton extends GuiButton {
        private TrimmingButton(int buttonId, int x, int y, int width, int height, String label) {
            super(buttonId, x, y, width, height, label);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            String original = displayString;
            displayString = trimToWidth(mc, original, width - 8);
            try {
                super.drawButton(mc, mouseX, mouseY, partialTicks);
            } finally {
                displayString = original;
            }
        }
    }

    private static class ChatTargetButton extends GuiButton {
        private final ChatTarget target;
        private final Supplier<String> label;

        private ChatTargetButton(int buttonId, int x, int y, int width, int height, Supplier<String> label, ChatTarget target) {
            super(buttonId, x, y, width, height, "");
            this.label = label;
            this.target = target;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!(mc.currentScreen instanceof GuiChat)) {
                visible = false;
                return;
            }
            if (!visible) return;
            displayString = label.get();
            hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            boolean selected = MineTogetherChat.getTarget() == target;
            boolean highlighted = hovered || selected;
            Gui.drawRect(x, y, x + width, y + height, highlighted ? 0x80000000 : 0x64202020);
            drawVerticalLabel(mc, highlighted ? 0xFFFFA0 : 0xFFFFFF);
        }

        private void drawVerticalLabel(Minecraft mc, int color) {
            String label = displayString;
            int textWidth = mc.fontRenderer.getStringWidth(label);
            if (textWidth <= 0) return;
            float scale = 1.0F;
            float lineHeight = mc.fontRenderer.FONT_HEIGHT;
            float scaledHeight = lineHeight * scale;
            float scaledWidth = textWidth * scale;
            int autoWidth = height - 6;
            if (autoWidth > 0 && scaledWidth > autoWidth) {
                scale *= autoWidth / scaledWidth;
                scaledHeight = lineHeight * scale;
                scaledWidth = textWidth * scale;
            }

            GlStateManager.pushMatrix();
            GlStateManager.translate(
                    x + scaledHeight + (width / 2.0F) - (scaledHeight / 2.0F),
                    y + (height / 2.0F) - (scaledWidth / 2.0F),
                    0.0F);
            GlStateManager.rotate(90.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.scale(scale, scale, 1.0F);
            mc.fontRenderer.drawString(label, 0, 0, color);
            GlStateManager.popMatrix();
        }
    }

    private static class CompactChatSlider extends GuiButton {
        private final GameSettings.Options option;
        private boolean dragging;

        private CompactChatSlider(int buttonId, int x, int y, int width, GameSettings.Options option) {
            super(buttonId, x, y, width, 7, "");
            this.option = option;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!(mc.currentScreen instanceof GuiChat)) {
                visible = false;
                return;
            }
            if (dragging) {
                setFromMouse(mc, mouseX);
            }
            hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            Gui.drawRect(x, y, x + width, y + height, hovered || dragging ? 0x60202020 : 0x40202020);
            Gui.drawRect(x + 3, y + height - 2, x + width - 3, y + height - 1, 0x80505050);

            float value = option.normalizeValue(mc.gameSettings.getOptionFloatValue(option));
            int knob = x + 3 + Math.round(value * Math.max(1, width - 6));
            Gui.drawRect(knob - 1, y + 1, knob + 2, y + height - 1, hovered || dragging ? 0xFFFFFFFF : 0xCCFFFFFF);

            String label = trimToWidth(mc, compactLabel(mc.gameSettings.getKeyBinding(option)), width - 10);
            float scale = 0.55F;
            int labelWidth = mc.fontRenderer.getStringWidth(label);
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + (width / 2.0F) - (labelWidth * scale / 2.0F), y + 1.0F, 0.0F);
            GlStateManager.scale(scale, scale, 1.0F);
            mc.fontRenderer.drawString(label, 0, 0, MTStyle.Flat.TEXT);
            GlStateManager.popMatrix();
        }

        private String compactLabel(String label) {
            if (option == GameSettings.Options.CHAT_HEIGHT_FOCUSED) {
                int separator = label.indexOf(':');
                if (separator >= 0) {
                    return I18n.format("minetogether:ingame.chat.slider.height") + label.substring(separator);
                }
            }
            return label;
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (!super.mousePressed(mc, mouseX, mouseY)) return false;
            dragging = true;
            setFromMouse(mc, mouseX);
            return true;
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            dragging = false;
            Minecraft.getMinecraft().gameSettings.saveOptions();
        }

        private void setFromMouse(Minecraft mc, int mouseX) {
            float normalized = MathHelper.clamp((mouseX - (x + 3)) / (float) Math.max(1, width - 6), 0.0F, 1.0F);
            if (option == GameSettings.Options.CHAT_HEIGHT_FOCUSED) {
                normalized = Math.max(normalized, minFocusedChatHeightValue(mc));
            }
            mc.gameSettings.setOptionFloatValue(option, option.denormalizeValue(normalized));
        }
    }
}
