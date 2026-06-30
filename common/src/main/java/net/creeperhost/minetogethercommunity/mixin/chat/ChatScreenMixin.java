package net.creeperhost.minetogethercommunity.mixin.chat;

import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.chat.gui.ChatScreenInjection;
import net.creeperhost.minetogethercommunity.chat.gui.FriendChatGui;
import net.creeperhost.minetogethercommunity.chat.ingame.MTChatComponent;
import net.creeperhost.minetogethercommunity.config.LocalConfig;
import net.creeperhost.minetogethercommunity.gui.PreviewElement;
import net.creeperhost.minetogethercommunity.gui.SettingGui;
import net.creeperhost.minetogether.lib.chat.irc.IrcState;
import net.creeperhost.minetogether.lib.chat.message.Message;
import net.creeperhost.minetogethercommunity.polylib.gui.IconButton;
import net.creeperhost.minetogethercommunity.polylib.gui.RadioButton;
import net.creeperhost.minetogethercommunity.polylib.gui.SlideButton;
import net.creeperhost.minetogethercommunity.util.ChatStyleHelper;
import net.creeperhost.minetogethercommunity.util.MessageFormatter;
import net.creeperhost.minetogethercommunity.chat.*;
import net.creeperhost.polylib.client.modulargui.ModularGui;
import net.creeperhost.polylib.client.modulargui.ModularGuiInjector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.URL;

/**
 * Created by covers1624 on 5/8/22.
 */
@Mixin(ChatScreen.class)
abstract class ChatScreenMixin extends Screen {

    private static final Logger LOGGER = LogManager.getLogger();

    private RadioButton vanillaChatButton;
    private RadioButton mtChatButton;
    private RadioButton groupChatButton;
    private SlideButton chatScaleSlider;
    private SlideButton chatWidthSlider;
    private SlideButton chatHeightSlider;
    private IconButton settingsButton;

    @Nullable
    private Message clickedMessage;

    @Shadow
    protected EditBox input;

    @Shadow
    CommandSuggestions commandSuggestions;

    @Shadow private String initial;

    @Shadow private ChatComponent.DisplayMode displayMode;

    @Shadow protected abstract boolean handleComponentClicked(Style clicked, boolean allowInsertions);

    private Button newUserButton;
    private Button disableButton;

    protected ChatScreenMixin(Component component) {
        super(component);
    }

    @Inject(
            method = "init",
            at = @At("TAIL")
    )
    private void onInit(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!LocalConfig.instance().chatEnabled || mc.gui.hud.isHidden()) return;
        input.setValue(initial);

        ChatComponent chat = mc.gui.hud.getChat();
        float cScale = (float) chat.getScale();
        int cWidth = Mth.ceil((float) chat.getWidth() + (12 * cScale)); //Vanilla does some wired s%$#. This mostly accounts for it.
        int cHeight = Mth.ceil(chat.getHeight() * cScale);

        vanillaChatButton = addRenderableWidget(new RadioButton(0, 0, 12, 100, mc.isLocalServer() ? Component.translatable("minetogether:ingame.chat.local") : Component.translatable("minetogether:ingame.chat.server")))
                .withAutoScaleText(3)
                .withVerticalText()
                .selectedSupplier(() -> MineTogetherChat.getTarget() == ChatTarget.VANILLA)
                .onPressed(e -> MineTogetherChat.setTarget(ChatTarget.VANILLA))
                .onRelease(() -> setFocused(input));

        mtChatButton = addRenderableWidget(new RadioButton(0, 0, 12, 100, Component.translatable("minetogether:ingame.chat.global")))
                .withAutoScaleText(3)
                .withVerticalText()
                .selectedSupplier(() -> MineTogetherChat.getTarget() == ChatTarget.PUBLIC)
                .onPressed(e -> MineTogetherChat.setTarget(ChatTarget.PUBLIC))
                .onRelease(() -> setFocused(input));

        groupChatButton = addRenderableWidget(new RadioButton(0, 0, 12, 100, Component.translatable("minetogether:ingame.chat.group")))
                .withAutoScaleText(3)
                .withVerticalText()
                .selectedSupplier(() -> MineTogetherChat.getTarget() == ChatTarget.GROUP)
                .onPressed(e -> MineTogetherChat.setTarget(ChatTarget.GROUP))
                .onRelease(() -> setFocused(input));

