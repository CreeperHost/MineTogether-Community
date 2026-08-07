package net.creeperhost.minetogethercommunity.ci.forge;

import net.creeperhost.minetogethercommunity.ci.MineTogetherCiProbe;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod(
        modid = "minetogether_ci_probe",
        name = "MineTogether CI Probe",
        version = "1",
        clientSideOnly = true,
        acceptableRemoteVersions = "*"
)
public final class MineTogetherCiProbeForge {

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MineTogetherCiProbe.init();
        FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MineTogetherCiProbe.tick(Minecraft.getMinecraft());
        }
    }
}
