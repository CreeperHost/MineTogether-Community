package net.creeperhost.minetogethercommunity.tests.fabric;

import net.creeperhost.minetogethercommunity.tests.ServerHealthCheck;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class DedicatedServerTestFabric implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        var check = new ServerHealthCheck();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> check.started());
        ServerTickEvents.END_SERVER_TICK.register(server -> check.tick(() -> server.halt(false)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> check.stopped());
    }
}
