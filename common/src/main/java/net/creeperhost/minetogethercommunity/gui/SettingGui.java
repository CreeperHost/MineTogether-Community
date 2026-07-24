package net.creeperhost.minetogethercommunity.gui;

import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.chat.gui.FriendChatGui;
import net.creeperhost.minetogethercommunity.chat.gui.MTStyle;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.activity.GetProfileVisibilityRequest;
import net.creeperhost.minetogethercommunity.activity.PutProfileVisibilityRequest;
import net.creeperhost.minetogethercommunity.config.Config;
import net.creeperhost.minetogethercommunity.config.LocalConfig;
import net.creeperhost.minetogethercommunity.connect.gui.ConnectPackSelectionScreen;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticsGui;
import net.creeperhost.minetogethercommunity.oauth.KeycloakOAuth;
import net.creeperhost.polylib.client.modulargui.ModularGui;
import net.creeperhost.polylib.client.modulargui.ModularGuiScreen;
import net.creeperhost.polylib.client.modulargui.elements.*;
import net.creeperhost.polylib.client.modulargui.lib.*;
import net.creeperhost.polylib.client.modulargui.lib.geometry.Align;
import net.creeperhost.polylib.client.modulargui.lib.geometry.Axis;
import net.creeperhost.polylib.client.modulargui.lib.geometry.GuiParent;
import net.creeperhost.polylib.helpers.MathUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static net.creeperhost.polylib.client.modulargui.lib.geometry.Constraint.*;
import static net.creeperhost.polylib.client.modulargui.lib.geometry.Constraint.literal;
import static net.creeperhost.polylib.client.modulargui.lib.geometry.GeoParam.*;
import static net.creeperhost.polylib.client.modulargui.lib.geometry.GeoParam.RIGHT;
import static net.minecraft.ChatFormatting.*;

/**
 * Created by brandon3055 on 02/10/2023
 */
public class SettingGui implements GuiProvider {

    private boolean showBlocked = false;
    private double blockedAnim;
    private GuiList<Profile> blockedList;
    private volatile String profileVisibility = "public";
    private volatile boolean visibilityLoading = false;
    private volatile boolean visibilitySaving = false;

    private SettingGui() {}

    @Override
    public GuiElement<?> createRootElement(ModularGui gui) {
        return MTStyle.Flat.background(gui);
    }

