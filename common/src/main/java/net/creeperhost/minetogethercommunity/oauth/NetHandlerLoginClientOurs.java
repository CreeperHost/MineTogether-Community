package net.creeperhost.minetogethercommunity.oauth;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;

public class NetHandlerLoginClientOurs extends ClientHandshakePacketListenerImpl {

    public NetHandlerLoginClientOurs(Connection connection, Minecraft mcIn) {
        super(connection, mcIn, new ServerData("", "", ServerData.Type.OTHER), null, false, null, e -> { }, new LevelLoadTracker(), (TransferState) null);
    }

    @Override
    public void onDisconnect(DisconnectionDetails details) {
        ServerAuthTest.disconnected(details.reason().getString());
        // NO-OP
    }
}