        settingsButton = addRenderableWidget(new IconButton(0, 0, 12, 12, Identifier.fromNamespaceAndPath(MineTogether.MOD_ID, "textures/gui/buttons/gear.png"), e -> mc.gui.setScreen(new SettingGui.Screen(mc.gui.screen()))));

        chatScaleSlider = addRenderableWidget(new SlideButton(0, 0, 12, 200))
                .setDynamicMessage(() -> Component.translatable("options.percent_value", Component.translatable("options.chat.scale"), (int) (mc.options.chatScale().get() * 100.0)))
                .bindValue(() -> mc.options.chatScale().get(), value -> {
                    mc.options.chatScale().set(value);
                    updateButtons();
                })
                .setRange(0.25, 1)
                .withTextScale(0.75F)
                .onRelease(() -> setFocused(input))
                .setEnabled(() -> commandSuggestions.suggestions == null && LocalConfig.instance().chatSettingsSliders)
                .withAutoScaleText(3);

        chatWidthSlider = addRenderableWidget(new SlideButton(0, 0, 12, 200))
                .setDynamicMessage((newValue) -> Component.translatable("options.pixel_value", Component.translatable("options.chat.width"), ChatComponent.getWidth(newValue)))
                .bindValue(() -> mc.options.chatWidth().get(), value -> {
                    mc.options.chatWidth().set(value);
                    updateButtons();
                })
                .withTextScale(0.75F)
                .onRelease(() -> setFocused(input))
                .setEnabled(() -> commandSuggestions.suggestions == null && LocalConfig.instance().chatSettingsSliders)
                .withAutoScaleText(3);

        chatHeightSlider = addRenderableWidget(new SlideButton(0, 0, 12, 200))
                .setDynamicMessage(() -> Component.translatable("options.pixel_value", Component.translatable("options.chat.height.focused"), ChatComponent.getHeight(mc.options.chatHeightFocused().get())))
                .bindValue(() -> mc.options.chatHeightFocused().get(), value -> {
                    mc.options.chatHeightFocused().set(value);
                    updateButtons();
                })
                .withTextScale(0.75F)
                .onRelease(() -> setFocused(input))
                .setEnabled(() -> commandSuggestions.suggestions == null && LocalConfig.instance().chatSettingsSliders)
                .withAutoScaleText(3);

        updateButtons();

        newUserButton = addWidget(Button.builder(Component.literal("Join " + ChatStatistics.onlineCount + " online users now!"), e -> {
                            MineTogetherChat.setNewUserResponded();
                            setFocused(input);
                        })
                        .bounds(6, height - ((cHeight + 80) / 2) + 45, cWidth - 2, 20)
                        .build()
        );
        disableButton = addWidget(Button.builder(Component.literal("Don't ask me again."), e -> {
                            MineTogetherChat.disableChat();
                            LocalConfig.instance().chatEnabled = false;
                            LocalConfig.save();
                            MineTogetherChat.setNewUserResponded();
                            clearWidgets();
                            setFocused(input);
                        })
                        .bounds(6, height - ((cHeight + 80) / 2) + 70, cWidth - 2, 20)
                        .build()
        );
        newUserButton.visible = false;
        disableButton.visible = false;

        if (MineTogetherChat.isNewUser() && MineTogetherChat.getTarget() == ChatTarget.PUBLIC) {
            ChatStatistics.pollStats();
            newUserButton.visible = true;
            disableButton.visible = true;
        }

        switchToVanillaIfCommand();