    @Override
    public void buildGui(ModularGui gui) {
        gui.setPauseScreen(true);
        gui.renderScreenBackground(false);
        gui.initFullscreenGui();
        gui.setGuiTitle(Component.translatable("minetogether:gui.settings.title"));

        GuiElement<?> root = gui.getRoot();
        // 6 rows × 14 + 5 × 4 + 16 gap + 14 back = 134px total → center offset -67
        int panelWidth = 310;
        int blockedPanelWidth = 150;
        int buttonHeight = 14;

        GuiText title = new GuiText(root, gui.getGuiTitle())
                .constrain(TOP, relative(root.get(TOP), 10))
                .constrain(HEIGHT, literal(8))
                .constrain(LEFT, relative(root.get(LEFT), 10))
                .constrain(RIGHT, relative(root.get(RIGHT), -10));

        GuiElement<?> settings = new GuiElement<>(root)
                .constrain(TOP, midPoint(root.get(TOP), root.get(BOTTOM), -76))
                .constrain(LEFT, dynamic(() -> buttonPanelPos(root)))
                .constrain(WIDTH, literal(panelWidth))
                .constrain(HEIGHT, literal(0));

        // Row 0: Chat | Menu Buttons
        GuiButton enabled = MTStyle.Flat.button(settings, Component.empty())
                .onPress(this::toggleEnabled)
                .constrain(TOP, match(settings.get(TOP)))
                .constrain(LEFT, match(settings.get(LEFT)))
                .constrain(RIGHT, midPoint(settings.get(LEFT), settings.get(RIGHT), -2))
                .constrain(HEIGHT, literal(buttonHeight));
        enabled.getLabel().setTextSupplier(() -> Component.translatable("minetogether:gui.settings.button.chat").append(state(LocalConfig.instance().chatEnabled)));

        GuiButton menuButtons = MTStyle.Flat.button(settings, Component.empty())
                .onPress(() -> setLocalConfig(() -> LocalConfig.instance().mainMenuButtons ^= true))
                .constrain(TOP, match(enabled.get(TOP)))
                .constrain(LEFT, midPoint(settings.get(LEFT), settings.get(RIGHT), 2))
                .constrain(RIGHT, match(settings.get(RIGHT)))
                .constrain(HEIGHT, literal(buttonHeight));
        menuButtons.getLabel().setTextSupplier(() -> Component.translatable("minetogether:gui.settings.button.menu_buttons").append(state(LocalConfig.instance().mainMenuButtons)));

        // Row 1: Pause Buttons | Friend Toasts
        GuiButton pauseButtons = MTStyle.Flat.button(settings, Component.empty())
                .onPress(() -> setLocalConfig(() -> Config.instance().pauseScreenButtons ^= true))
                .constrain(TOP, relative(enabled.get(BOTTOM), 4))
                .constrain(LEFT, match(settings.get(LEFT)))
                .constrain(RIGHT, midPoint(settings.get(LEFT), settings.get(RIGHT), -2))
                .constrain(HEIGHT, literal(buttonHeight));
        pauseButtons.getLabel().setTextSupplier(() -> Component.translatable("minetogether:gui.settings.button.pause_buttons").append(state(Config.instance().pauseScreenButtons)));

        GuiButton toasts = MTStyle.Flat.button(settings, Component.empty())
                .onPress(() -> setLocalConfig(() -> LocalConfig.instance().friendNotifications ^= true))
                .constrain(TOP, match(pauseButtons.get(TOP)))
                .constrain(LEFT, midPoint(settings.get(LEFT), settings.get(RIGHT), 2))
                .constrain(RIGHT, match(settings.get(RIGHT)))
                .constrain(HEIGHT, literal(buttonHeight));
        toasts.getLabel().setTextSupplier(() -> Component.translatable("minetogether:gui.settings.button.friend_toasts").append(state(LocalConfig.instance().friendNotifications)));

        // Row 2: Chat Sliders | Shift-Click Mention
        GuiButton chatSliders = MTStyle.Flat.button(settings, Component.empty())
                .onPress(() -> setLocalConfig(() -> LocalConfig.instance().chatSettingsSliders ^= true))
                .constrain(TOP, relative(pauseButtons.get(BOTTOM), 4))
                .constrain(LEFT, match(settings.get(LEFT)))
                .constrain(RIGHT, midPoint(settings.get(LEFT), settings.get(RIGHT), -2))
                .constrain(HEIGHT, literal(buttonHeight));
        chatSliders.getLabel().setTextSupplier(() -> Component.translatable("minetogether:gui.settings.button.chat_sliders").append(state(LocalConfig.instance().chatSettingsSliders)));

        GuiButton shiftClickMention = MTStyle.Flat.button(settings, Component.empty())
                .onPress(() -> setLocalConfig(() -> LocalConfig.instance().shiftClickMention ^= true))
                .constrain(TOP, match(chatSliders.get(TOP)))
                .constrain(LEFT, midPoint(settings.get(LEFT), settings.get(RIGHT), 2))
                .constrain(RIGHT, match(settings.get(RIGHT)))
                .constrain(HEIGHT, literal(buttonHeight));
        shiftClickMention.getLabel().setTextSupplier(() -> Component.translatable("minetogether:gui.settings.button.shift_click_mention").append(state(LocalConfig.instance().shiftClickMention)));

        // Row 3: Activity Telemetry | Profile Visibility
        GuiButton activityTelemetry = MTStyle.Flat.button(settings, Component.empty())
                .onPress(() -> setLocalConfig(() -> LocalConfig.instance().activityTelemetry ^= true))
                .constrain(TOP, relative(chatSliders.get(BOTTOM), 4))
                .constrain(LEFT, match(settings.get(LEFT)))
                .constrain(RIGHT, midPoint(settings.get(LEFT), settings.get(RIGHT), -2))
                .constrain(HEIGHT, literal(buttonHeight));
        activityTelemetry.getLabel().setTextSupplier(() -> Component.translatable("minetogether:gui.settings.button.activity_telemetry").append(state(LocalConfig.instance().activityTelemetry)));

        GuiButton visibility = MTStyle.Flat.button(settings, Component.empty())
                .onPress(this::cycleProfileVisibility)
                .setDisabled(() -> !MineTogetherChat.getOurProfile().hasAccount() || visibilityLoading || visibilitySaving)
                .constrain(TOP, match(activityTelemetry.get(TOP)))
                .constrain(LEFT, midPoint(settings.get(LEFT), settings.get(RIGHT), 2))
                .constrain(RIGHT, match(settings.get(RIGHT)))
                .constrain(HEIGHT, literal(buttonHeight));
        visibility.getLabel().setTextSupplier(() -> Component.translatable("minetogether:gui.settings.button.profile_visibility").append(profileVisibilityLabel()));

        // Row 4: Muted Users | Link Account
        GuiButton blocked = MTStyle.Flat.button(settings, Component.translatable("minetogether:gui.settings.button.blocked"))
                .onPress(() -> showBlocked ^= true)
                .constrain(TOP, relative(activityTelemetry.get(BOTTOM), 4))
                .constrain(LEFT, match(settings.get(LEFT)))
                .constrain(RIGHT, midPoint(settings.get(LEFT), settings.get(RIGHT), -2))
                .constrain(HEIGHT, literal(buttonHeight));

        GuiButton link = MTStyle.Flat.button(settings, Component.translatable("minetogether:gui.settings.button.link"))
                .onPress(() -> {
                    gui.mc().gui.setScreen(new ConfirmScreen(b -> {
                        if (b) {
                            KeycloakOAuth.main(new String[0]);
                        }
                        gui.mc().gui.setScreen(gui.getScreen());
                    }, Component.translatable("minetogether:linkaccount1"), Component.translatable("minetogether:linkaccount2")));
                })
                .setDisabled(() -> MineTogetherChat.getOurProfile().hasAccount())
                .constrain(TOP, match(blocked.get(TOP)))
                .constrain(LEFT, midPoint(settings.get(LEFT), settings.get(RIGHT), 2))
                .constrain(RIGHT, match(settings.get(RIGHT)))
                .constrain(HEIGHT, literal(buttonHeight));

        // Row 5: Edit Profile | Cosmetics
        GuiButton profileScreen = MTStyle.Flat.button(settings, () -> Component.translatable("minetogether:gui.settings.button.profile"))
                .onPress(() -> gui.mc().gui.setScreen(new ProfileGui.Screen(gui.getScreen())))
                .constrain(TOP, relative(blocked.get(BOTTOM), 4))
                .constrain(LEFT, match(settings.get(LEFT)))
                .constrain(RIGHT, midPoint(settings.get(LEFT), settings.get(RIGHT), -2))
                .constrain(HEIGHT, literal(buttonHeight));

        GuiButton cosmetics = MTStyle.Flat.button(settings, Component.translatable("minetogether:gui.settings.button.cosmetics"))
                .onPress(() -> gui.mc().gui.setScreen(new CosmeticsGui.Screen(gui.getScreen())))
                .constrain(TOP, match(profileScreen.get(TOP)))
                .setDisabled(Minecraft.getInstance().player == null)
                .constrain(LEFT, midPoint(settings.get(LEFT), settings.get(RIGHT), 2))
                .constrain(RIGHT, match(settings.get(RIGHT)))
                .constrain(HEIGHT, literal(buttonHeight));

        // Back — full width
        // Row 6: Modpack Identity
        GuiButton modpack = MTStyle.Flat.button(settings, Component.translatable("minetogether:gui.settings.button.modpack"))
                .onPress(() -> gui.mc().gui.setScreen(new ConnectPackSelectionScreen.Screen(gui.getScreen())))
                .constrain(TOP, relative(profileScreen.get(BOTTOM), 4))
                .constrain(LEFT, match(settings.get(LEFT)))
                .constrain(RIGHT, match(settings.get(RIGHT)))
                .constrain(HEIGHT, literal(buttonHeight));

        GuiButton back = MTStyle.Flat.button(settings, Component.translatable("minetogether:gui.button.back"))
                .onPress(() -> gui.mc().gui.setScreen(gui.getParentScreen()))
                .constrain(TOP, relative(modpack.get(BOTTOM), 16))
                .constrain(LEFT, match(settings.get(LEFT)))
                .constrain(RIGHT, match(settings.get(RIGHT)))
                .constrain(HEIGHT, literal(buttonHeight));

        //Blocked Users
        GuiElement<?> blockedBg = MTStyle.Flat.contentArea(root)
                .setEnabled(() -> blockedAnim == 1)
                .constrain(LEFT, midPoint(root.get(LEFT), root.get(RIGHT), 5))
                .constrain(WIDTH, literal(blockedPanelWidth))
                .constrain(TOP, match(enabled.get(TOP)))
                .constrain(BOTTOM, match(back.get(BOTTOM)));

        GuiText blockedTitle = new GuiText(blockedBg, Component.translatable("minetogether:gui.settings.button.blocked").withStyle(UNDERLINE))
                .constrain(BOTTOM, relative(blockedBg.get(TOP), -3))
                .constrain(HEIGHT, literal(8))
                .constrain(LEFT, match(blockedBg.get(LEFT)))
                .constrain(RIGHT, match(blockedBg.get(RIGHT)));

        blockedList = new GuiList<Profile>(blockedBg)
                .setDisplayBuilder(BlockedEntry::new)
                .setItemSpacing(2);
        Constraints.bind(blockedList, blockedBg, 5);

        var scrollBar = MTStyle.Flat.scrollBar(blockedBg, Axis.Y);
        scrollBar.container
                .setEnabled(() -> blockedList.hiddenSize() > 0)
                .constrain(TOP, match(blockedList.get(TOP)))
                .constrain(BOTTOM, match(blockedList.get(BOTTOM)))
                .constrain(RIGHT, match(blockedBg.get(RIGHT)))
                .constrain(WIDTH, literal(4));
        scrollBar.primary
                .setScrollableElement(blockedList)
                .setSliderState(blockedList.scrollState());

        updateBlockedList();
        fetchProfileVisibility();
        gui.onTick(this::tick);
        gui.onResize(this::updateBlockedList);
    }

