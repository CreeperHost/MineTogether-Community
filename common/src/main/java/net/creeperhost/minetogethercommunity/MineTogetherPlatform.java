package net.creeperhost.minetogethercommunity;

import net.creeperhost.minetogethercommunity.platform.MineTogetherPlatformService;
import net.minecraft.SharedConstants;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ServiceLoader;

/**
 * Created by covers1624 on 26/8/22.
 */
public class MineTogetherPlatform {

    private static final MineTogetherPlatformService SERVICE = ServiceLoader.load(MineTogetherPlatformService.class)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No MineTogether platform service loaded."));

    @Nullable
    public static Path getModJar() {
        return SERVICE.getModJar();
    }

    public static String getVersion() {
        return SERVICE.getVersion();
    }

    public static String getMinecraftVersion() {
        return SharedConstants.getCurrentVersion().name();
    }

    public static String getPlatformName() {
        return SERVICE.getPlatformName();
    }

    public static boolean isModLoaded(String modId) {
        return SERVICE.isModLoaded(modId);
    }

    public static boolean isDevelopmentEnvironment() {
        return SERVICE.isDevelopmentEnvironment();
    }

    public static boolean isClient() {
        return SERVICE.isClient();
    }

    public static Path getGameFolder() {
        return SERVICE.getGameFolder();
    }

    public static Path getConfigFolder() {
        return SERVICE.getConfigFolder();
    }

}
