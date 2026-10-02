package net.creeperhost.minetogethercommunity.tests.neoforge;

import net.creeperhost.minetogethercommunity.tests.ServerHealthCheck;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(value = "minetogether_server_test", dist = Dist.DEDICATED_SERVER)
public final class DedicatedServerTestNeoForge {
    public DedicatedServerTestNeoForge() {
        var check = new ServerHealthCheck();
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> check.started());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> check.tick(() -> event.getServer().halt(false)));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> check.stopped());
    }
}