    private void updateBlockedList() {
        blockedList.getList().clear();
        blockedList.markDirty();
        String search = "";
        for (Profile mutedProfile : MineTogetherChat.CHAT_STATE.profileManager.getMutedProfiles()) {
            if (StringUtils.isEmpty(search) || StringUtils.containsAnyIgnoreCase(mutedProfile.getDisplayName(), search)) {
                blockedList.add(mutedProfile);
            }
        }
    }

    private void tick() {
        if (showBlocked && blockedAnim < 1) {
            blockedAnim = Math.min(1, blockedAnim + 0.2);
        } else if (!showBlocked && blockedAnim > 0) {
            blockedAnim = Math.max(0, blockedAnim - 0.2);
        }
    }

    private double buttonPanelPos(GuiElement<?> root) {
        double partial = (showBlocked ? 0.2 : -0.2) * Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double anim = MathUtil.clamp(blockedAnim + partial, 0, 1);
        return (root.xCenter() - 155D - (80D * anim));
    }

    private static Component state(boolean state) {
        if (state) {
            return Component.translatable("minetogether:gui.settings.button.enabled").withStyle(ChatFormatting.GREEN);
        }
        return Component.translatable("minetogether:gui.settings.button.disabled").withStyle(ChatFormatting.RED);
    }

