package net.creeperhost.minetogethercommunity.platform;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.Connection;

public interface MineTogetherClientPlatformService {

    void registerKeyMapping(KeyMapping keyMapping);

    void prepareConnection(Connection connection);

}
