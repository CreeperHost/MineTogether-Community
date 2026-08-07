package net.creeperhost.minetogethercommunity.platform;

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

}