    private Component profileVisibilityLabel() {
        if (visibilityLoading) {
            return Component.translatable("minetogether:gui.settings.visibility.loading").withStyle(ChatFormatting.GRAY);
        }
        if (visibilitySaving) {
            return Component.translatable("minetogether:gui.settings.visibility.saving").withStyle(ChatFormatting.GRAY);
        }
        return switch (profileVisibility) {
            case "private" -> Component.translatable("minetogether:gui.settings.visibility.private").withStyle(ChatFormatting.RED);
            case "friends" -> Component.translatable("minetogether:gui.settings.visibility.friends").withStyle(ChatFormatting.YELLOW);
            case "friends_of_friends" -> Component.translatable("minetogether:gui.settings.visibility.friends_of_friends").withStyle(ChatFormatting.AQUA);
            default -> Component.translatable("minetogether:gui.settings.visibility.public").withStyle(ChatFormatting.GREEN);
        };
    }

    private void fetchProfileVisibility() {
        if (visibilityLoading || !MineTogetherChat.getOurProfile().hasAccount()) return;
        visibilityLoading = true;
        CompletableFuture.runAsync(() -> {
            try {
                GetProfileVisibilityRequest.Response response = MineTogether.API.execute(new GetProfileVisibilityRequest()).apiResponse();
                if (response.success) {
                    profileVisibility = normalizeProfileVisibility(response.visibility);
                }
            } catch (Throwable ignored) {
                profileVisibility = "public";
            } finally {
                visibilityLoading = false;
            }
        });
    }

