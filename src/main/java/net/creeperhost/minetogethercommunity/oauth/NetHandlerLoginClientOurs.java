package net.creeperhost.minetogethercommunity.oauth;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerLoginClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.text.ITextComponent;

public class NetHandlerLoginClientOurs extends NetHandlerLoginClient {

    public NetHandlerLoginClientOurs(NetworkManager networkManager, Minecraft minecraft) {
        super(networkManager, minecraft, null);
    }

    @Override
    public void onDisconnect(ITextComponent reason) {
        ServerAuthTest.disconnected(reason == null ? "" : reason.getUnformattedText());
    }
}
