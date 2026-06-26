package net.creeperhost.minetogethercommunity.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public final class Log4jUtils {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Community");

    private Log4jUtils() {
    }

    public static void attachMTLogs(Path logsFolder) {
        LOGGER.info("MineTogether Community logging using Minecraft/Forge Log4j configuration.");
    }
}
