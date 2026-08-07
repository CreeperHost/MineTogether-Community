package net.creeperhost.minetogethercommunity.ci;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;

@Mod(modid = "minetogether_ci_probe", name = "MineTogether CI Probe", version = "1",
        acceptedMinecraftVersions = "[1.7.10]", acceptableRemoteVersions = "*",
        dependencies = "after:minetogethercommunity")
public final class MineTogetherCiProbeForge {
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MineTogetherCiProbe.init();
    }
}
