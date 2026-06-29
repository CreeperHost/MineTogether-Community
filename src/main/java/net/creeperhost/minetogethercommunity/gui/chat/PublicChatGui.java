package net.creeperhost.minetogethercommunity.gui.chat;

import net.creeperhost.minetogether.lib.chat.irc.IrcChannel;
import net.creeperhost.minetogether.lib.chat.irc.IrcState;
import net.creeperhost.minetogether.lib.chat.message.Message;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogether.lib.chat.profile.ProfileManager;
import net.creeperhost.minetogethercommunity.Constants;
import net.creeperhost.minetogethercommunity.chat.ChatConstants;
import net.creeperhost.minetogethercommunity.chat.ChatStatistics;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.config.LocalConfig;
import net.creeperhost.minetogethercommunity.gui.PreviewElement;
import net.creeperhost.minetogethercommunity.gui.ProfileGui;
import net.creeperhost.minetogethercommunity.gui.SettingGui;
import net.creeperhost.minetogethercommunity.modulargui.ContextMenu;
import net.creeperhost.minetogethercommunity.modulargui.GuiButton;
import net.creeperhost.minetogethercommunity.modulargui.GuiClip;
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
import java.util.List;
import java.util.function.Consumer;

public class PublicChatGui implements GuiProvider {

    private GuiTextField input;
    private GuiButton sendButton;
    private ContextMenu activeContextMenu;