    private void cycleProfileVisibility() {
        String next = nextProfileVisibility(profileVisibility);
        profileVisibility = next;
        visibilitySaving = true;
        CompletableFuture.runAsync(() -> {
            try {
                PutProfileVisibilityRequest.Response response = MineTogether.API.execute(new PutProfileVisibilityRequest(next)).apiResponse();
                if (response.success) {
                    profileVisibility = normalizeProfileVisibility(response.visibility);
                }
            } catch (Throwable ignored) {
                profileVisibility = next;
            } finally {
                visibilitySaving = false;
            }
        });
    }

    private static String nextProfileVisibility(String current) {
        return switch (normalizeProfileVisibility(current)) {
            case "public" -> "friends";
            case "friends" -> "friends_of_friends";
            case "friends_of_friends" -> "private";
            default -> "public";
        };
    }

    private static String normalizeProfileVisibility(String value) {
        if (value == null) return "public";
        String normalized = value.trim().toLowerCase().replace("-", "_").replace(" ", "_");
        return switch (normalized) {
            case "private", "friends", "friends_of_friends" -> normalized;
            case "friend" -> "friends";
            case "friend_of_friend", "friendsoffriends" -> "friends_of_friends";
            default -> "public";
        };
    }

    private void toggleEnabled() {
        LocalConfig config = LocalConfig.instance();
        if (config.chatEnabled) {
            config.chatEnabled = false;
            MineTogetherChat.disableChat();
        } else {
            config.chatEnabled = true;
            MineTogetherChat.enableChat();
        }
        LocalConfig.save();
    }

    private void setLocalConfig(Runnable set) {
        set.run();
        LocalConfig.save();
        Config.save();
    }

    private class BlockedEntry extends GuiElement<BlockedEntry> implements BackgroundRender {
        public BlockedEntry(@NotNull GuiParent<?> parent, Profile profile) {
            super(parent);
            this.constrain(HEIGHT, literal(14));

            GuiText name = new GuiText(this, Component.empty())
                    .setTextSupplier(() -> Component.literal(FriendChatGui.displayName(profile)))
                    .setShadow(false)
                    .setAlignment(Align.LEFT)
                    .constrain(TOP, relative(get(TOP), 2))
                    .constrain(LEFT, relative(get(LEFT), 5))
                    .constrain(RIGHT, relative(get(RIGHT), -14))
                    .constrain(HEIGHT, literal(9));

            GuiButton unblock = MTStyle.Flat.button(this, (Supplier<Component>) null)
                    .setTooltip(Component.translatable("minetogether:gui.settings.button.unblock.info"))
                    .setTooltipDelay(0)
                    .onPress(() -> {
                        profile.unmute();
                        updateBlockedList();
                    })
                    .constrain(TOP, match(get(TOP)))
                    .constrain(BOTTOM, match(get(BOTTOM)))
                    .constrain(RIGHT, match(get(RIGHT)))
                    .constrain(WIDTH, literal(14));

            GuiTexture removeTex = new GuiTexture(unblock, MTTextures.get("buttons/delete"));
            Constraints.bind(removeTex, unblock, 2);
        }

        @Override
        public void renderBehind(GuiRender render, double mouseX, double mouseY, float partialTicks) {
            render.rect(getRectangle(), MTStyle.Flat.listEntryBackground(true));
        }
    }

    public static class Screen extends ModularGuiScreen {
        public Screen(net.minecraft.client.gui.screens.Screen parentScreen) {
            super(new SettingGui(), parentScreen);
        }
    }
}
