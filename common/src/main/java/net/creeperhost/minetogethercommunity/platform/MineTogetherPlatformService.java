package net.creeperhost.minetogethercommunity.platform;

import net.minecraft.network.Connection;
import net.minecraft.client.KeyMapping;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public interface MineTogetherPlatformService {

    @Nullable
    Path getModJar();

    String getVersion();

    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    boolean isClient();

    Path getGameFolder();

    Path getConfigFolder();

    void registerKeyMapping(KeyMapping keyMapping);

    void prepareClientConnection(Connection connection);
}
