package net.creeperhost.minetogethercommunity.compat;

import dev.architectury.platform.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

/**
 * Created by brandon3055 on 14/07/2024
 */
public class Integration {

    private static final Logger LOGGER = LogManager.getLogger();

    public static void runOptional(String modid, Supplier<Runnable> runnable) {
        if (Platform.isModLoaded(modid)) {
            try {
                runnable.get().run();
            } catch (LinkageError | RuntimeException e) {
                LOGGER.warn("Optional integration '{}' failed to initialize; continuing without it.", modid, e);
            }
        }
    }

}
