package net.creeperhost.minetogethercommunity;

import net.creeperhost.minetogethercommunity.platform.MineTogetherClientPlatformService;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.Connection;

import java.util.ServiceLoader;

/**
 * Client-only platform operations. This class must only be reached from client code.
 */
public final class MineTogetherClientPlatform {

    private static final MineTogetherClientPlatformService SERVICE = ServiceLoader.load(MineTogetherClientPlatformService.class)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No MineTogether client platform service loaded."));

    public static void registerKeyMapping(KeyMapping keyMapping) {
        SERVICE.registerKeyMapping(keyMapping);
    }

    public static void prepareConnection(Connection connection) {
        SERVICE.prepareConnection(connection);
    }

    private MineTogetherClientPlatform() {
    }
}
