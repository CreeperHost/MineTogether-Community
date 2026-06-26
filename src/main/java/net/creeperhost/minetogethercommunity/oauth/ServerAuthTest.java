package net.creeperhost.minetogethercommunity.oauth;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.realms.RealmsSharedConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerAuthTest {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether OAuth");
    private static final AtomicInteger CONNECTION_ID = new AtomicInteger();
    private static final Pattern CODE_PATTERN = Pattern.compile("code: (\\w{5})");

    private static volatile boolean cancel;
    private static NetworkManager networkManager;
    private static BiFunction<Boolean, String, Void> callback;

    private ServerAuthTest() {
    }

    public static void auth(BiFunction<Boolean, String, Void> callbackIn) {
        cancel = false;
        callback = callbackIn;
        final Minecraft mc = Minecraft.getMinecraft();
        final String address = "mc.auth.minetogether.io";
        final int port = 25565;

        new Thread("MT OAuth Server Auth #" + CONNECTION_ID.incrementAndGet()) {
            @Override
            public void run() {
                try {
                    if (cancel) return;
                    InetSocketAddress socketAddress = new InetSocketAddress(InetAddress.getByName(address), port);
                    networkManager = NetworkManager.provideLanClient(socketAddress.getAddress(), socketAddress.getPort());
                    networkManager.setNetHandler(new NetHandlerLoginClientOurs(networkManager, mc));
                    networkManager.scheduleOutboundPacket(new C00Handshake(RealmsSharedConstants.NETWORK_PROTOCOL_VERSION, address, port, EnumConnectionState.LOGIN));
                    UUID uuid = mc.getSession().getProfile().getId();
                    networkManager.scheduleOutboundPacket(new C00PacketLoginStart(new GameProfile(uuid, mc.getSession().getUsername())));
                } catch (UnknownHostException ex) {
                    if (!cancel) {
                        LOGGER.error("Could not resolve MineTogether auth server", ex);
                        fireCallback(false, "Unknown Host");
                    }
                } catch (Throwable ex) {
                    if (!cancel) {
                        LOGGER.error("Could not connect to MineTogether auth server", ex);
                        fireCallback(false, ex.getMessage());
                    }
                }
            }
        }.start();
    }

    public static void cancel() {
        cancel = true;
        if (networkManager != null) {
            networkManager.closeChannel(new net.minecraft.util.text.TextComponentString("Cancelled"));
            networkManager = null;
        }
        callback = null;
    }

    public static void processPackets() {
        NetworkManager manager = networkManager;
        if (manager == null) return;
        if (manager.isChannelOpen()) {
            manager.processReceivedPackets();
        } else {
        }
    }

    public static void disconnected(String reason) {
        Matcher matcher = CODE_PATTERN.matcher(reason == null ? "" : reason);
        if (matcher.find()) {
            fireCallback(true, matcher.group(1));
        } else {
            fireCallback(false, reason);
        }
        networkManager = null;
    }

    private static void fireCallback(boolean status, String message) {
        BiFunction<Boolean, String, Void> cb = callback;
        callback = null;
        if (cb != null) {
            cb.apply(status, message);
        }
    }
}
