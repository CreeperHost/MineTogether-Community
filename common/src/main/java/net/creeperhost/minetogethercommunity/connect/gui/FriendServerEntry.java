package net.creeperhost.minetogethercommunity.connect.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.creeperhost.minetogethercommunity.connect.RemoteServer;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.server.LanServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Created by brandon3055 on 21/04/2023
 */
public class FriendServerEntry extends ServerSelectionList.NetworkServerEntry {
    private static final ResourceLocation ICONS_LOCATION = new ResourceLocation("textures/gui/icons.png");
    private static final ResourceLocation SERVER_SELECTION_LOCATION = new ResourceLocation("textures/gui/server_selection.png");
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
        super(joinMultiplayerScreen, new LanServer("Dummy Server", "0.0.0.0"));
        this.screen = joinMultiplayerScreen;
        this.remoteServer = remoteServer;
        this.friendProfile = friendProfile;
        this.listAppender = listAppender;
        this.icon = FaviconTexture.forServer(this.minecraft.getTextureManager(), friendProfile.getFullHash().toLowerCase(Locale.ROOT));
    }

    @Override                                                 //Yes. y, then x. This is correct. wtf...
    public void render(GuiGraphics graphics, int entryIndex, int y, int x, int entryWidth, int m, int mouseX, int mouseY, boolean selected, float f) {
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
        graphics.drawString(this.minecraft.font, Component.translatable("minetogether.connect.friend.server.title", getDisplayName()), x + 32 + 3, y + 1, 16777215);

        //Draw MOTD
        List<FormattedCharSequence> list = this.minecraft.font.split(this.remoteServer.motd, entryWidth - 32 - 2);
        for (int line = 0; line < Math.min(list.size(), 2); ++line) {
            Font var10000 = this.minecraft.font;
            FormattedCharSequence var10002 = list.get(line);
            int var10003 = (x + 32 + 3);
            int var10004 = y + 12;
            Objects.requireNonNull(this.minecraft.font);
            graphics.drawString(var10000, var10002, var10003, (var10004 + 9 * line), 8421504);
        }

        boolean versionMismatch = this.remoteServer.protocol != SharedConstants.getCurrentVersion().getProtocolVersion();
        //Num Players or Version Mismatch text
        Component statusText = versionMismatch ? this.remoteServer.version.copy().withStyle(ChatFormatting.RED) : this.remoteServer.status;
        //Draw Status
        int statusWidth = this.minecraft.font.width(statusText);
        graphics.drawString(this.minecraft.font, statusText, (x + entryWidth - statusWidth - 15 - 2), (y + 1), 8421504);

        int statusIcon = 0;
        boolean scanning = false;
        List<Component> playersToolTip;
        Component statusToolTip;
        if (versionMismatch) {
            statusIcon = 5;
            statusToolTip = INCOMPATIBLE_TOOLTIP;
            playersToolTip = this.remoteServer.playerList;
        } else if (this.remoteServer.pinged && this.remoteServer.ping != -2L) {
            if (this.remoteServer.ping < 150L) {
                statusIcon = 0;
            } else if (this.remoteServer.ping < 300L) {
                statusIcon = 1;
            } else if (this.remoteServer.ping < 600L) {
                statusIcon = 2;
            } else if (this.remoteServer.ping < 1000L) {
                statusIcon = 3;
            } else {
                statusIcon = 4;
            }

            if (this.remoteServer.ping < 0L) {
                statusToolTip = NO_CONNECTION_TOOLTIP;
                statusIcon = 5;
                playersToolTip = Collections.emptyList();
            } else {
                statusToolTip = Component.translatable("multiplayer.status.ping", this.remoteServer.ping);
                playersToolTip = this.remoteServer.playerList;
            }
        } else {
            scanning = true;
            statusIcon = (int) (Util.getMillis() / 100L + (long) (entryIndex * 2) & 7L);
            if (statusIcon > 4) statusIcon = 8 - statusIcon;

            statusToolTip = PINGING_TOOLTIP;
            playersToolTip = Collections.emptyList();
        }

        //Draw Signal / Scanning Bars.
        graphics.blit(ICONS_LOCATION, x + entryWidth - 15, y, scanning ? 10.0F : 0.0F, 176.0F + statusIcon * 8.0F, 10, 8, 256, 256);

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
            this.screen.setTooltipForNextRenderPass(Collections.singletonList(statusToolTip.getVisualOrderText()));
        } else if (t >= entryWidth - statusWidth - 15 - 2 && t <= entryWidth - 15 - 2 && u >= 0 && u <= 8) {
            //Draw Players Tool Tip
            if (playersToolTip != null) {
                this.screen.setTooltipForNextRenderPass(playersToolTip.stream().map(Component::getVisualOrderText).toList());
            }
        }

        if (this.minecraft.options.touchscreen().get() || selected) {
            graphics.fill(x, y, x + 32, y + 32, 0xa0909090);
            int v = mouseX - x;
            //Draw "Join Arrow"
            if (v < 32 && v > 16) {
                graphics.blit(SERVER_SELECTION_LOCATION, x, y, 0.0F, 32.0F, 32, 32, 256, 256);
            } else {
                graphics.blit(SERVER_SELECTION_LOCATION, x, y, 0.0F, 0.0F, 32, 32, 256, 256);
            }
        }
    }

    public String getDisplayName() {
        return friendProfile.isFriend() ? friendProfile.getFriendName() : friendProfile.getDisplayName();
    }

    protected void drawIcon(GuiGraphics graphics, int i, int j, ResourceLocation resourceLocation) {
        graphics.blit(resourceLocation, i, j, 0.0F, 0.0F, 32, 32, 32, 32);
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (listAppender.getServerList() != null) {
            double f = mouseX - listAppender.getServerList().getRowLeft();
            if (f < 32.0 && f > 16.0) {
                this.screen.setSelected(this);
                this.screen.joinSelectedServer();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
