package net.creeperhost.minetogethercommunity.util;

import org.apache.logging.log4j.Logger;

public final class LegacyLog4jCompat {
    private LegacyLog4jCompat() {
    }

    public static void trace(Logger logger, String message, Object arg0) {
        log(logger, "trace", message, new Object[]{arg0});
    }

    public static void trace(Logger logger, String message, Object arg0, Object arg1) {
        log(logger, "trace", message, new Object[]{arg0, arg1});
    }

    public static void trace(Logger logger, String message, Object arg0, Object arg1, Object arg2) {
        log(logger, "trace", message, new Object[]{arg0, arg1, arg2});
    }

    public static void trace(Logger logger, String message, Object arg0, Object arg1, Object arg2, Object arg3) {
        log(logger, "trace", message, new Object[]{arg0, arg1, arg2, arg3});
    }

    public static void debug(Logger logger, String message, Object arg0) {
        log(logger, "debug", message, new Object[]{arg0});
    }

    public static void debug(Logger logger, String message, Object arg0, Object arg1) {
        log(logger, "debug", message, new Object[]{arg0, arg1});
    }

    public static void debug(Logger logger, String message, Object arg0, Object arg1, Object arg2) {
        log(logger, "debug", message, new Object[]{arg0, arg1, arg2});
    }

    public static void debug(Logger logger, String message, Object arg0, Object arg1, Object arg2, Object arg3) {
        log(logger, "debug", message, new Object[]{arg0, arg1, arg2, arg3});
    }

    public static void info(Logger logger, String message, Object arg0) {
        log(logger, "info", message, new Object[]{arg0});
    }

    public static void info(Logger logger, String message, Object arg0, Object arg1) {
        log(logger, "info", message, new Object[]{arg0, arg1});
    }

    public static void info(Logger logger, String message, Object arg0, Object arg1, Object arg2) {
        log(logger, "info", message, new Object[]{arg0, arg1, arg2});
    }

    public static void info(Logger logger, String message, Object arg0, Object arg1, Object arg2, Object arg3) {
        log(logger, "info", message, new Object[]{arg0, arg1, arg2, arg3});
    }

    public static void warn(Logger logger, String message, Object arg0) {
        log(logger, "warn", message, new Object[]{arg0});
    }

    public static void warn(Logger logger, String message, Object arg0, Object arg1) {
        log(logger, "warn", message, new Object[]{arg0, arg1});
    }

    public static void warn(Logger logger, String message, Object arg0, Object arg1, Object arg2) {
        log(logger, "warn", message, new Object[]{arg0, arg1, arg2});
    }

    public static void warn(Logger logger, String message, Object arg0, Object arg1, Object arg2, Object arg3) {
        log(logger, "warn", message, new Object[]{arg0, arg1, arg2, arg3});
    }

    public static void error(Logger logger, String message, Object arg0) {
        log(logger, "error", message, new Object[]{arg0});
    }

    public static void error(Logger logger, String message, Object arg0, Object arg1) {
        log(logger, "error", message, new Object[]{arg0, arg1});
    }

    public static void error(Logger logger, String message, Object arg0, Object arg1, Object arg2) {
        log(logger, "error", message, new Object[]{arg0, arg1, arg2});
    }

    public static void error(Logger logger, String message, Object arg0, Object arg1, Object arg2, Object arg3) {
        log(logger, "error", message, new Object[]{arg0, arg1, arg2, arg3});
    }

    public static void fatal(Logger logger, String message, Object arg0) {
        log(logger, "fatal", message, new Object[]{arg0});
    }

    public static void fatal(Logger logger, String message, Object arg0, Object arg1) {
        log(logger, "fatal", message, new Object[]{arg0, arg1});
    }

    public static void fatal(Logger logger, String message, Object arg0, Object arg1, Object arg2) {
        log(logger, "fatal", message, new Object[]{arg0, arg1, arg2});
    }

    public static void fatal(Logger logger, String message, Object arg0, Object arg1, Object arg2, Object arg3) {
        log(logger, "fatal", message, new Object[]{arg0, arg1, arg2, arg3});
    }

    private static void log(Logger logger, String level, String message, Object[] args) {
        if (logger == null) {
            return;
        }
        if ("trace".equals(level)) {
            logger.trace(message, args);
        } else if ("debug".equals(level)) {
            logger.debug(message, args);
        } else if ("info".equals(level)) {
            logger.info(message, args);
        } else if ("warn".equals(level)) {
            logger.warn(message, args);
        } else if ("error".equals(level)) {
            logger.error(message, args);
        } else if ("fatal".equals(level)) {
            logger.fatal(message, args);
        }
    }
}