    @Override
    public GuiElement<?> createRootElement(final ModularGui gui) {
        gui.setPauseScreen(true);
        gui.renderScreenBackground(false);
        if (MineTogetherChat.isNewUser()) {
            return createNewUserRoot(gui);
        }

        GuiElement<?> root = new GuiElement<>(gui);
        int screenWidth = gui.getScreen().width;
        int screenHeight = gui.getScreen().height;
        int margin = 10;
        int textBoxTop = screenHeight - 26;
        int chatTop = 22;
        int chatBottom = textBoxTop - 4;

        new GuiRectangle(root, MTStyle.Flat.BACKGROUND).setBounds(0, 0, screenWidth, screenHeight);
        new GuiText(root, () -> I18n.format("minetogether.gui.chat.title")).centered()
                .setBounds(0, 5, screenWidth, 8);
        int titleWidth = gui.mc().fontRenderer.getStringWidth(I18n.format("minetogether.gui.chat.title"));
        new GuiText(root, this::stateIndicator)
                .setBounds(screenWidth / 2 + titleWidth / 2 + 4, 5, 10, 8);

        new GuiButton(root, () -> I18n.format("minetogether.gui.button.back_arrow"))
                .setBounds(margin, chatTop - 18, 50, 14)
                .onPress(() -> gui.mc().displayGuiScreen(gui.getParentScreen()));
        iconButton(root, screenWidth - margin - 14, chatTop - 18, 14, 14, Constants.GEAR_BUTTON, 12)
                .onPress(() -> gui.mc().displayGuiScreen(new SettingGui.Screen(gui.getScreen())));
        iconButton(root, screenWidth - margin - 32, chatTop - 18, 16, 14,
                new ResourceLocation("minetogethercommunity", "textures/gui/buttons/friend_chat_light.png"), 16)
                .onPress(() -> gui.mc().displayGuiScreen(new FriendChatGui.Screen(gui.getParentScreen())));

        ChatMessagePanel messages = new ChatMessagePanel(root);
        messages.setBounds(margin, chatTop, screenWidth - margin * 2, chatBottom - chatTop);
        new PreviewElement(root)
                .setEnforceDomains(false)
                .setUrlProvider((mouseX, mouseY) -> {
                    if (hasActiveContextMenu()) return null;
                    PreviewElement.URLInfo info = messages.hoveredUrl(mouseX, mouseY);
                    return info;
                })
                .setBounds(0, 0, screenWidth, screenHeight);

        if (MineTogetherChat.isBanned()) {
            new GuiButton(root, () -> I18n.format("minetogether.gui.button.banned"))
                    .setBounds(margin, textBoxTop, screenWidth - margin * 2, 16)
                    .onPress(() -> gui.mc().displayGuiScreen(new ProfileGui.Screen(gui.getScreen())));
            return root;
        }

        new GuiRectangle(root, MTStyle.Flat.CONTENT_AREA).setBounds(margin, textBoxTop, screenWidth - margin * 2, 16);
        input = new GuiTextField(root) {
            @Override
            public boolean keyTyped(char typedChar, int keyCode) throws IOException {
                if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) && enabled) {
                    sendCurrentMessage();
                    return true;
                }
                return super.keyTyped(typedChar, keyCode);
            }
        }.setMaxLength(256).setCanLoseFocus(false).setBounds(margin + 1, textBoxTop + 3, screenWidth - margin * 2 - 2, 8);
        sendButton = null;
        updateInputState();
        return root;
    }

    @Override
    public void tick(ModularGui gui) {
        updateInputState();
    }

    private GuiElement<?> createNewUserRoot(final ModularGui gui) {
        ChatStatistics.pollStats();

        GuiElement<?> root = new GuiElement<>(gui);
        int screenWidth = gui.getScreen().width;
        int screenHeight = gui.getScreen().height;
        int width = 360;
        int left = (screenWidth - width) / 2;
        int top = screenHeight / 2 - 40;

        new GuiRectangle(root, MTStyle.Flat.BACKGROUND).setBounds(0, 0, screenWidth, screenHeight);
        new GuiText(root, () -> I18n.format("minetogether.new_user.1")).centered().setBounds(left, top, width, 12);
        new GuiText(root, () -> I18n.format("minetogether.new_user.2")).centered().setColor(0xCCCCCC).setBounds(left, top + 9, width, 12);
        new GuiText(root, () -> I18n.format("minetogether.new_user.3")).centered().setColor(0xCCCCCC).setBounds(left, top + 18, width, 12);
        new GuiText(root, () -> I18n.format("minetogether.new_user.4", ChatStatistics.userCount)).centered().setColor(0xCCCCCC).setBounds(left, top + 27, width, 12);
        new GuiButton(root, () -> I18n.format("minetogether.gui.join.button.accept", ChatStatistics.onlineCount))
                .setBounds(left + 30, top + 44, width - 60, 16)
                .onPress(() -> {
                    MineTogetherChat.setNewUserResponded();
                    gui.mc().displayGuiScreen(new Screen(gui.getParentScreen()));
                });
        new GuiButton(root, () -> I18n.format("minetogether.gui.join.button.reject"))
                .caution()
                .setBounds(left + 30, top + 62, width - 60, 16)
                .onPress(() -> {
                    MineTogetherChat.disableChat();
                    LocalConfig.instance().chatEnabled = false;
                    LocalConfig.save();
                    MineTogetherChat.setNewUserResponded();
                    gui.mc().displayGuiScreen(gui.getParentScreen());
                });
        return root;
    }

    private String stateText() {
        return MineTogetherChat.CHAT_STATE == null
                ? I18n.format("minetogether.gui.chat.disconnected")
                : ChatConstants.describe(MineTogetherChat.CHAT_STATE.ircClient.getState());
    }

    private String stateIndicator() {
        if (MineTogetherChat.CHAT_STATE == null) return "\u2716";
        switch (MineTogetherChat.CHAT_STATE.ircClient.getState()) {
            case CONNECTED:
                return "\u2714";
            case CONNECTING:
            case RECONNECTING:
            case VERIFYING:
                return new String[]{"|", "/", "-", "\\"}[(int) ((System.currentTimeMillis() / 100L) % 4L)];
            default:
                return "\u2716";
        }
    }

    private GuiButton iconButton(GuiElement<?> root, int x, int y, int width, int height, ResourceLocation texture, int textureSize) {
        GuiButton button = new GuiButton(root, () -> "").setBounds(x, y, width, height);
        int iconSize = Math.min(textureSize, Math.min(width, height));
        int iconX = x + (width - iconSize) / 2;
        int iconY = y + (height - iconSize) / 2;
        new GuiTexture(button, texture).textureSize(textureSize, textureSize).setBounds(iconX, iconY, iconSize, iconSize);
        return button;
    }

    private void updateInputState() {
        boolean enabled = MineTogetherChat.canSendPublicMessages();
        if (input != null) {
            input.setEnabled(enabled);
            input.setFocused(enabled);
            input.setSuggestion(enabled ? "" : publicInputSuggestion());
        }
        if (sendButton != null) sendButton.setEnabled(enabled);
    }

    private String publicInputSuggestion() {
        if (MineTogetherChat.CHAT_STATE == null) {
            return I18n.format("minetogether.screen.chat.suggestion.disconnected");
        }
        IrcState state = MineTogetherChat.CHAT_STATE.ircClient.getState();
        String key = ChatConstants.STATE_SUGGESTION_LOOKUP.get(state);
        return key == null ? "" : I18n.format(key);
    }

    private void sendCurrentMessage() {
        if (input == null) return;
        String text = input.getText();
        if (MineTogetherChat.sendPublicMessage(text)) {
            input.setText("");
        }
    }

    private class ChatMessagePanel extends GuiElement<ChatMessagePanel> {

        private int scrollFromBottom;
        private List<ChatMessageLines.Line> cachedLines;
        private int cacheWidth = Integer.MIN_VALUE;
        private long cacheSignature = Long.MIN_VALUE;

        private List<ChatMessageLines.Line> allLines() {
            List<Message> messages = recentMessages();
            int wrapWidth = width - 10;
            long signature = contentSignature(messages);
            if (cachedLines == null || wrapWidth != cacheWidth || signature != cacheSignature) {
                cachedLines = ChatMessageLines.build(font(), messages, wrapWidth);
                cacheWidth = wrapWidth;
                cacheSignature = signature;
            }
            return cachedLines;
        }

        private long contentSignature(List<Message> messages) {
            long sig = 1L;
            Profile ours = MineTogetherChat.getOurProfile();
            sig = sig * 1000003L + System.identityHashCode(ours);
            for (Message message : messages) {
                sig = sig * 1000003L + System.identityHashCode(message);
                sig = sig * 1000003L + System.identityHashCode(message.getMessage());
                sig = appendSignature(sig, message.getMessage() == null ? null : message.getMessage().getMessage());
                sig = appendSignature(sig, message.senderName == null ? null : message.senderName.getMessage());
                sig = appendProfileSignature(sig, message.sender, ours);
            }
            return sig;
        }

        private long appendProfileSignature(long sig, Profile profile, Profile ours) {
            sig = sig * 1000003L + System.identityHashCode(profile);
            if (profile == null) return sig;
            sig = appendSignature(sig, MineTogetherChat.displayName(profile));
            sig = appendSignature(sig, profile.getDisplayName());
            sig = appendSignature(sig, profile.hasFriendName() ? profile.getFriendName() : null);
            sig = sig * 1000003L + (profile.isFriend() ? 1 : 0);
            sig = sig * 1000003L + (profile.isPremium() ? 1 : 0);
            sig = sig * 1000003L + (profile.isBanned() ? 1 : 0);
            sig = sig * 1000003L + (isOnSamePack(ours, profile) ? 1 : 0);
            return sig;
        }

        private long appendSignature(long sig, String value) {
            return sig * 1000003L + (value == null ? 0 : value.hashCode());
        }

        private boolean isOnSamePack(Profile ours, Profile sender) {
            try {
                return ours != null && sender != null && ours.isOnSamePack(sender);
            } catch (Throwable ignored) {
                return false;
            }
        }

        public ChatMessagePanel(GuiElement<?> parent) {
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
            List<ChatMessageLines.Line> lines = visibleLines();
            int lineY = y + Math.max(4, height - lines.size() * 10 - 4);
            for (ChatMessageLines.Line line : lines) {
                font().drawStringWithShadow(line.text, x + 4, lineY, 0xE6E6E6);
                if (mouseY >= lineY - 1 && mouseY < lineY + 10 && !hasActiveContextMenu()
                        && actionableStyle(ChatMessageLines.styleAt(font(), line, mouseX - x - 4))) {
                    drawRect(x + 2, lineY - 1, x + width - 8, lineY + 10, 0x20FFFFFF);
                }
                lineY += 10;
            }
            drawScrollBar(mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
            if ((mouseButton != GuiButton.LEFT_CLICK && mouseButton != GuiButton.RIGHT_CLICK) || !isMouseOver(mouseX, mouseY)) return false;
            List<ChatMessageLines.Line> lines = visibleLines();
            int lineY = y + Math.max(4, height - lines.size() * 10 - 4);
            for (ChatMessageLines.Line line : lines) {
                Message message = line.message;
                if (mouseY >= lineY - 1 && mouseY < lineY + 10) {
                    Style style = ChatMessageLines.styleAt(font(), line, mouseX - x - 4);
                    URL clickedUrl = urlFromStyle(style);
                    if (clickedUrl == null) {
                        clickedUrl = ChatMessageLines.urlAt(font(), line, mouseX - x - 4);
                    }
                    if (mouseButton == GuiButton.RIGHT_CLICK && openContextMenu(message, clickedUrl, mouseX, mouseY)) {
                        return true;
                    }
                    if (clickedUrl != null) {
                        openUrl(clickedUrl);
                        return true;
                    }
                    if (isNameClick(style) && isActionable(message)) {
                        if (LocalConfig.instance().shiftClickMention && GuiScreen.isShiftKeyDown()) {
                            mention(message.sender);
                        } else {
                            openContextMenu(message, null, mouseX, mouseY);
                        }
                        return true;
                    }
                }
                lineY += 10;
            }
            return true;
        }

        private boolean openContextMenu(final Message message, final URL url, int mouseX, int mouseY) {
            if (url == null && !isActionable(message)) return false;
            closeActiveContextMenu();
            ContextMenu menu = new ContextMenu(getModularGui().getRoot());
            activeContextMenu = menu;
            menu.addTitle(contextTitle(message));
            if (url != null) {
                menu.addOption(I18n.format("minetogether.gui.chat.action.open_link"), 0x55AAFF, new Runnable() {
                    @Override
                    public void run() {
                        openUrl(url);
                    }
                });
            }
            if (isActionable(message)) {
                menu.addOption(I18n.format("minetogether.gui.chat.action.mention"), 0x55FFFF, new Runnable() {
                    @Override
                    public void run() {
                        mention(message.sender);
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

        private boolean actionableStyle(Style style) {
            return isNameClick(style) || urlFromStyle(style) != null;
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

        @Override
        public boolean mouseInput(int mouseX, int mouseY, int dWheel) throws IOException {
            if (!isMouseOver(mouseX, mouseY)) return false;
            int max = maxLineScroll();
            if (max <= 0) return false;
            scrollFromBottom += dWheel < 0 ? -3 : 3;
            if (scrollFromBottom < 0) scrollFromBottom = 0;
            if (scrollFromBottom > max) scrollFromBottom = max;
            return true;
        }

        private List<Message> recentMessages() {
            List<Message> lines = new ArrayList<>();
            if (MineTogetherChat.CHAT_STATE == null) return lines;
            IrcChannel channel = MineTogetherChat.CHAT_STATE.ircClient.getPrimaryChannel();
            if (channel == null) return lines;
            List<Message> messages = channel.getMessages();
            int start = Math.max(0, messages.size() - 500);
            for (int i = start; i < messages.size(); i++) {
                lines.add(messages.get(i));
            }
            return lines;
        }

        private List<ChatMessageLines.Line> visibleLines() {
            List<ChatMessageLines.Line> all = allLines();
            int maxLines = Math.max(1, (height - 8) / 10);
            int maxScroll = Math.max(0, all.size() - maxLines);
            if (scrollFromBottom > maxScroll) scrollFromBottom = maxScroll;
            int start = Math.max(0, all.size() - maxLines - scrollFromBottom);
            int end = Math.min(all.size(), start + maxLines);
            return new ArrayList<>(all.subList(start, end));
        }

        private int maxLineScroll() {
            int maxLines = Math.max(1, (height - 8) / 10);
            return Math.max(0, allLines().size() - maxLines);
        }

        private void drawScrollBar(int mouseX, int mouseY) {
            List<ChatMessageLines.Line> all = allLines();
            int maxLines = Math.max(1, (height - 8) / 10);
            int maxScroll = Math.max(0, all.size() - maxLines);
            if (maxScroll <= 0) return;
            int scrollTop = maxScroll - Math.max(0, Math.min(scrollFromBottom, maxScroll));
            boolean hover = mouseX >= x + width - 6 && mouseX < x + width - 2 && mouseY >= y + 2 && mouseY < y + height - 2;
            MTStyle.Flat.drawVerticalScrollBar(x + width - 6, y + 2, 4, height - 4, all.size(), maxLines, scrollTop, hover);
        }

        private boolean isActionable(Message message) {
            return message.sender != null && message.sender != MineTogetherChat.getOurProfile();
        }

        private PreviewElement.URLInfo hoveredUrl(int mouseX, int mouseY) {
            if (!isMouseOver(mouseX, mouseY)) return null;
            List<ChatMessageLines.Line> lines = visibleLines();
            int lineY = y + Math.max(4, height - lines.size() * 10 - 4);
            for (ChatMessageLines.Line line : lines) {
                Message message = line.message;
                if (mouseY >= lineY - 1 && mouseY < lineY + 10) {
                    URL url = ChatMessageLines.urlAt(font(), line, mouseX - x - 4);
                    return url == null ? null : new PreviewElement.URLInfo(url, message.sender == null);
                }
                lineY += 10;
            }
            return null;
        }

        private void openUrl(URL url) {
            if (!KeycloakOAuth.openURL(url)) {
                MineTogetherChat.localStatus("minetogether.gui.chat.action.open_failed");
            }
        }

        private void toggleMute(Profile profile) {
            if (profile.isMuted()) {
                profile.unmute();
                MineTogetherChat.localStatus("minetogether.gui.chat.action.unmuted", MineTogetherChat.displayName(profile));
            } else {
                profile.mute();
                MineTogetherChat.localStatus("minetogether.gui.chat.action.muted", MineTogetherChat.displayName(profile));
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
                            ProfileManager manager = MineTogetherChat.CHAT_STATE == null ? null : MineTogetherChat.CHAT_STATE.profileManager;
                            if (manager == null) return;
                            manager.sendFriendRequest(profile.getFriendCode(), friendName.trim(), success -> {
                                MineTogetherChat.localStatus(success ? "minetogether.gui.friends.request_sent" : "minetogether.gui.friends.request_fail");
                            });
                        }
                    }).init();
        }

        private void mention(Profile profile) {
            if (input == null) return;
            String value = input.getText();
            if (!value.isEmpty() && value.charAt(value.length() - 1) != ' ') {
                value += " ";
            }
            input.setText(value + MineTogetherChat.displayName(profile));
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

    public static class Screen extends ModularGuiScreen {
        public Screen(GuiScreen parentScreen) {
            super(new PublicChatGui(), parentScreen);
        }
    }
}
