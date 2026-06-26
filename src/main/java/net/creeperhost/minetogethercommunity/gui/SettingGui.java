package net.creeperhost.minetogethercommunity.gui;

import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogether.lib.chat.profile.ProfileManager;
import net.creeperhost.minetogethercommunity.activity.ActivityTelemetry;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.config.Config;
import net.creeperhost.minetogethercommunity.config.LocalConfig;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticsGui;
import net.creeperhost.minetogethercommunity.gui.chat.FriendChatGui;
import net.creeperhost.minetogethercommunity.gui.chat.MTStyle;
import net.creeperhost.minetogethercommunity.modulargui.GuiButton;
import net.creeperhost.minetogethercommunity.modulargui.GuiElement;
import net.creeperhost.minetogethercommunity.modulargui.GuiProvider;
import net.creeperhost.minetogethercommunity.modulargui.GuiRectangle;
import net.creeperhost.minetogethercommunity.modulargui.GuiText;
import net.creeperhost.minetogethercommunity.modulargui.ModularGui;
import net.creeperhost.minetogethercommunity.modulargui.ModularGuiScreen;
import net.creeperhost.minetogethercommunity.oauth.KeycloakOAuth;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SettingGui implements GuiProvider {

    private static final int PANEL_WIDTH = 310;
    private static final int BLOCKED_PANEL_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 14;
    private static final int ROW_GAP = 4;
    private static final int BLOCKED_PANEL_GAP = 5;

    private boolean showBlocked;
    private GuiButton profileVisibilityButton;
    private GuiButton linkAccountButton;

    @Override
    public GuiElement<?> createRootElement(final ModularGui gui) {
        gui.setPauseScreen(true);
        gui.renderScreenBackground(false);
        gui.setGuiTitle(new TextComponentString(I18n.format("minetogether.gui.settings.title")));

        int screenWidth = gui.getScreen().width;
        int screenHeight = gui.getScreen().height;
        GuiElement<?> root = new GuiElement<>(gui);
        new GuiRectangle(root, MTStyle.Flat.BACKGROUND).setBounds(0, 0, screenWidth, screenHeight);

        new GuiText(root, () -> I18n.format("minetogether.gui.settings.title"))
                .centered()
                .setBounds(10, 10, screenWidth - 20, 8);

        int panelLeft = buttonPanelLeft(screenWidth);
        int panelTop = screenHeight / 2 - 67;
        int halfWidth = (PANEL_WIDTH - 4) / 2;
        int rightLeft = panelLeft + halfWidth + 4;

        int y = panelTop;
        addToggle(root, panelLeft, y, halfWidth, "minetogether.gui.settings.button.chat", () -> LocalConfig.instance().chatEnabled, value -> {
            LocalConfig.instance().chatEnabled = value;
            LocalConfig.save();
            if (value) MineTogetherChat.enableChat();
            else MineTogetherChat.disableChat();
        });
        addToggle(root, rightLeft, y, halfWidth, "minetogether.gui.settings.button.menu_buttons", () -> LocalConfig.instance().mainMenuButtons, value -> {
            LocalConfig.instance().mainMenuButtons = value;
            LocalConfig.save();
        });

        y += BUTTON_HEIGHT + ROW_GAP;
        addToggle(root, panelLeft, y, halfWidth, "minetogether.gui.settings.button.pause_buttons", () -> Config.instance().pauseScreenButtons, value -> {
            Config.instance().pauseScreenButtons = value;
            Config.save();
        });
        addToggle(root, rightLeft, y, halfWidth, "minetogether.gui.settings.button.friend_toasts", () -> LocalConfig.instance().friendNotifications, value -> {
            LocalConfig.instance().friendNotifications = value;
            LocalConfig.save();
        });

        y += BUTTON_HEIGHT + ROW_GAP;
        addToggle(root, panelLeft, y, halfWidth, "minetogether.gui.settings.button.chat_sliders", () -> LocalConfig.instance().chatSettingsSliders, value -> {
            LocalConfig.instance().chatSettingsSliders = value;
            LocalConfig.save();
        });
        addToggle(root, rightLeft, y, halfWidth, "minetogether.gui.settings.button.shift_click_mention", () -> LocalConfig.instance().shiftClickMention, value -> {
            LocalConfig.instance().shiftClickMention = value;
            LocalConfig.save();
        });

        y += BUTTON_HEIGHT + ROW_GAP;
        addToggle(root, panelLeft, y, halfWidth, "minetogether.gui.settings.button.activity_telemetry", () -> LocalConfig.instance().activityTelemetry, value -> {
            LocalConfig.instance().activityTelemetry = value;
            LocalConfig.save();
            ActivityTelemetry.setTelemetryPreference(value);
        });
        profileVisibilityButton = new GuiButton(root, () -> I18n.format("minetogether.gui.settings.button.profile_visibility") + visibilityLabel())
                .setBounds(rightLeft, y, halfWidth, BUTTON_HEIGHT)
                .setEnabled(hasAccount() && !ActivityTelemetry.isProfileVisibilityBusy())
                .onPress(() -> ActivityTelemetry.setProfileVisibility(nextVisibility(ActivityTelemetry.getProfileVisibility())));

        y += BUTTON_HEIGHT + ROW_GAP;
        new GuiButton(root, () -> I18n.format("minetogether.gui.settings.button.blocked"))
                .setBounds(panelLeft, y, halfWidth, BUTTON_HEIGHT)
                .setToggleMode(() -> showBlocked)
                .onPress(() -> {
                    showBlocked = !showBlocked;
                    gui.getScreen().initGui();
                });
        linkAccountButton = new GuiButton(root, () -> I18n.format("minetogether.gui.settings.button.link"))
                .setBounds(rightLeft, y, halfWidth, BUTTON_HEIGHT)
                .setEnabled(!hasAccount())
                .onPress(KeycloakOAuth::start);

        y += BUTTON_HEIGHT + ROW_GAP;
        new GuiButton(root, () -> I18n.format("minetogether.gui.settings.button.profile"))
                .setBounds(panelLeft, y, halfWidth, BUTTON_HEIGHT)
                .onPress(() -> gui.mc().displayGuiScreen(new ProfileGui.Screen(gui.getScreen())));
        new GuiButton(root, () -> I18n.format("minetogether.gui.settings.button.cosmetics"))
                .setBounds(rightLeft, y, halfWidth, BUTTON_HEIGHT)
                .setEnabled(() -> gui.mc().thePlayer != null)
                .onPress(() -> gui.mc().displayGuiScreen(new CosmeticsGui.Screen(gui.getScreen())));

        y += BUTTON_HEIGHT + 16;
        new GuiButton(root, () -> I18n.format("minetogether.gui.button.back"))
                .setBounds(panelLeft, y, PANEL_WIDTH, BUTTON_HEIGHT)
                .onPress(() -> gui.mc().displayGuiScreen(gui.getParentScreen()));

        if (showBlocked) {
            int blockedLeft = panelLeft + PANEL_WIDTH + BLOCKED_PANEL_GAP;
            new GuiRectangle(root, MTStyle.Flat.CONTENT_AREA).setBounds(blockedLeft, panelTop, BLOCKED_PANEL_WIDTH, y + BUTTON_HEIGHT - panelTop);
            new GuiText(root, () -> I18n.format("minetogether.gui.settings.button.blocked"))
                    .centered()
                    .setBounds(blockedLeft, panelTop - 12, BLOCKED_PANEL_WIDTH, 8);
            new BlockedList(root).setBounds(blockedLeft + 5, panelTop + 5, BLOCKED_PANEL_WIDTH - 10, y + BUTTON_HEIGHT - panelTop - 10);
        }

        return root;
    }

    @Override
    public void tick(ModularGui gui) {
        if (profileVisibilityButton != null) {
            profileVisibilityButton.setEnabled(hasAccount() && !ActivityTelemetry.isProfileVisibilityBusy());
        }
        if (linkAccountButton != null) {
            linkAccountButton.setEnabled(!hasAccount());
        }
    }

    private int buttonPanelLeft(int screenWidth) {
        if (!showBlocked) return screenWidth / 2 - PANEL_WIDTH / 2;
        int totalWidth = PANEL_WIDTH + BLOCKED_PANEL_GAP + BLOCKED_PANEL_WIDTH;
        return Math.max(5, screenWidth / 2 - totalWidth / 2);
    }

    private void addToggle(GuiElement<?> root, int left, int top, int width, String labelKey, BoolSupplier getter, BoolConsumer setter) {
        new GuiButton(root, () -> I18n.format(labelKey) + state(getter.get()))
                .setBounds(left, top, width, BUTTON_HEIGHT)
                .onPress(() -> setter.accept(!getter.get()));
    }

    private String state(boolean state) {
        return state
                ? TextFormatting.GREEN + I18n.format("minetogether.gui.settings.button.enabled")
                : TextFormatting.RED + I18n.format("minetogether.gui.settings.button.disabled");
    }

    private String visibilityLabel() {
        if (ActivityTelemetry.isProfileVisibilityLoading()) {
            return TextFormatting.GRAY + I18n.format("minetogether.gui.settings.visibility.loading");
        }
        if (ActivityTelemetry.isProfileVisibilitySaving()) {
            return TextFormatting.GRAY + I18n.format("minetogether.gui.settings.visibility.saving");
        }
        String visibility = ActivityTelemetry.getProfileVisibility();
        if ("private".equals(visibility)) return TextFormatting.RED + I18n.format("minetogether.gui.settings.visibility.private");
        if ("friends".equals(visibility)) return TextFormatting.YELLOW + I18n.format("minetogether.gui.settings.visibility.friends");
        if ("friends_of_friends".equals(visibility)) return TextFormatting.AQUA + I18n.format("minetogether.gui.settings.visibility.friends_of_friends");
        return TextFormatting.GREEN + I18n.format("minetogether.gui.settings.visibility.public");
    }

    private String nextVisibility(String visibility) {
        if ("public".equals(visibility)) return "friends";
        if ("friends".equals(visibility)) return "friends_of_friends";
        if ("friends_of_friends".equals(visibility)) return "private";
        return "public";
    }

    private boolean hasAccount() {
        try {
            return MineTogetherChat.getOurProfile() != null && MineTogetherChat.getOurProfile().hasAccount();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private List<Profile> mutedProfiles() {
        List<Profile> profiles = new ArrayList<Profile>();
        if (MineTogetherChat.CHAT_STATE == null) return profiles;
        ProfileManager manager = MineTogetherChat.CHAT_STATE.profileManager;
        if (manager == null) return profiles;
        profiles.addAll(manager.getMutedProfiles());
        profiles.sort(Comparator.comparing(FriendChatGui::displayName, String.CASE_INSENSITIVE_ORDER));
        return profiles;
    }

    private class BlockedList extends GuiElement<BlockedList> {
        private static final int ROW_HEIGHT = 14;

        private BlockedList(GuiElement<?> parent) {
            super(parent);
        }

        @Override
        protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
            List<Profile> profiles = mutedProfiles();
            if (profiles.isEmpty()) {
                font().drawStringWithShadow(trim(I18n.format("minetogether.gui.muted.empty"), width - 8), x + 4, y + 3, MTStyle.Flat.TEXT_MUTED);
                return;
            }

            int rowY = y;
            for (Profile profile : profiles) {
                if (rowY + ROW_HEIGHT > y + height) break;
                boolean hover = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
                drawRect(x, rowY, x + width, rowY + ROW_HEIGHT, MTStyle.Flat.listEntryBackground(hover));
                font().drawStringWithShadow(trim(FriendChatGui.displayName(profile), width - 18), x + 4, rowY + 3, MTStyle.Flat.TEXT);
                drawCenteredString(font(), "x", x + width - 7, rowY + 3, MTStyle.Flat.TEXT_WARN);
                rowY += ROW_HEIGHT + 2;
            }
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
            if (mouseButton != 0 || !isMouseOver(mouseX, mouseY)) return false;
            List<Profile> profiles = mutedProfiles();
            int index = (mouseY - y) / (ROW_HEIGHT + 2);
            if (index >= 0 && index < profiles.size()) {
                Profile profile = profiles.get(index);
                profile.unmute();
                MineTogetherChat.localStatus("minetogether.gui.muted.unmuted", FriendChatGui.displayName(profile));
                gui.getScreen().initGui();
            }
            return true;
        }

        private String trim(String value, int maxWidth) {
            if (value == null || maxWidth <= 0) return "";
            if (font().getStringWidth(value) <= maxWidth) return value;
            int dots = font().getStringWidth("...");
            return font().trimStringToWidth(value, Math.max(1, maxWidth - dots)) + "...";
        }
    }

    private interface BoolSupplier {
        boolean get();
    }

    private interface BoolConsumer {
        void accept(boolean value);
    }

    public static class Screen extends ModularGuiScreen {
        public Screen(GuiScreen parentScreen) {
            super(new SettingGui(), parentScreen);
        }
    }
}
