package net.creeperhost.minetogethercommunity.ci.forge;

import net.creeperhost.minetogethercommunity.ci.MineTogetherCiProbe;
import net.minecraftforge.fml.common.Mod;

@Mod("minetogether_ci_probe")
public final class MineTogetherCiProbeForge {
    public MineTogetherCiProbeForge() {
        MineTogetherCiProbe.init();
    }
}
