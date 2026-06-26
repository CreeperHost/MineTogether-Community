package net.creeperhost.minetogethercommunity.gui.chat;

import net.creeperhost.minetogether.lib.chat.irc.IrcChannel;
import net.creeperhost.minetogether.lib.chat.irc.IrcClient;
import net.creeperhost.minetogether.lib.chat.irc.IrcState;
import net.creeperhost.minetogether.lib.chat.irc.IrcUser;
import net.creeperhost.minetogether.lib.chat.message.Message;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogether.lib.chat.profile.ProfileManager;
import net.creeperhost.minetogethercommunity.Constants;
import net.creeperhost.minetogethercommunity.chat.ChatConstants;
import net.creeperhost.minetogethercommunity.chat.ChatTarget;
import net.creeperhost.minetogethercommunity.chat.FriendChatNotifier;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.config.LocalConfig;
import net.creeperhost.minetogethercommunity.gui.PreviewElement;
import net.creeperhost.minetogethercommunity.gui.SettingGui;
import net.creeperhost.minetogethercommunity.modulargui.ContextMenu;
import net.creeperhost.minetogethercommunity.modulargui.GuiButton;
import net.creeperhost.minetogethercommunity.modulargui.GuiClip;
import net.creeperhost.minetogethercommunity.modulargui.GuiDialog;
import net.creeperhost.minetogethercommunity.modulargui.GuiElement;
import net.creeperhost.minetogethercommunity.modulargui.GuiProvider;
import net.creeperhost.minetogethercommunity.modulargui.GuiRectangle;
import net.creeperhost.minetogethercommunity.modulargui.GuiText;
import net.creeperhost.minetogethercommunity.modulargui.GuiTextField;
import net.creeperhost.minetogethercommunity.modulargui.GuiTextPrompt;
import net.creeperhost.minetogethercommunity.modulargui.GuiTexture;
import net.creeperhost.minetogethercommunity.modulargui.ModularGui;
import net.creeperhost.minetogethercommunity.modulargui.ModularGuiScreen;
import net.creeperhost.minetogethercommunity.oauth.KeycloakOAuth;
import net.creeperhost.minetogethercommunity.util.MessageFormatter;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.event.ClickEvent;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class FriendChatGui implements GuiProvider {

    private final ChatMonitor chatMonitor = new ChatMonitor();
    private GuiTextField input;
    private GuiTextField search;
    private GuiTextField friendCode;
    private GuiButton sendButton;
    private ContextMenu activeContextMenu;
    private int friendCookie = -1;

    private static Profile selected;
    private static boolean groupChat;

    @Override
    public GuiElement<?> createRootElement(final ModularGui gui) {
        gui.setPauseScreen(true);
        gui.renderScreenBackground(false);

        GuiElement<?> root = new GuiElement<>(gui);
        int screenWidth = gui.getScreen().width;
        int screenHeight = gui.getScreen().height;
        int left = 10;
        int leftWidth = 150;
        int friendListTop = 22;
        int groupTop = screenHeight - 55;
        int codeTop = screenHeight - 26;
        int inputTop = screenHeight - 26;
        int right = left + leftWidth + 4;
        int rightWidth = screenWidth - right - 10;

        new GuiRectangle(root, MTStyle.Flat.BACKGROUND).setBounds(0, 0, screenWidth, screenHeight);
        new GuiText(root, () -> chatTitle()).setBounds(right, 7, rightWidth - 38, 8);

        new GuiButton(root, () -> I18n.format("minetogether.gui.button.back_arrow"))
                .setBounds(left, friendListTop - 18, 50, 14)
                .onPress(() -> gui.mc().displayGuiScreen(gui.getParentScreen()));
        iconButton(root, screenWidth - 24, friendListTop - 18, 14, 14, Constants.GEAR_BUTTON, 12)
                .onPress(() -> gui.mc().displayGuiScreen(new SettingGui.Screen(gui.getScreen())));
        iconButton(root, screenWidth - 42, friendListTop - 18, 16, 14,
                new ResourceLocation("minetogethercommunity", "textures/gui/buttons/public_chat_light.png"), 16)
                .onPress(() -> gui.mc().displayGuiScreen(new PublicChatGui.Screen(gui.getParentScreen())));

        new GuiRectangle(root, MTStyle.Flat.CONTENT_AREA).setBounds(left + leftWidth - 90, friendListTop - 18, 90, 14);
        search = new GuiTextField(root)
                .setMaxLength(64)
                .setSuggestion(I18n.format("minetogether.gui.friends.search_suggestion"))
                .setBounds(left + leftWidth - 89, friendListTop - 15, 88, 8);
        new FriendList(root).setBounds(left, friendListTop, leftWidth, groupTop - friendListTop - 2);
        new GuiRectangle(root, MTStyle.Flat.CONTENT_AREA).setBounds(left, groupTop, leftWidth, 26);
        new GuiButton(root, () -> groupLabel())
                .setBounds(left + 2, groupTop + 4, leftWidth - 22, 18)
                .setVisible(FriendChatGui::hasGroup)
                .setToggleMode(() -> groupChat)
                .onPress(() -> selectGroupChat());
        iconButton(root, left + leftWidth - 18, groupTop + 4, 16, 18,
                new ResourceLocation("minetogethercommunity", "textures/gui/buttons/leave_group.png"), 16)
                .setVisible(FriendChatGui::hasGroup)
                .onPress(() -> leaveGroup(root));

        Profile ownProfile = MineTogetherChat.getOurProfile();
        int codeBoxLeft = left + leftWidth / 2 - 14;
        int codeBoxRight = left + leftWidth - 30;
        new GuiButton(root, () -> getFriendCode(ownProfile))
                .setBounds(left, codeTop, codeBoxLeft - left - 2, 16)
                .onPress(() -> GuiScreen.setClipboardString(getFriendCode(MineTogetherChat.getOurProfile())));
        new GuiRectangle(root, MTStyle.Flat.CONTENT_AREA).setBounds(codeBoxLeft, codeTop, codeBoxRight - codeBoxLeft, 16);
        friendCode = new GuiTextField(root)
                .setMaxLength(64)
                .setSuggestion(I18n.format("minetogether.gui.friends.code_box.suggestion"))
                .setBounds(codeBoxLeft + 1, codeTop + 3, codeBoxRight - codeBoxLeft - 2, 8);
        new GuiButton(root, () -> I18n.format("minetogether.gui.friends.code_send"))
                .setBounds(codeBoxRight + 2, inputTop, left + leftWidth - codeBoxRight - 2, 16)
                .setEnabled(() -> friendCode != null && !friendCode.getText().trim().isEmpty())
                .onPress(() -> sendFriendRequest(root));

        final ChatPanel chatPanel = new ChatPanel(root);
        chatPanel.setBounds(right, friendListTop, rightWidth, inputTop - friendListTop - 4);
        new PreviewElement(root)
                .setEnforceDomains(false)
                .setUrlProvider((mouseX, mouseY) -> hasActiveContextMenu() ? null : chatPanel.hoveredUrl(mouseX, mouseY))
                .setBounds(0, 0, screenWidth, screenHeight);
        new GuiRectangle(root, MTStyle.Flat.CONTENT_AREA).setBounds(right, inputTop, rightWidth, 16);
        input = new GuiTextField(root) {
            @Override
            public boolean keyTyped(char typedChar, int keyCode) throws IOException {
                if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) && enabled) {
                    sendCurrentMessage();
                    return true;
                }
                return super.keyTyped(typedChar, keyCode);
            }
        }.setMaxLength(256).setCanLoseFocus(false).setBounds(right + 1, inputTop + 3, rightWidth - 2, 8);
        sendButton = null;

        tickFriendList();
        updateSelected();
        return root;
    }

    @Override
    public void tick(ModularGui gui) {
        tickFriendList();
        updateSelected();
        chatMonitor.tick();
    }

    private void tickFriendList() {
        ProfileManager manager = profileManager();
        if (manager == null) {
            friendCookie = -1;
            selected = null;
            groupChat = false;
            return;
        }

        int newCookie = manager.getFriendUpdateCookie();
        if (friendCookie != newCookie) {
            friendCookie = newCookie;
            if (selected != null && !selected.isFriend()) {
                selected = null;
            }
        }
    }

    private void updateSelected() {
        boolean enabled = false;
        if (MineTogetherChat.CHAT_STATE != null) {
            IrcClient client = MineTogetherChat.CHAT_STATE.ircClient;
            IrcState state = client.getState();
            if (selected != null && state == IrcState.CONNECTED) {
                IrcUser user = client.getUser(selected);
                if (user != null) {
                    chatMonitor.attach(user.getChannel());
                    enabled = chatMonitor.getChannel() != null;
                    FriendChatNotifier.setActiveChat(selected);
                } else {
                    chatMonitor.attach(null);
                }
            } else if (groupChat && hasGroup()) {
                ProfileManager.PrivateGroup group = profileManager().getPrivateGroup();
                IrcChannel channel = group == null ? null : client.getChannel(group.channelName);
                chatMonitor.attach(channel);
                enabled = channel != null;
                FriendChatNotifier.setActiveChat(null);
            } else {
                chatMonitor.attach(null);
                FriendChatNotifier.setActiveChat(null);
            }
        } else {
            chatMonitor.attach(null);
            FriendChatNotifier.setActiveChat(null);
        }

        if (input != null) {
            input.setEnabled(enabled);
            input.setFocused(enabled);
            input.setSuggestion(enabled ? "" : friendInputSuggestion());
        }
        if (sendButton != null) sendButton.setEnabled(enabled);
    }

    private String friendInputSuggestion() {
        if (MineTogetherChat.CHAT_STATE == null) {
            return I18n.format("minetogether.screen.chat.suggestion.disconnected");
        }
        IrcState state = MineTogetherChat.CHAT_STATE.ircClient.getState();
        if (state != IrcState.CONNECTED) {
            String key = ChatConstants.STATE_SUGGESTION_LOOKUP.get(state);
            return key == null ? ChatConstants.describe(state) : I18n.format(key);
        }
        if (selected == null && !groupChat) {
            return I18n.format("minetogether.gui.friends.select_friend");
        }
        if (selected != null) {
            return I18n.format("minetogether.gui.friends.user_offline");
        }
        if (groupChat) {
            return I18n.format("minetogether.gui.friends.group.channel_waiting");
        }
        return I18n.format("minetogether.gui.friends.select_friend");
    }

    private void sendCurrentMessage() {
        if (input == null || chatMonitor.getChannel() == null) return;
        String text = input.getText().trim();
        if (text.isEmpty()) return;
        input.setText("");
        chatMonitor.getChannel().sendMessage(text);
    }

    private void sendFriendRequest(final GuiElement<?> root) {
        final String code = friendCode == null ? "" : friendCode.getText().trim();
        if (code.isEmpty()) return;
        if (MineTogetherChat.CHAT_STATE == null) return;
        if (code.equals(getFriendCode(MineTogetherChat.getOurProfile()))) {
            MineTogetherChat.localStatus("minetogether.gui.friends.error.own_code");
            return;
        }

        showTextPrompt(root,
                I18n.format("minetogether.screen.friendreq.desc.request"),
                "",
                new Consumer<String>() {
                    @Override
                    public void accept(String friendName) {
                        ProfileManager manager = MineTogetherChat.CHAT_STATE.profileManager;
                        manager.sendFriendRequest(code, friendName.trim(), success -> {
                            MineTogetherChat.localStatus(success ? "minetogether.gui.friends.request_sent" : "minetogether.gui.friends.request_fail");
                        });
                        friendCode.setText("");
                    }
                });
    }

    private String chatTitle() {
        if (selected != null) {
            return I18n.format("minetogether.gui.friends.title") + " - " + displayName(selected);
        }
        return I18n.format("minetogether.gui.friends.title");
    }

    private String groupLabel() {
        ProfileManager manager = profileManager();
        ProfileManager.PrivateGroup group = manager == null ? null : manager.getPrivateGroup();
        if (group == null) return I18n.format("minetogether.gui.friends.group.no_group");
        String label;
        if (group.isOurGroup() || group.ownerHash == null) {
            label = I18n.format("minetogether.gui.friends.group.own");
        } else {
            label = I18n.format("minetogether.gui.friends.group.user", displayName(manager.lookupProfile(group.ownerHash)));
        }
        IrcChannel channel = MineTogetherChat.CHAT_STATE == null ? null : MineTogetherChat.CHAT_STATE.ircClient.getChannel(group.channelName);
        if (channel != null) {
            label += " " + I18n.format("minetogether.gui.friends.group.user_count", channel.getUsers().size());
        }
        return groupChat ? "[" + label + "]" : label;
    }

    private static boolean hasGroup() {
        return MineTogetherChat.CHAT_STATE != null
                && MineTogetherChat.CHAT_STATE.profileManager != null
                && MineTogetherChat.CHAT_STATE.profileManager.getPrivateGroup() != null;
    }

    private static ProfileManager profileManager() {
        return MineTogetherChat.CHAT_STATE == null ? null : MineTogetherChat.CHAT_STATE.profileManager;
    }

    public static void setSelected(Profile profile) {
        selected = profile;
        groupChat = false;
        FriendChatNotifier.setActiveChat(profile);
    }

    public static Profile getSelected() {
        return selected;
    }

    public static void selectGroupChat() {
        selected = null;
        groupChat = true;
        MineTogetherChat.setTarget(ChatTarget.GROUP);
        FriendChatNotifier.setActiveChat(null);
    }

    public static String displayName(Profile profile) {
        return profile == null ? "" : MineTogetherChat.displayName(profile);
    }

    public static String getFriendCode(Profile profile) {
        return profile != null && profile.hasFriendCode() ? profile.getFriendCode() : "";
    }

    private GuiButton iconButton(GuiElement<?> root, int x, int y, int width, int height, ResourceLocation texture, int textureSize) {
        GuiButton button = new GuiButton(root, () -> "").setBounds(x, y, width, height);
        int iconSize = Math.min(textureSize, Math.min(width, height));
        int iconX = x + (width - iconSize) / 2;
        int iconY = y + (height - iconSize) / 2;
        new GuiTexture(button, texture).textureSize(textureSize, textureSize).setBounds(iconX, iconY, iconSize, iconSize);
        return button;
    }

    private static void showTextPrompt(GuiElement<?> root, String title, String initialValue, Consumer<String> callback) {
        GuiTextPrompt prompt = new GuiTextPrompt(root, title, initialValue, callback);
        prompt.init();
    }

    private static void showConfirm(GuiElement<?> root, String title, String message, final Runnable action) {
        final GuiDialog[] dialog = new GuiDialog[1];
        dialog[0] = new GuiDialog(root, () -> title, () -> message)
                .addButton(() -> I18n.format("minetogether.gui.button.ok"), () -> {
                    dialog[0].setVisible(false);
                    action.run();
                })
                .closeButton(() -> I18n.format("minetogether.gui.button.cancel"));
        dialog[0].init();
    }

    private void showActionMenu(final GuiElement<?> root, final Profile profile, int mouseX, int mouseY) {
        closeActiveContextMenu();
        ContextMenu menu = new ContextMenu(root);
        activeContextMenu = menu;
        menu.addTitle(displayName(profile));
        menu.addOption(I18n.format("minetogether.gui.friends.button.invite"), 0x55FF77, new Runnable() {
            @Override
            public void run() {
                inviteToGroup(root, profile);
            }
        });
        menu.addOption(I18n.format("minetogether.gui.friends.button.rename"), 0x55FFFF, new Runnable() {
            @Override
            public void run() {
                renameFriend(root, profile);
            }
        });
        menu.addOption(I18n.format("minetogether.gui.friends.button.remove"), 0xFFFF55, new Runnable() {
            @Override
            public void run() {
                removeFriend(profile);
            }
        });
        menu.addOption(profile.isMuted()
                ? I18n.format("minetogether.gui.chat.action.unmute")
                : I18n.format("minetogether.gui.friends.button.block"), 0xFF7070, new Runnable() {
            @Override
            public void run() {
                toggleBlock(profile);
            }
        });
        menu.position(mouseX, mouseY);
    }

    private static void leaveGroup(final GuiElement<?> root) {
        ProfileManager manager = profileManager();
        if (manager == null) return;
        final ProfileManager.PrivateGroup group = manager.getPrivateGroup();
        if (group == null) return;
        showConfirm(root,
                I18n.format(group.isOurGroup() || group.ownerHash == null
                        ? "minetogether.gui.friends.button.disband_group"
                        : "minetogether.gui.friends.button.leave_group"),
                I18n.format(group.isOurGroup() || group.ownerHash == null
                        ? "minetogether.gui.friends.group.confirm_leave_own"
                        : "minetogether.gui.friends.group.confirm_leave"),
                () -> {
                    ProfileManager latest = profileManager();
                    if (latest != null) latest.leaveGroup("leaving");
                    setSelected(null);
                });
    }

    private static void inviteToGroup(final GuiElement<?> root, final Profile profile) {
        ProfileManager manager = profileManager();
        if (manager == null) return;
        ProfileManager.PrivateGroup group = manager.getPrivateGroup();
        if (group != null && group.ownerHash != null) {
            showConfirm(root,
                    I18n.format("minetogether.gui.friends.button.invite"),
                    I18n.format("minetogether.gui.friends.group.confirm_leave"),
                    () -> {
                        selectGroupChat();
                        ProfileManager current = profileManager();
                        if (current != null) current.sendGroupInvite(profile);
                    });
            return;
        }
        selectGroupChat();
        manager.sendGroupInvite(profile);
    }

    private static void renameFriend(GuiElement<?> root, final Profile profile) {
        final ProfileManager manager = profileManager();
        if (manager == null) return;
        showTextPrompt(root,
                I18n.format("minetogether.gui.friends.button.rename"),
                displayName(profile),
                new Consumer<String>() {
                    @Override
                    public void accept(String friendName) {
                        manager.updateFriendName(profile, friendName.trim(), success -> {
                            MineTogetherChat.localStatus(success ? "minetogether.gui.friends.update_friend" : "minetogether.gui.friends.update_fail");
                        });
                    }
                });
    }

    private static void removeFriend(Profile profile) {
        ProfileManager manager = profileManager();
        if (manager == null) return;
        manager.removeFriend(profile);
        if (selected == profile) selected = null;
    }

    private static void toggleBlock(Profile profile) {
        if (profile.isMuted()) {
            profile.unmute();
            MineTogetherChat.localStatus("minetogether.gui.chat.action.unmuted", displayName(profile));
        } else {
            profile.mute();
            MineTogetherChat.localStatus("minetogether.gui.chat.action.muted", displayName(profile));
            if (selected == profile) selected = null;
        }
    }

    private static class FriendList extends GuiElement<FriendList> {

        private final Map<Profile, PlayerIconElement> icons = new IdentityHashMap<>();
        private int scroll;

        private FriendList(GuiElement<?> parent) {
            super(parent);
        }

        @Override
        public void render(int mouseX, int mouseY, float partialTicks) {
            if (!visible) return;
            GuiClip.push(x, y, width, height);
            try {
                super.render(mouseX, mouseY, partialTicks);
            } finally {
                GuiClip.pop();
            }
        }

        @Override
        protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
            drawRect(x, y, x + width, y + height, MTStyle.Flat.CONTENT_AREA);
            List<Row> rows = rows();
            clampScroll(rows);
            updateIcons(rows);
            if (rows.isEmpty()) {
                font().drawStringWithShadow(I18n.format("minetogether.gui.friends.empty"), x + 5, y + 6, 0xAAAAAA);
                return;
            }

            int rowY = y + 4 - scroll;
            for (Row row : rows) {
                int rowHeight = row.height();
                if (rowY + rowHeight < y) {
                    rowY += rowHeight;
                    continue;
                }
                if (rowY > y + height - 8) break;
                if (row.divider != null) {
                    drawCenteredString(font(), row.divider, x + width / 2, rowY + 2, 0xFFFFAA);
                } else if (row.request != null) {
                    renderRequest(row.request, mouseX, mouseY, rowY);
                } else if (row.invite != null) {
                    renderInvite(row.invite, row.profile, mouseX, mouseY, rowY);
                } else if (row.profile != null) {
                    renderFriend(row.profile, mouseX, mouseY, rowY);
                }
                rowY += rowHeight;
            }
            drawScrollBar(rows, mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
            if (!isMouseOver(mouseX, mouseY)) return false;
            List<Row> rows = rows();
            clampScroll(rows);
            int rowY = y + 4 - scroll;
            for (Row row : rows) {
                int rowHeight = row.height();
                if (mouseY >= rowY && mouseY < rowY + rowHeight) {
                    if (row.request != null) {
                        handleRequestClick(row.request, mouseX, mouseY, rowY);
                    } else if (row.invite != null) {
                        handleInviteClick(row.invite, rowY, mouseX, mouseY);
                    } else if (row.profile != null) {
                        if (mouseButton == GuiButton.LEFT_CLICK) {
                            if (selected == row.profile) {
                                FriendChatGui provider = getProvider();
                                if (provider != null) provider.showActionMenu(getModularGui().getRoot(), row.profile, mouseX, mouseY);
                            } else {
                                setSelected(row.profile);
                            }
                        } else if (mouseButton == GuiButton.RIGHT_CLICK) {
                            FriendChatGui provider = getProvider();
                            if (provider != null) provider.showActionMenu(getModularGui().getRoot(), row.profile, mouseX, mouseY);
                        }
                    }
                    return true;
                }
                rowY += rowHeight;
            }
            return true;
        }

        @Override
        public boolean mouseInput(int mouseX, int mouseY, int dWheel) throws IOException {
            if (!isMouseOver(mouseX, mouseY)) return false;
            int max = maxScroll(rows());
            if (max <= 0) return false;
            scroll += dWheel < 0 ? 20 : -20;
            if (scroll < 0) scroll = 0;
            if (scroll > max) scroll = max;
            return true;
        }

        private void renderFriend(Profile profile, int mouseX, int mouseY, int rowY) {
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + 28;
            drawRect(x + 2, rowY, x + width - 2, rowY + 28, MTStyle.Flat.listEntryBackground(selected == profile || hover));
            int unread = FriendChatNotifier.getUnreadMessageCount(profile);
            int badgeWidth = 0;
            if (unread > 0) {
                String value = String.valueOf(unread);
                badgeWidth = Math.max(12, font().getStringWidth(value) + 4);
                drawRect(x + width - badgeWidth - 6, rowY + 15, x + width - 6, rowY + 25, 0xDDCC3030);
                drawCenteredString(font(), value, x + width - badgeWidth / 2 - 6, rowY + 16, 0xFFFFFF);
            }
            int textWidth = Math.max(12, width - 39 - (unread > 0 ? badgeWidth + 4 : 0));
            font().drawStringWithShadow(trimRowText(displayName(profile), textWidth), x + 31, rowY + 5, profile.isOnline() ? 0xFFFFFF : 0xAAAAAA);
            font().drawStringWithShadow(profile.isOnline() ? I18n.format("minetogether.gui.friends.online") : I18n.format("minetogether.gui.friends.offline"),
                    x + 31, rowY + 17, profile.isOnline() ? 0x55FF77 : 0x888888);
        }

        private void renderRequest(ProfileManager.FriendRequest request, int mouseX, int mouseY, int rowY) {
            drawRect(x + 2, rowY, x + width - 2, rowY + 30, 0x66384020);
            font().drawStringWithShadow(trimRowText(I18n.format("minetogether.gui.friends.request", displayName(request.user)), width - 39), x + 31, rowY + 4, 0xFFFFAA);
            drawAction(mouseX, mouseY, rowY + 16, 0, I18n.format("minetogether.gui.friends.accept"));
            drawAction(mouseX, mouseY, rowY + 16, 1, I18n.format("minetogether.gui.friends.deny"));
        }

        private void renderInvite(ProfileManager.PrivateGroup invite, Profile sender, int mouseX, int mouseY, int rowY) {
            drawRect(x + 2, rowY, x + width - 2, rowY + 30, 0x66384020);
            font().drawStringWithShadow(trimRowText(I18n.format("minetogether.gui.friends.group_invite", displayName(sender)), width - 39), x + 31, rowY + 4, 0xFFFFAA);
            drawAction(mouseX, mouseY, rowY + 16, 0, I18n.format("minetogether.gui.friends.join_group"));
            drawAction(mouseX, mouseY, rowY + 16, 1, I18n.format("minetogether.gui.friends.deny"));
        }

        private String trimRowText(String value, int maxWidth) {
            if (value == null) return "";
            if (maxWidth <= 0 || font().getStringWidth(value) <= maxWidth) return value;
            int dots = font().getStringWidth("...");
            return font().trimStringToWidth(value, Math.max(1, maxWidth - dots)) + "...";
        }

        private void handleRequestClick(final ProfileManager.FriendRequest request, int mouseX, int mouseY, int rowY) {
            if (isAction(mouseX, mouseY, rowY + 16, 0)) {
                showTextPrompt(getModularGui().getRoot(),
                        I18n.format("minetogether.gui.friends.request_name"),
                        request.desiredName == null ? displayName(request.user) : request.desiredName,
                        new Consumer<String>() {
                            @Override
                            public void accept(String friendName) {
                                ProfileManager manager = profileManager();
                                if (manager != null) manager.acceptFriendRequest(request, friendName.trim());
                            }
                        });
            } else if (isAction(mouseX, mouseY, rowY + 16, 1)) {
                ProfileManager manager = profileManager();
                if (manager != null) manager.denyFriendRequest(request);
            }
        }

        private void handleInviteClick(final ProfileManager.PrivateGroup invite, int rowY, int mouseX, int mouseY) {
            if (isAction(mouseX, mouseY, rowY + 16, 0)) {
                ProfileManager manager = profileManager();
                if (manager == null) return;
                ProfileManager.PrivateGroup current = manager.getPrivateGroup();
                if (current == null) {
                    manager.acceptGroupInvite(invite);
                    selectGroupChat();
                } else {
                    showConfirm(getModularGui().getRoot(),
                            I18n.format("minetogether.gui.friends.join_group"),
                            I18n.format(current.ownerHash == null ? "minetogether.gui.friends.group.confirm_leave_own" : "minetogether.gui.friends.group.confirm_leave"),
                            () -> {
                                ProfileManager latest = profileManager();
                                if (latest != null) latest.acceptGroupInvite(invite);
                                selectGroupChat();
                            });
                }
            } else if (isAction(mouseX, mouseY, rowY + 16, 1)) {
                ProfileManager manager = profileManager();
                if (manager != null) manager.rejectGroupInvite(invite);
            }
        }

        private void drawAction(int mouseX, int mouseY, int rowY, int slot, String label) {
            int buttonWidth = 42;
            int bx = x + width - 94 + slot * 46;
            int by = rowY;
            drawRect(bx, by, bx + buttonWidth, by + 12, isAction(mouseX, mouseY, rowY, slot) ? MTStyle.Flat.BUTTON_HOVER : MTStyle.Flat.BUTTON);
            drawCenteredString(font(), label, bx + buttonWidth / 2, by + 2, MTStyle.Flat.TEXT);
        }

        private boolean isAction(int mouseX, int mouseY, int rowY, int slot) {
            int buttonWidth = 42;
            int bx = x + width - 94 + slot * 46;
            int by = rowY;
            return mouseX >= bx && mouseX < bx + buttonWidth && mouseY >= by && mouseY < by + 12;
        }

        private List<Row> rows() {
            List<Row> rows = new ArrayList<>();
            ProfileManager manager = profileManager();
            if (manager == null) return rows;

            String filter = "";
            FriendChatGui provider = getProvider();
            if (provider != null && provider.search != null) {
                filter = provider.search.getText().trim().toLowerCase(Locale.ROOT);
            }

            List<Profile> friends = new ArrayList<>();
            for (Profile profile : manager.getKnownProfiles()) {
                if (profile.isFriend()) friends.add(profile);
            }
            friends.sort(new Comparator<Profile>() {
                @Override
                public int compare(Profile a, Profile b) {
                    if (a.isOnline() != b.isOnline()) return a.isOnline() ? -1 : 1;
                    return String.CASE_INSENSITIVE_ORDER.compare(displayName(a), displayName(b));
                }
            });

            for (Profile friend : friends) {
                if (!filter.isEmpty() && !displayName(friend).toLowerCase(Locale.ROOT).contains(filter)) continue;
                rows.add(Row.friend(friend));
            }

            List<ProfileManager.FriendRequest> requests = manager.getFriendRequests();
            if (!requests.isEmpty()) {
                rows.add(Row.divider(I18n.format("minetogether.gui.friends.requests")));
                for (ProfileManager.FriendRequest request : requests) {
                    if (!filter.isEmpty() && !displayName(request.user).toLowerCase(Locale.ROOT).contains(filter)) continue;
                    rows.add(Row.request(request));
                }
            }

            List<ProfileManager.PrivateGroup> invites = manager.getGroupInvites();
            if (!invites.isEmpty()) {
                rows.add(Row.divider(I18n.format("minetogether.gui.friends.group.invites")));
                for (ProfileManager.PrivateGroup invite : invites) {
                    Profile sender = manager.lookupProfile(invite.ownerHash);
                    if (!filter.isEmpty() && !displayName(sender).toLowerCase(Locale.ROOT).contains(filter)) continue;
                    rows.add(Row.invite(invite, sender));
                }
            }
            return rows;
        }

        private void updateIcons(List<Row> rows) {
            for (PlayerIconElement icon : icons.values()) {
                icon.setVisible(false);
            }
            int rowY = y + 4 - scroll;
            for (Row row : rows) {
                int rowHeight = row.height();
                if (row.profile != null && row.divider == null && rowY + rowHeight >= y && rowY <= y + height) {
                    PlayerIconElement icon = icons.get(row.profile);
                    if (icon == null) {
                        icon = new PlayerIconElement(this, row.profile);
                        icons.put(row.profile, icon);
                        icon.init();
                    }
                    icon.setBounds(x + 2, rowY + 2, 28, 28).setVisible(true);
                }
                rowY += rowHeight;
            }
        }

        private void clampScroll(List<Row> rows) {
            int max = maxScroll(rows);
            if (scroll > max) scroll = max;
            if (scroll < 0) scroll = 0;
        }

        private int maxScroll(List<Row> rows) {
            int total = 8;
            for (Row row : rows) {
                total += row.height();
            }
            return Math.max(0, total - height);
        }

        private void drawScrollBar(List<Row> rows, int mouseX, int mouseY) {
            int total = 8;
            for (Row row : rows) {
                total += row.height();
            }
            if (total <= height) return;
            boolean hover = mouseX >= x + width - 6 && mouseX < x + width - 2 && mouseY >= y + 2 && mouseY < y + height - 2;
            MTStyle.Flat.drawVerticalScrollBar(x + width - 6, y + 2, 4, height - 4, total, height, scroll, hover);
        }

        private FriendChatGui getProvider() {
            return getModularGui().getProvider() instanceof FriendChatGui ? (FriendChatGui) getModularGui().getProvider() : null;
        }
    }

    private static class ChatPanel extends GuiElement<ChatPanel> {

        private int scrollFromBottom;

        private ChatPanel(GuiElement<?> parent) {
            super(parent);
        }

        @Override
        public void render(int mouseX, int mouseY, float partialTicks) {
            if (!visible) return;
            GuiClip.push(x, y, width, height);
            try {
                super.render(mouseX, mouseY, partialTicks);
            } finally {
                GuiClip.pop();
            }
        }

        @Override
        protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
            drawRect(x, y, x + width, y + height, MTStyle.Flat.CONTENT_AREA);
            FriendChatGui provider = getProvider();
            if (provider == null) return;

            if (provider.chatMonitor.getChannel() == null) {
                String status = statusText();
                drawCenteredString(font(), status, x + width / 2, y + height / 2 - 4, 0xAAAAAA);
                return;
            }

            List<ChatMessageLines.Line> lines = visibleLines(provider);
            int lineY = y + Math.max(4, height - lines.size() * 10 - 4);
            for (ChatMessageLines.Line line : lines) {
                font().drawStringWithShadow(line.text, x + 5, lineY, 0xE6E6E6);
                lineY += 10;
            }
            drawScrollBar(provider, mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
            if ((mouseButton != GuiButton.LEFT_CLICK && mouseButton != GuiButton.RIGHT_CLICK) || !isMouseOver(mouseX, mouseY)) return false;
            FriendChatGui provider = getProvider();
            if (provider == null || provider.chatMonitor.getChannel() == null) return true;
            List<ChatMessageLines.Line> lines = visibleLines(provider);
            int lineY = y + Math.max(4, height - lines.size() * 10 - 4);
            for (ChatMessageLines.Line line : lines) {
                Message message = line.message;
                if (mouseY >= lineY - 1 && mouseY < lineY + 10) {
                    int localX = mouseX - x - 5;
                    Style style = ChatMessageLines.styleAt(font(), line, localX);
                    URL clickedUrl = urlFromStyle(style);
                    if (clickedUrl == null) {
                        clickedUrl = ChatMessageLines.urlAt(font(), line, localX);
                    }
                    if (mouseButton == GuiButton.RIGHT_CLICK) {
                        openContextMenu(provider, message, clickedUrl, mouseX, mouseY);
                        return true;
                    }
                    if (clickedUrl != null) {
                        if (!KeycloakOAuth.openURL(clickedUrl)) {
                            MineTogetherChat.localStatus("minetogether.gui.chat.action.open_failed");
                        }
                        return true;
                    }
                    if (isNameClick(style) && isActionable(message)) {
                        if (LocalConfig.instance().shiftClickMention && GuiScreen.isShiftKeyDown()) {
                            mention(provider, message.sender);
                        } else {
                            openContextMenu(provider, message, null, mouseX, mouseY);
                        }
                        return true;
                    }
                }
                lineY += 10;
            }
            return true;
        }

        private boolean openContextMenu(final FriendChatGui provider, final Message message, final URL url, int mouseX, int mouseY) {
            if (url == null && !isActionable(message)) return false;
            provider.closeActiveContextMenu();
            ContextMenu menu = new ContextMenu(getModularGui().getRoot());
            provider.activeContextMenu = menu;
            menu.addTitle(contextTitle(message));
            if (url != null) {
                menu.addOption(I18n.format("minetogether.gui.chat.action.open_link"), 0x55AAFF, new Runnable() {
                    @Override
                    public void run() {
                        if (!KeycloakOAuth.openURL(url)) {
                            MineTogetherChat.localStatus("minetogether.gui.chat.action.open_failed");
                        }
                    }
                });
            }
            if (isActionable(message)) {
                menu.addOption(I18n.format("minetogether.gui.chat.action.mention"), 0x55FFFF, new Runnable() {
                    @Override
                    public void run() {
                        mention(provider, message.sender);
                    }
                });
                if (!message.sender.isFriend() && message.sender.hasFriendCode()) {
                    menu.addOption(I18n.format("minetogether.gui.chat.action.friend"), 0x55FFFF, new Runnable() {
                        @Override
                        public void run() {
                            sendFriendRequestPrompt(message.sender);
                        }
                    });
                }
                menu.addOption(message.sender.isMuted()
                        ? I18n.format("minetogether.gui.chat.action.unmute")
                        : I18n.format("minetogether.gui.chat.action.mute"), 0xFF7070, new Runnable() {
                    @Override
                    public void run() {
                        toggleMute(message.sender);
                    }
                });
            }
            menu.position(mouseX, mouseY);
            return true;
        }

        private String contextTitle(Message message) {
            if (message == null || message.sender == null) {
                return I18n.format("minetogether.gui.chat.message");
            }
            String name = MineTogetherChat.displayName(message.sender);
            return name == null || name.isEmpty() ? I18n.format("minetogether.gui.chat.message") : name;
        }

        private boolean isActionable(Message message) {
            return message != null && message.sender != null && message.sender != MineTogetherChat.getOurProfile();
        }

        private boolean isNameClick(Style style) {
            ClickEvent event = style == null ? null : style.getClickEvent();
            return event != null && event.getValue() != null
                    && (MessageFormatter.CLICK_NAME.equals(event.getValue())
                    || event.getValue().startsWith(MessageFormatter.CLICK_NAME + ":"));
        }

        private URL urlFromStyle(Style style) {
            ClickEvent event = style == null ? null : style.getClickEvent();
            if (event == null || event.getAction() != ClickEvent.Action.OPEN_URL || event.getValue() == null) {
                return null;
            }
            try {
                return new URL(event.getValue());
            } catch (java.net.MalformedURLException ignored) {
                return null;
            }
        }

        private void sendFriendRequestPrompt(final Profile profile) {
            if (MineTogetherChat.CHAT_STATE == null) return;
            if (!profile.hasFriendCode()) {
                MineTogetherChat.localStatus("minetogether.gui.chat.action.friend_missing_code");
                return;
            }
            new GuiTextPrompt(getModularGui().getRoot(),
                    I18n.format("minetogether.gui.friends.request_name"),
                    MineTogetherChat.displayName(profile),
                    new Consumer<String>() {
                        @Override
                        public void accept(String friendName) {
                            ProfileManager manager = profileManager();
                            if (manager == null) return;
                            manager.sendFriendRequest(profile.getFriendCode(), friendName.trim(), success -> {
                                MineTogetherChat.localStatus(success ? "minetogether.gui.friends.request_sent" : "minetogether.gui.friends.request_fail");
                            });
                        }
                    }).init();
        }

        private void mention(FriendChatGui provider, Profile profile) {
            if (provider.input == null || profile == null) return;
            String value = provider.input.getText();
            if (!value.isEmpty() && value.charAt(value.length() - 1) != ' ') {
                value += " ";
            }
            provider.input.setText(value + MineTogetherChat.displayName(profile));
        }

        private void toggleMute(Profile profile) {
            if (profile == null) return;
            if (profile.isMuted()) {
                profile.unmute();
                MineTogetherChat.localStatus("minetogether.gui.chat.action.unmuted", displayName(profile));
            } else {
                profile.mute();
                MineTogetherChat.localStatus("minetogether.gui.chat.action.muted", displayName(profile));
            }
        }

        @Override
        public boolean mouseInput(int mouseX, int mouseY, int dWheel) throws IOException {
            if (!isMouseOver(mouseX, mouseY)) return false;
            FriendChatGui provider = getProvider();
            if (provider == null) return false;
            int maxLines = Math.max(1, (height - 8) / 10);
            int maxScroll = Math.max(0, ChatMessageLines.build(font(), provider.chatMonitor.getMessages(), width - 10).size() - maxLines);
            if (maxScroll <= 0) return false;
            scrollFromBottom += dWheel < 0 ? -3 : 3;
            if (scrollFromBottom < 0) scrollFromBottom = 0;
            if (scrollFromBottom > maxScroll) scrollFromBottom = maxScroll;
            return true;
        }

        private String statusText() {
            if (MineTogetherChat.CHAT_STATE == null) return I18n.format("minetogether.gui.chat.disconnected");
            IrcState state = MineTogetherChat.CHAT_STATE.ircClient.getState();
            if (state != IrcState.CONNECTED) return ChatConstants.describe(state);
            if (selected != null) return I18n.format("minetogether.gui.friends.user_offline");
            if (groupChat) return I18n.format("minetogether.gui.friends.group.channel_waiting");
            return I18n.format("minetogether.gui.friends.select_friend");
        }

        private List<ChatMessageLines.Line> visibleLines(FriendChatGui provider) {
            List<ChatMessageLines.Line> all = ChatMessageLines.build(font(), provider.chatMonitor.getMessages(), width - 10);
            int maxLines = Math.max(1, (height - 8) / 10);
            int maxScroll = Math.max(0, all.size() - maxLines);
            if (scrollFromBottom > maxScroll) scrollFromBottom = maxScroll;
            int start = Math.max(0, all.size() - maxLines - scrollFromBottom);
            int end = Math.min(all.size(), start + maxLines);
            return new ArrayList<>(all.subList(start, end));
        }

        private void drawScrollBar(FriendChatGui provider, int mouseX, int mouseY) {
            List<ChatMessageLines.Line> all = ChatMessageLines.build(font(), provider.chatMonitor.getMessages(), width - 10);
            int maxLines = Math.max(1, (height - 8) / 10);
            int maxScroll = Math.max(0, all.size() - maxLines);
            if (maxScroll <= 0) return;
            int scrollTop = maxScroll - Math.max(0, Math.min(scrollFromBottom, maxScroll));
            boolean hover = mouseX >= x + width - 6 && mouseX < x + width - 2 && mouseY >= y + 2 && mouseY < y + height - 2;
            MTStyle.Flat.drawVerticalScrollBar(x + width - 6, y + 2, 4, height - 4, all.size(), maxLines, scrollTop, hover);
        }

        private PreviewElement.URLInfo hoveredUrl(int mouseX, int mouseY) {
            if (!isMouseOver(mouseX, mouseY)) return null;
            FriendChatGui provider = getProvider();
            if (provider == null || provider.chatMonitor.getChannel() == null) return null;
            List<ChatMessageLines.Line> lines = visibleLines(provider);
            int lineY = y + Math.max(4, height - lines.size() * 10 - 4);
            for (ChatMessageLines.Line line : lines) {
                Message message = line.message;
                if (mouseY >= lineY - 1 && mouseY < lineY + 10) {
                    URL url = ChatMessageLines.urlAt(font(), line, mouseX - x - 5);
                    return url == null ? null : new PreviewElement.URLInfo(url, message.sender == null);
                }
                lineY += 10;
            }
            return null;
        }

        private FriendChatGui getProvider() {
            return getModularGui().getProvider() instanceof FriendChatGui ? (FriendChatGui) getModularGui().getProvider() : null;
        }
    }

    private boolean hasActiveContextMenu() {
        return activeContextMenu != null && !activeContextMenu.isClosed();
    }

    private void closeActiveContextMenu() {
        if (activeContextMenu != null && !activeContextMenu.isClosed()) {
            activeContextMenu.close();
        }
        activeContextMenu = null;
    }

    private static class Row {
        private final Profile profile;
        private final ProfileManager.FriendRequest request;
        private final ProfileManager.PrivateGroup invite;
        private final String divider;

        private Row(Profile profile, ProfileManager.FriendRequest request, ProfileManager.PrivateGroup invite, String divider) {
            this.profile = profile;
            this.request = request;
            this.invite = invite;
            this.divider = divider;
        }

        private static Row friend(Profile profile) {
            return new Row(profile, null, null, null);
        }

        private static Row request(ProfileManager.FriendRequest request) {
            return new Row(request.user, request, null, null);
        }

        private static Row invite(ProfileManager.PrivateGroup invite, Profile sender) {
            return new Row(sender, null, invite, null);
        }

        private static Row divider(String divider) {
            return new Row(null, null, null, divider);
        }

        private int height() {
            return divider == null ? 32 : 14;
        }
    }

    public static class Screen extends ModularGuiScreen {
        public Screen(GuiScreen parentScreen) {
            super(new FriendChatGui(), parentScreen);
        }

        @Override
        public void onGuiClosed() {
            super.onGuiClosed();
            FriendChatGui provider = getModularGui() == null ? null : (FriendChatGui) getModularGui().getProvider();
            if (provider != null) provider.chatMonitor.close();
            FriendChatNotifier.setActiveChat(null);
        }
    }
}
