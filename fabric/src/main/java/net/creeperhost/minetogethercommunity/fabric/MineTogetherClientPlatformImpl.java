package net.creeperhost.minetogethercommunity.fabric;

import net.creeperhost.minetogethercommunity.platform.MineTogetherClientPlatformService;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.Connection;

public final class MineTogetherClientPlatformImpl implements MineTogetherClientPlatformService {

    @Override
    public void registerKeyMapping(KeyMapping keyMapping) {
        KeyMappingHelper.registerKeyMapping(keyMapping);
    }

    @Override
    public void prepareConnection(Connection connection) {
    }

}
