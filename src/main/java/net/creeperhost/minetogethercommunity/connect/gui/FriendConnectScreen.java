package net.creeperhost.minetogethercommunity.connect.gui;

import net.creeperhost.minetogether.session.JWebToken;
import net.creeperhost.minetogethercommunity.connect.ConnectHandler;
import net.creeperhost.minetogethercommunity.connect.ConnectHost;
import net.creeperhost.minetogethercommunity.connect.RemoteServer;
import net.creeperhost.minetogethercommunity.connect.netty.NettyClient;
import net.creeperhost.minetogethercommunity.util.DiagnosticLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetHandlerLoginClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.realms.RealmsSharedConstants;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.client.FMLClientHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class FriendConnectScreen extends GuiScreen {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Connect");
    private static final AtomicInteger CONNECTION_ID = new AtomicInteger();

    private final GuiScreen previousGuiScreen;
    private final RemoteServer remoteServer;
    private NetworkManager networkManager;
    private boolean cancel;
    private boolean connectingStarted;
    private boolean handledDisconnect;

    public FriendConnectScreen(GuiScreen previousGuiScreen, RemoteServer remoteServer) {
        this.previousGuiScreen = previousGuiScreen;
        this.remoteServer = remoteServer;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(0, width / 2 - 100, height / 4 + 132, I18n.format("gui.cancel")));
        if (!connectingStarted && networkManager == null && !cancel) {
            startConnecting();
        }
    }

    private void startConnecting() {
        connectingStarted = true;
        final Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.loadWorld(null);
        minecraft.setServerData(new ServerData("MineTogether Friend Server", "mtconnect", false));
        FMLClientHandler.instance().connectToRealmsServer("mtconnect", 0);
        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] prepared Forge play-client latch for friend-server connection friend={} node={}",
                remoteServer.getFriendHash(), remoteServer.getNode() == null ? "<auto>" : remoteServer.getNode());

        Thread thread = new Thread("MT Friend Server Connector #" + CONNECTION_ID.incrementAndGet()) {
            @Override
            public void run() {
                try {
                    if (cancel) return;
                    ConnectHost endpoint = ConnectHandler.getSpecificEndpoint(remoteServer.getNode());
                    JWebToken token = ConnectHandler.requireSessionToken();
                    DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] opening friend-server connection friend={} node={} endpoint={}:{}",
                            remoteServer.getFriendHash(),
                            remoteServer.getNode() == null ? "<auto>" : remoteServer.getNode(),
                            endpoint.getAddress(),
                            Integer.valueOf(endpoint.getProxyPort()));
                    networkManager = NettyClient.connect(endpoint, token, remoteServer.getServerToken(), false);
                    DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] friend-server raw connection ready friend={} node={}",
                            remoteServer.getFriendHash(), remoteServer.getNode() == null ? "<auto>" : remoteServer.getNode());
                    if (cancel) {
                        networkManager.closeChannel(new TextComponentString("Aborted"));
                        return;
                    }
                    networkManager.setNetHandler(new NetHandlerLoginClient(networkManager, minecraft, previousGuiScreen));
                    networkManager.sendPacket(new C00Handshake(RealmsSharedConstants.NETWORK_PROTOCOL_VERSION, endpoint.getAddress(), endpoint.getProxyPort(), EnumConnectionState.LOGIN));
                    networkManager.sendPacket(new C00PacketLoginStart(minecraft.getSession().getProfile()));
                } catch (Exception ex) {
                    if (cancel) return;
                    LOGGER.error("Couldn't connect to MineTogether friend server", ex);
                    showDisconnect(ex);
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    private void showDisconnect(final Exception ex) {
        net.creeperhost.minetogethercommunity.util.ClientTaskRunner.run(new Runnable() {
            @Override
            public void run() {
                String message = ex instanceof IOException && ex.getMessage() != null ? ex.getMessage() : ex.toString();
                Minecraft.getMinecraft().displayGuiScreen(new GuiDisconnected(previousGuiScreen, "connect.failed", new TextComponentTranslation("disconnect.genericReason", message)));
            }
        });
    }

    @Override
    public void updateScreen() {
        if (networkManager != null) {
            if (networkManager.isChannelOpen()) {
                networkManager.processReceivedPackets();
            } else if (!handledDisconnect) {
                handledDisconnect = true;
                IChatComponent reason = networkManager.getExitMessage();
                if (reason == null) {
                    reason = new TextComponentTranslation("disconnect.endOfStream");
                }
                DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] friend-server connection closed friend={} reason={}",
                        remoteServer.getFriendHash(), reason.getUnformattedText());
                if (networkManager.getNetHandler() != null) {
                    networkManager.getNetHandler().onDisconnect(reason);
                }
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            cancel = true;
            if (networkManager != null) {
                networkManager.closeChannel(new TextComponentString("Aborted"));
            }
            mc.displayGuiScreen(previousGuiScreen);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format(networkManager == null ? "connect.connecting" : "connect.authorizing"), width / 2, height / 2 - 50, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
