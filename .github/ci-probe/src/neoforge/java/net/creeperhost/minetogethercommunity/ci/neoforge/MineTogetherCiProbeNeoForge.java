package net.creeperhost.minetogethercommunity.ci.neoforge;

import net.creeperhost.minetogethercommunity.ci.MineTogetherCiProbe;
import net.neoforged.fml.common.Mod;

@Mod("minetogether_ci_probe")
public final class MineTogetherCiProbeNeoForge {
    public MineTogetherCiProbeNeoForge() {
        MineTogetherCiProbe.init();
    }
}
