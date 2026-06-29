package net.creeperhost.minetogethercommunity.connect.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.creeperhost.minetogethercommunity.connect.RemoteServer;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.util.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.server.LanServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Created by brandon3055 on 21/04/2023
 */
public class FriendServerEntry extends ServerSelectionList.NetworkServerEntry {
    private static final Identifier INCOMPATIBLE_SPRITE = Identifier.withDefaultNamespace("server_list/incompatible");
    private static final Identifier UNREACHABLE_SPRITE = Identifier.withDefaultNamespace("server_list/unreachable");
    private static final Identifier PING_1_SPRITE = Identifier.withDefaultNamespace("server_list/ping_1");
    private static final Identifier PING_2_SPRITE = Identifier.withDefaultNamespace("server_list/ping_2");
    private static final Identifier PING_3_SPRITE = Identifier.withDefaultNamespace("server_list/ping_3");
    private static final Identifier PING_4_SPRITE = Identifier.withDefaultNamespace("server_list/ping_4");
    private static final Identifier PING_5_SPRITE = Identifier.withDefaultNamespace("server_list/ping_5");
    private static final Identifier PINGING_1_SPRITE = Identifier.withDefaultNamespace("server_list/pinging_1");
    private static final Identifier PINGING_2_SPRITE = Identifier.withDefaultNamespace("server_list/pinging_2");
    private static final Identifier PINGING_3_SPRITE = Identifier.withDefaultNamespace("server_list/pinging_3");
    private static final Identifier PINGING_4_SPRITE = Identifier.withDefaultNamespace("server_list/pinging_4");
    private static final Identifier PINGING_5_SPRITE = Identifier.withDefaultNamespace("server_list/pinging_5");
    private static final Identifier JOIN_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("server_list/join_highlighted");
    private static final Identifier JOIN_SPRITE = Identifier.withDefaultNamespace("server_list/join");
    private static final Component INCOMPATIBLE_TOOLTIP = Component.translatable("multiplayer.status.incompatible");
    private static final Component NO_CONNECTION_TOOLTIP = Component.translatable("multiplayer.status.no_connection");
    private static final Component PINGING_TOOLTIP = Component.translatable("multiplayer.status.pinging");

    private final JoinMultiplayerScreen screen;
    private final FaviconTexture icon;
    public final RemoteServer remoteServer;
    public final Profile friendProfile;
    private final ServerListAppender listAppender;
    @Nullable
    private byte[] lastIconBytes;

