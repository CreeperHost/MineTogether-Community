package net.creeperhost.minetogethercommunity.connect.gui;

import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.connect.RemoteServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.resources.I18n;
import net.minecraft.realms.RealmsSharedConstants;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class FriendServerEntry implements GuiListExtended.IGuiListEntry {

    private final GuiMultiplayer owner;
    private final RemoteServer remoteServer;
    private final Profile friendProfile;
    private long lastClickTime;

    public FriendServerEntry(GuiMultiplayer owner, RemoteServer remoteServer, Profile friendProfile) {
        this.owner = owner;
        this.remoteServer = remoteServer;
        this.friendProfile = friendProfile;
    }

    public RemoteServer getRemoteServer() {
        return remoteServer;
    }

    public Profile getFriendProfile() {
        return friendProfile;
    }

    @Override
    public void updatePosition(int slotIndex, int x, int y, float partialTicks) {
    }

    @Override
    public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean selected, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        ensurePingStarted();

        String displayName = getDisplayName();
        String title = I18n.format("minetogether.connect.friend.server.title", displayName);
        String motd = remoteServer.getMotd();
        if (motd == null || motd.trim().isEmpty()) {
            motd = remoteServer.getPing() == -2L
                    ? TextFormatting.GRAY + I18n.format("multiplayer.status.pinging")
                    : I18n.format("minetogether.connect.friend.server.motd", remoteServer.getNode() == null ? "auto" : remoteServer.getNode());
        }
        String status = statusText();
        String ping = pingText(slotIndex);

        Gui.drawRect(x, y, x + 32, y + 32, 0xFF24384F);
        font.drawStringWithShadow("MT", x + 9, y + 12, 0xFFFFFF);
        font.drawString(title, x + 35, y + 1, 0xFFFFFF);

        List<String> motdLines = font.listFormattedStringToWidth(motd, listWidth - 58);
        for (int i = 0; i < Math.min(2, motdLines.size()); i++) {
            font.drawString(motdLines.get(i), x + 35, y + 12 + font.FONT_HEIGHT * i, 0x808080);
        }

        int pingWidth = font.getStringWidth(ping);
        font.drawString(ping, x + listWidth - pingWidth - 4, y + 22, 0x808080);

        int statusWidth = font.getStringWidth(status);
        int maxStatusWidth = Math.max(30, listWidth - 62 - pingWidth);
        if (statusWidth > maxStatusWidth) {
            status = font.trimStringToWidth(status, maxStatusWidth);
            statusWidth = font.getStringWidth(status);
        }
        int statusX = x + listWidth - statusWidth - 4;
        font.drawString(status, statusX, y + 1, 0x808080);

        if (mouseX >= statusX && mouseX <= x + listWidth - 4 && mouseY >= y && mouseY <= y + 10 && !remoteServer.getPlayerList().isEmpty()) {
            owner.setHoveringText(joinPlayerList(remoteServer.getPlayerList()));
        }

        if (mc.gameSettings.touchscreen || selected) {
            Gui.drawRect(x, y, x + 32, y + 32, 0xA0909090);
            int relativeX = mouseX - x;
            int color = relativeX > 16 && relativeX < 32 ? 0xFFFFFFFF : 0xFFB0C4DA;
            font.drawStringWithShadow(">", x + 21, y + 12, color);
        }
    }

    @Override
    public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
        owner.selectServer(slotIndex);
        if (relativeX > 16 && relativeX < 32 && relativeY >= 0 && relativeY <= 32) {
            ServerListAppender.INSTANCE.openSelected(owner);
            return true;
        }

        long now = Minecraft.getSystemTime();
        if (now - lastClickTime < 250L) {
            ServerListAppender.INSTANCE.openSelected(owner);
            return true;
        }
        lastClickTime = now;
        return false;
    }

    @Override
    public void mouseReleased(int slotIndex, int x, int y, int mouseEvent, int relativeX, int relativeY) {
    }

    public String getDisplayName() {
        String displayName = MineTogetherChat.displayName(friendProfile);
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = remoteServer.getFriendHash();
        }
        return displayName == null || displayName.trim().isEmpty() ? "Friend" : displayName;
    }

    private void ensurePingStarted() {
        if (remoteServer.isPinged()) return;
        remoteServer.resetPingState();
        remoteServer.setPinged(true);
        remoteServer.setPing(-2L);
        ServerListAppender.INSTANCE.pingServer(remoteServer, friendProfile);
    }

    private String statusText() {
        if (remoteServer.getPing() == -1L) {
            return TextFormatting.DARK_RED + I18n.format("multiplayer.status.cannot_connect");
        }
        if (remoteServer.getPing() == -2L) {
            return TextFormatting.GRAY + I18n.format("multiplayer.status.pinging");
        }
        if (remoteServer.getProtocol() != RealmsSharedConstants.NETWORK_PROTOCOL_VERSION) {
            return TextFormatting.RED + remoteServer.getVersion();
        }
        String status = remoteServer.getStatus();
        return TextFormatting.GRAY + (status == null || status.trim().isEmpty() ? I18n.format("minetogether.connect.friend.server.ready") : status);
    }

    private String pingText(int slotIndex) {
        long ping = remoteServer.getPing();
        if (ping == -1L) {
            return TextFormatting.DARK_RED + "X";
        }
        if (ping == -2L) {
            int frame = (int) (Minecraft.getSystemTime() / 100L + slotIndex * 2L) & 7;
            int dots = frame > 4 ? 8 - frame : frame;
            StringBuilder builder = new StringBuilder(TextFormatting.GRAY.toString());
            for (int i = 0; i < Math.max(1, dots); i++) {
                builder.append('.');
            }
            return builder.toString();
        }
        return TextFormatting.GRAY + Long.toString(ping) + "ms";
    }

    private String joinPlayerList(List<String> players) {
        StringBuilder builder = new StringBuilder();
        for (String player : players) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(player);
        }
        return builder.toString();
    }
}
