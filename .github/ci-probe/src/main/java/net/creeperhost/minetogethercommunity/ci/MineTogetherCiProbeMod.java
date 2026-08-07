package net.creeperhost.minetogethercommunity.ci;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(
        modid = "minetogether_ci_probe",
        name = "MineTogether CI Probe",
        version = "1.0.0",
        clientSideOnly = true,
        acceptableRemoteVersions = "*",
        dependencies = "required-after:minetogethercommunity"
)
public final class MineTogetherCiProbeMod {

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MineTogetherCiProbe.init();
    }
}
