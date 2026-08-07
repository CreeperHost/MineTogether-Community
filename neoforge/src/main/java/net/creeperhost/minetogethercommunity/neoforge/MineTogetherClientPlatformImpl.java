package net.creeperhost.minetogethercommunity.neoforge;

import net.creeperhost.minetogethercommunity.platform.MineTogetherClientPlatformService;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.Connection;

import java.util.ArrayList;
import java.util.List;

public final class MineTogetherClientPlatformImpl implements MineTogetherClientPlatformService {

    private static final List<KeyMapping> KEY_MAPPINGS = new ArrayList<>();

    static List<KeyMapping> getKeyMappings() {
        return KEY_MAPPINGS;
    }

    @Override
    public void registerKeyMapping(KeyMapping keyMapping) {
        KEY_MAPPINGS.add(keyMapping);
    }

    @Override
    public void prepareConnection(Connection connection) {
        // Not required on NeoForge.
    }

}
