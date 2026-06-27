package net.creeperhost.minetogethercommunity.util;

import net.creeperhost.minetogethercommunity.config.Config;
import org.apache.logging.log4j.Logger;

public final class DiagnosticLog {

    private DiagnosticLog() {
    }

    public static boolean enabled() {
        return Config.instance().debugMode;
    }

    public static void debug(Logger logger, String message, Object... args) {
        if (enabled()) {
            logger.debug(message, args);
        }
    }

    public static void info(Logger logger, String message, Object... args) {
        if (enabled()) {
            logger.info(message, args);
        }
    }

    public static void warn(Logger logger, String message, Object... args) {
        if (enabled()) {
            logger.warn(message, args);
        }
    }

    public static void error(Logger logger, String message, Object... args) {
        if (enabled()) {
            logger.error(message, args);
        }
    }
}
