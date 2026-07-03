package net.creeperhost.minetogethercommunity.proxy;

import net.creeperhost.minetogethercommunity.connect.DedicatedServerConnect;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteNetworking;
import net.minecraft.entity.player.EntityPlayerMP;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
    }

    public void init(FMLInitializationEvent event) {
        EmoteNetworking.init(false);
        FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent
    public void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            DedicatedServerConnect.playerJoined((EntityPlayerMP) event.player);
        }
    }
}
