package net.creeperhost.minetogethercommunity.gui;

import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogether.lib.chat.profile.ProfileManager;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.gui.chat.MTStyle;
import net.creeperhost.minetogethercommunity.modulargui.GuiButton;
import net.creeperhost.minetogethercommunity.modulargui.GuiElement;
import net.creeperhost.minetogethercommunity.modulargui.GuiProvider;
import net.creeperhost.minetogethercommunity.modulargui.GuiRectangle;
import net.creeperhost.minetogethercommunity.modulargui.GuiText;
import net.creeperhost.minetogethercommunity.modulargui.ModularGui;
import net.creeperhost.minetogethercommunity.modulargui.ModularGuiScreen;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MutedUsersGui implements GuiProvider {

    @Override
    public GuiElement<?> createRootElement(final ModularGui gui) {
        gui.setPauseScreen(true);
        gui.setGuiTitle(new TextComponentString(I18n.format("minetogether.gui.muted.title")));

        GuiElement<?> root = new GuiElement<>(gui);
        int margin = 28;
        int panelWidth = gui.getScreen().width - margin * 2;
        int listTop = 70;
        int listHeight = gui.getScreen().height - 112;

        new GuiRectangle(root, MTStyle.Flat.BACKGROUND).setBounds(margin, 24, panelWidth, gui.getScreen().height - 48);
        new GuiText(root, () -> I18n.format("minetogether.gui.muted.title")).centered().setBounds(margin, 34, panelWidth, 12);
        new GuiButton(root, () -> I18n.format("minetogether.gui.button.back"))
                .setBounds(gui.getScreen().width - margin - 82, 48, 74, 20)
                .onPress(() -> gui.mc().displayGuiScreen(gui.getParentScreen()));

        new MutedList(root).setBounds(margin + 8, listTop, panelWidth - 16, listHeight);
        return root;
    }

    private static class MutedList extends GuiElement<MutedList> {

        public MutedList(GuiElement<?> parent) {
            super(parent);
        }

        @Override
        protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
            drawRect(x, y, x + width, y + height, MTStyle.Flat.CONTENT_AREA);
            List<Profile> muted = mutedProfiles();
            if (muted.isEmpty()) {
                font().drawStringWithShadow(I18n.format("minetogether.gui.muted.empty"), x + 6, y + 6, 0xAAAAAA);
                return;
            }

            int rowY = y + 6;
            for (Profile profile : muted) {
                font().drawStringWithShadow(trim(MineTogetherChat.displayName(profile), width - 86), x + 6, rowY + 4, 0xE6E6E6);
                drawRect(x + width - 70, rowY, x + width - 8, rowY + 18, isUnmute(mouseX, mouseY, rowY) ? MTStyle.Flat.BUTTON_HOVER : MTStyle.Flat.BUTTON);
                drawCenteredString(font(), I18n.format("minetogether.gui.muted.unmute"), x + width - 39, rowY + 5, MTStyle.Flat.TEXT);
                rowY += 22;
                if (rowY > y + height - 18) break;
            }
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
            if (mouseButton != 0 || !isMouseOver(mouseX, mouseY)) return false;
            List<Profile> muted = mutedProfiles();
            int index = (mouseY - y - 6) / 22;
            if (index >= 0 && index < muted.size()) {
                int rowY = y + 6 + index * 22;
                if (isUnmute(mouseX, mouseY, rowY)) {
                    Profile profile = muted.get(index);
                    profile.unmute();
                    MineTogetherChat.localStatus("minetogether.gui.muted.unmuted", MineTogetherChat.displayName(profile));
                    return true;
                }
            }
            return true;
        }

        private boolean isUnmute(int mouseX, int mouseY, int rowY) {
            return mouseX >= x + width - 70 && mouseX < x + width - 8 && mouseY >= rowY && mouseY < rowY + 18;
        }

        private String trim(String value, int maxWidth) {
            if (value == null || maxWidth <= 0) return "";
            if (font().getStringWidth(value) <= maxWidth) return value;
            int dots = font().getStringWidth("...");
            return font().trimStringToWidth(value, Math.max(1, maxWidth - dots)) + "...";
        }

        private List<Profile> mutedProfiles() {
            List<Profile> profiles = new ArrayList<Profile>();
            if (MineTogetherChat.CHAT_STATE == null) return profiles;
            ProfileManager manager = MineTogetherChat.CHAT_STATE.profileManager;
            profiles.addAll(manager.getMutedProfiles());
            profiles.sort(Comparator.comparing(MineTogetherChat::displayName, String.CASE_INSENSITIVE_ORDER));
            return profiles;
        }
    }

    public static class Screen extends ModularGuiScreen {
        public Screen(GuiScreen parentScreen) {
            super(new MutedUsersGui(), parentScreen);
        }
    }
}