    protected FriendServerEntry(JoinMultiplayerScreen joinMultiplayerScreen, RemoteServer remoteServer, Profile friendProfile, ServerListAppender listAppender) {
        super(joinMultiplayerScreen, new LanServer(friendProfile.isFriend() ? friendProfile.getFriendName() : friendProfile.getDisplayName(), remoteServer.friend));
        this.screen = joinMultiplayerScreen;
        this.remoteServer = remoteServer;
        this.friendProfile = friendProfile;
        this.listAppender = listAppender;
        this.icon = FaviconTexture.forServer(this.minecraft.getTextureManager(), friendProfile.getFullHash().toLowerCase(Locale.ROOT));
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float f) {
        int x = getContentX();
        int y = getContentY();
        int entryWidth = getContentWidth();

        //Do Ping
        if (!remoteServer.pinged) {
            remoteServer.pinged = true;
            remoteServer.ping = -2L;
            remoteServer.motd = Component.empty();
            remoteServer.status = Component.empty();
            ServerSelectionList.THREAD_POOL.submit(() -> {
                try {
                    listAppender.pingServer(remoteServer, friendProfile);
                } catch (Exception var2) {
                    remoteServer.ping = -1L;
                }
            });
        }

        //Draw Server Title
        graphics.text(this.minecraft.font, Component.translatable("minetogether.connect.friend.server.title", getDisplayName()), x + 32 + 3, y + 1, 16777215);

        //Draw MOTD
        List<FormattedCharSequence> list = this.minecraft.font.split(this.remoteServer.motd, entryWidth - 32 - 2);
        for (int line = 0; line < Math.min(list.size(), 2); ++line) {
            Font var10000 = this.minecraft.font;
            FormattedCharSequence var10002 = list.get(line);
            int var10003 = (x + 32 + 3);
            int var10004 = y + 12;
            Objects.requireNonNull(this.minecraft.font);
            graphics.text(var10000, var10002, var10003, (var10004 + 9 * line), 8421504);
        }

        boolean versionMismatch = this.remoteServer.protocol != SharedConstants.getCurrentVersion().protocolVersion();
        //Num Players or Version Mismatch text
        Component statusText = versionMismatch ? this.remoteServer.version.copy().withStyle(ChatFormatting.RED) : this.remoteServer.status;
        //Draw Status
        int statusWidth = this.minecraft.font.width(statusText);
        graphics.text(this.minecraft.font, statusText, (x + entryWidth - statusWidth - 15 - 2), (y + 1), 8421504);

        Identifier statusIcon = null;
        List<Component> playersToolTip;
        Component statusToolTip;
        if (versionMismatch) {
            statusIcon = INCOMPATIBLE_SPRITE;
            statusToolTip = INCOMPATIBLE_TOOLTIP;
            playersToolTip = this.remoteServer.playerList;
        } else if (this.remoteServer.pinged && this.remoteServer.ping != -2L) {
            if (this.remoteServer.ping < 150L) {
                statusIcon = PING_5_SPRITE;
            } else if (this.remoteServer.ping < 300L) {
                statusIcon = PING_4_SPRITE;
            } else if (this.remoteServer.ping < 600L) {
                statusIcon = PING_3_SPRITE;
            } else if (this.remoteServer.ping < 1000L) {
                statusIcon = PING_2_SPRITE;
            } else {
                statusIcon = PING_1_SPRITE;
            }

            if (this.remoteServer.ping < 0L) {
                statusToolTip = NO_CONNECTION_TOOLTIP;
                statusIcon = UNREACHABLE_SPRITE;
                playersToolTip = Collections.emptyList();
            } else {
                statusToolTip = Component.translatable("multiplayer.status.ping", this.remoteServer.ping);
                playersToolTip = this.remoteServer.playerList;
            }
        } else {
            int entryIndex = listAppender.getServerList() == null ? 0 : listAppender.getServerList().children().indexOf(this);
            int time = (int)(Util.getMillis() / 100L + (long)(entryIndex * 2) & 7L);
            if (time > 4) time = 8 - time;
            switch (time) {
                case 1 -> statusIcon = PINGING_2_SPRITE;
                case 2 -> statusIcon = PINGING_3_SPRITE;
                case 3 -> statusIcon = PINGING_4_SPRITE;
                case 4 -> statusIcon = PINGING_5_SPRITE;
                default -> statusIcon = PINGING_1_SPRITE;
            }

            statusToolTip = PINGING_TOOLTIP;
            playersToolTip = Collections.emptyList();
        }

        //Draw Signal / Scanning Bars.
        if (statusIcon != null) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, statusIcon, x + entryWidth - 15, y, 10, 8);
        }

        //Update server icon.
        byte[] bs = this.remoteServer.getIconBytes();
        if (!Arrays.equals(bs, this.lastIconBytes)) {
            if (this.uploadServerIcon(bs)) {
                this.lastIconBytes = bs;
            } else {
                this.remoteServer.setIconBytes(null);
            }
        }

        this.drawIcon(graphics, x, y, this.icon.textureLocation());

        int t = mouseX - x;
        int u = mouseY - y;
        if (t >= entryWidth - 15 && t <= entryWidth - 5 && u >= 0 && u <= 8) {
            //Draw Status Tool Tip
            graphics.setTooltipForNextFrame(statusToolTip, mouseX, mouseY);
        } else if (t >= entryWidth - statusWidth - 15 - 2 && t <= entryWidth - 15 - 2 && u >= 0 && u <= 8) {
            //Draw Players Tool Tip
            if (playersToolTip != null) {
                graphics.setTooltipForNextFrame(playersToolTip.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
            }
        }

        if (this.minecraft.options.touchscreen().get() || hovered) {
            graphics.fill(x, y, x + 32, y + 32, 0xa0909090);
            int v = mouseX - x;
            //Draw "Join Arrow"
            if (v < 32 && v > 16) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, JOIN_HIGHLIGHTED_SPRITE, x, y, 32, 32);
            } else {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, JOIN_SPRITE, x, y, 32, 32);
            }
        }
    }

    public String getDisplayName() {
        return friendProfile.isFriend() ? friendProfile.getFriendName() : friendProfile.getDisplayName();
    }

    protected void drawIcon(GuiGraphicsExtractor graphics, int i, int j, Identifier resourceLocation) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, resourceLocation, i, j, 0.0F, 0.0F, 32, 32, 32, 32);
    }

    private boolean uploadServerIcon(@Nullable byte[] bs) {
        if (bs == null) {
            this.icon.clear();
        } else {
            try {
                this.icon.upload(NativeImage.read(bs));
            } catch (Throwable var3) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void join() {
        if (this.remoteServer.shouldWarnBeforeJoin()) {
            this.minecraft.setScreen(new ConnectPackWarningScreen.Screen(this.screen, this.remoteServer, this.serverData));
        } else {
            FriendConnectScreen.startConnecting(this.screen, this.minecraft, this.remoteServer, this.serverData);
        }
    }

    public LanServer getServerData() {
        return this.serverData;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int relX = (int) event.x() - this.getContentX();
        int relY = (int) event.y() - this.getContentY();
        if (relX < 32 && relX > 16 && relY >= 0 && relY < 32) {
            this.join();
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }
}