        ModularGui gui = ModularGuiInjector.getActiveGui();
        if (gui != null && gui.getProvider() instanceof ChatScreenInjection) {
            ChatScreenInjection.setURLProvider(this::getUrlUnderMouse);
        }
    }

    private void updateButtons() {
        Minecraft mc = Minecraft.getInstance();
        if (!LocalConfig.instance().chatEnabled || mc.gui.hud.isHidden()) {
            return;
        }
        ChatComponent chat = mc.gui.hud.getChat();
        float cScale = (float) chat.getScale();
        int cWidth = Mth.ceil((float) chat.getWidth() + (12 * cScale)); //Vanilla does some wired s%$#. This mostly accounts for it.
        int cHeight = Mth.ceil(chat.getHeight() * cScale) - 12;
        int guiHeight = height;
        int cMaxYPos = guiHeight - 40;

        boolean groupChat = MineTogetherChat.CHAT_STATE.profileManager.getPrivateGroup() != null;

        int vanillaYPos = cMaxYPos - cHeight - 12;
        int vanillaHeight = groupChat ? cHeight / 3 : cHeight / 2;
        vanillaChatButton.updateBounds(cWidth, vanillaYPos, 12, vanillaHeight);

        int mtYPos = vanillaYPos + vanillaHeight;
        int mtHeight = groupChat ? cHeight / 3 : cMaxYPos - mtYPos - 12;
        mtChatButton.updateBounds(cWidth, mtYPos, 12, mtHeight);

        int groupYPos = mtYPos + mtHeight;
        int groupHeight = groupChat ? cMaxYPos - groupYPos - 12 : 0;
        groupChatButton.updateBounds(cWidth, groupYPos, 12, groupHeight);
        groupChatButton.visible = groupChat;

        settingsButton.updateBounds(cWidth, groupChat ? groupYPos + groupHeight : mtYPos + mtHeight, 12, 12);

        int sliderWidth = cWidth / 3;
        chatWidthSlider.updateBounds(0, cMaxYPos + 2, sliderWidth, 10);
        chatHeightSlider.updateBounds(sliderWidth + 2, cMaxYPos + 2, sliderWidth, 10);
        chatScaleSlider.updateBounds((sliderWidth * 2) + 4, cMaxYPos + 2, cWidth - (sliderWidth * 2) - 4, 10);
    }

    @Inject(
            method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        //Should be able to do this with a client command, but client commands dont work through the "RUN_COMMAND" click event.
        Style style = ChatStyleHelper.styleAtChatPosition(Minecraft.getInstance(), getFont(), mouseX, mouseY, displayMode, Minecraft.getInstance().hasShiftDown());
        if (style != null) {
            ClickEvent clickEvent = style.getClickEvent();
            if (clickEvent instanceof FriendChatNotifier.OpenFriendEvent openFriendEvent) {
                FriendChatGui.setSelected(openFriendEvent.profile);
                Minecraft.getInstance().gui.setScreen(new FriendChatGui.Screen(null));
                cir.setReturnValue(true);
            }
        }

        if (!LocalConfig.instance().chatEnabled || Minecraft.getInstance().gui.hud.isHidden()) return;

        //Link clicks get blocked by our tryClickMTChat function, so we need to do it ourselves here.
        if (MineTogetherChat.getTarget() == ChatTarget.PUBLIC && button == 0) {
            if (style != null && !MessageFormatter.isClickName(style.getClickEvent()) && this.handleComponentClicked(style, false)) {
                this.initial = this.input.getValue();
                cir.setReturnValue(true);
            }
        }

        if (MineTogetherChat.getTarget() == ChatTarget.PUBLIC && tryClickMTChat(MineTogetherChat.publicChat, mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!LocalConfig.instance().chatEnabled || mc.gui.hud.isHidden()) {
            return super.mouseReleased(event);
        }

        //Needed because vanilla does not bother to send release if the mouse is not over the component.
        chatWidthSlider.mouseReleased(event);
        chatHeightSlider.mouseReleased(event);
        chatScaleSlider.mouseReleased(event);

        //Ensure input box is always focused after input.
        setFocused(input);
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        //Prevent focus from being directed away from text box via arrow keys
        setFocused(input);
        return super.keyReleased(event);
    }

    @Inject(
            method = "extractRenderState",
            at = @At("TAIL")
    )
    private void onRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (MineTogetherChat.getTarget() == ChatTarget.PUBLIC && MineTogetherChat.isNewUser()) {
            graphics.nextStratum();

            ChatComponent chatComponent = MineTogetherChat.publicChat;
            int y = height - 43 - (minecraft.font.lineHeight * Math.max(Math.min(chatComponent.getRecentChat().size(), chatComponent.getLinesPerPage()), 20));
            graphics.fill(0, y, chatComponent.getWidth() + 6, chatComponent.getHeight() + 10 + y, 0x99000000);

            graphics.centeredText(font, Component.translatable("minetogether:new_user.1"), (chatComponent.getWidth() / 2) + 3, height - ((chatComponent.getHeight() + 80) / 2), 0xFFFFFFFF);
            graphics.centeredText(font, Component.translatable("minetogether:new_user.2"), (chatComponent.getWidth() / 2) + 3, height - ((chatComponent.getHeight() + 80) / 2) + 10, 0xFFFFFFFF);
            graphics.centeredText(font, Component.translatable("minetogether:new_user.3"), (chatComponent.getWidth() / 2) + 3, height - ((chatComponent.getHeight() + 80) / 2) + 20, 0xFFFFFFFF);
            graphics.centeredText(font, Component.translatable("minetogether:new_user.4", ChatStatistics.userCount), (chatComponent.getWidth() / 2) + 3, height - ((chatComponent.getHeight() + 80) / 2) + 30, 0xFFFFFFFF);

            // Render these manually after the grey-out, so they are on top of it.
            newUserButton.extractRenderState(graphics, mouseX, mouseY, partialTicks);
            disableButton.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    public void tick() {
        switchToVanillaIfCommand();

        if (groupChatButton != null && groupChatButton.visible == (MineTogetherChat.CHAT_STATE.profileManager.getPrivateGroup() == null)) {
            updateButtons();
        }

        // If we are the vanilla chat, set things editable, and bail out.
        if (MineTogetherChat.getTarget() == ChatTarget.VANILLA) {
            input.setEditable(true);
            input.setSuggestion("");
            return;
        }

        if (MineTogetherChat.isNewUser()) {
            newUserButton.visible = true;
            disableButton.visible = true;
            input.setEditable(false);
            return;
        }

        IrcState state = MineTogetherChat.CHAT_STATE.ircClient.getState();
        if (state != IrcState.CONNECTED) {
            input.setEditable(false);
            input.setSuggestion(Component.translatable(ChatConstants.STATE_SUGGESTION_LOOKUP.get(state)).getString());
            return;
        }

        input.setEditable(true);
        input.setSuggestion("");
    }

    private boolean tryClickMTChat(MTChatComponent mtChat, double mouseX, double mouseY, int button) {
        if (!mtChat.handleClick(mouseX, mouseY)) return false;

        Message message = mtChat.getClickedMessage();
        if (message == null) return false;

        ModularGui gui = ModularGuiInjector.getActiveGui();
        if (gui != null && gui.getProvider() instanceof ChatScreenInjection injection && injection.canShowDialog()) {
            clickedMessage = message;
            mtChat.clearClickedMessage();
            injection.openMessageDialog(clickedMessage, input, mouseX, mouseY, button);
            return true;
        }
        return false;
    }

    @Nullable
    private PreviewElement.URLInfo getUrlUnderMouse(double mouseX, double mouseY) {
        if (MineTogetherChat.getTarget() != ChatTarget.PUBLIC) return null;

        Style style = MineTogetherChat.publicChat.getStyleUnderMouse(mouseX, mouseY);
        URL url = PreviewElement.urlFromStyle(style);
        if (url == null) return null;
        Message message = MineTogetherChat.publicChat.getMessageUnderMouse(mouseX, mouseY);
        return new PreviewElement.URLInfo(url, message != null && message.sender == null);
    }

    @Inject(
            method = "handleChatInput",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onHandleChatInput(String message, boolean bl, CallbackInfo ci) {
        String normalized = normalizeChatMessage(message);
        if (MineTogetherChat.getTarget() == ChatTarget.PUBLIC && !normalized.isEmpty()) {
            MineTogetherChat.publicChat.addRecentChat(normalized);
            ci.cancel();
        }
    }

    @Shadow public abstract String normalizeChatMessage(String string);

    //TODO looks like the code this references has been completely removed. Not sure if this is going to be an issue.
//    @Inject(
//            method = "sendsChatPreviewRequests",
//            at = @At("HEAD"),
//            cancellable = true
//    )
//    private void onSendsChatPreviewRequests(CallbackInfoReturnable<Boolean> cir) {
//        if (MineTogetherChat.getTarget() != ChatTarget.VANILLA) {
//            cir.setReturnValue(false);
//        }
//    }

    private boolean switchToVanillaIfCommand() {
        if (MineTogetherChat.getTarget() == ChatTarget.VANILLA) return false;
        if (!input.getValue().startsWith("/")) return false;

        MineTogetherChat.setTarget(ChatTarget.VANILLA);
        return true;
    }
}
