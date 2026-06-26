package net.creeperhost.minetogethercommunity.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class Log4jUtils {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String[] LOGGER_NAMES = {
            "net.creeperhost.minetogethercommunity",
            "MineTogether",
            "MineTogether Chat",
            "MineTogether Community",
            "MineTogether Config",
            "MineTogether Connect",
            "MineTogether Connect GUI",
            "MineTogether LocalConfig",
            "MineTogether OAuth",
            "MineTogether Order",
            "MineTogether PackInfo",
            "MineTogether Partners",
            "MineTogether Profile",
            "MineTogether Profile Updater",
            "MineTogether Signature",
            "MineTogether Statistics",
            "MineTogether World Upload"
    };

    private Log4jUtils() {
    }

    public static void attachMTLogs(Path logsFolder) {
        try {
            File logDir = logsFolder.resolve("minetogethercommunity").toFile();
            if (!logDir.isDirectory() && !logDir.mkdirs()) {
                throw new IOException("Could not create " + logDir);
            }

            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            Configuration configuration = context.getConfiguration();

            DirectFileAppender latest = new DirectFileAppender("MTC-Latest", new File(logDir, "latest.log"), Level.INFO, "[HH:mm:ss]");
            DirectFileAppender debug = new DirectFileAppender("MTC-Debug", new File(logDir, "debug.log"), Level.ALL, "[HH:mm:ss.SSS]");

            latest.start();
            debug.start();
            configuration.addAppender(latest);
            configuration.addAppender(debug);
            for (String loggerName : LOGGER_NAMES) {
                LoggerConfig loggerConfig = new LoggerConfig(loggerName, Level.ALL, true);
                loggerConfig.addAppender(latest, Level.INFO, null);
                loggerConfig.addAppender(debug, Level.ALL, null);
                configuration.addLogger(loggerName, loggerConfig);
            }
            context.updateLoggers();
            LogManager.getLogger("MineTogether Community").info("MineTogether Community logging attached.");
        } catch (Throwable ex) {
            LOGGER.error("Unable to configure MineTogether Community logging.", ex);
        }
    }

    private static final class DirectFileAppender extends AbstractAppender {

        private final File file;
        private final Level minimumLevel;
        private final SimpleDateFormat dateFormat;
        private PrintWriter writer;

        private DirectFileAppender(String name, File file, Level minimumLevel, String datePattern) throws IOException {
            super(name, null, null, false);
            this.file = file;
            this.minimumLevel = minimumLevel;
            this.dateFormat = new SimpleDateFormat(datePattern);
            this.writer = new PrintWriter(new FileWriter(file, true), true);
        }

        @Override
        public synchronized void append(LogEvent event) {
            if (event == null || !event.getLevel().isMoreSpecificThan(minimumLevel)) {
                return;
            }
            writer.println(dateFormat.format(new Date(event.getTimeMillis()))
                    + " [" + event.getThreadName() + "/" + event.getLevel().name() + "]"
                    + " [" + event.getLoggerName() + "]: "
                    + (event.getMessage() == null ? "" : event.getMessage().getFormattedMessage()));
            if (event.getThrown() != null) {
                StringWriter stack = new StringWriter();
                event.getThrown().printStackTrace(new PrintWriter(stack));
                writer.print(stack.toString());
            }
            writer.flush();
        }

        @Override
        public synchronized void stop() {
            super.stop();
            if (writer != null) {
                writer.close();
                writer = null;
            }
        }
    }
}
