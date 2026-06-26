package net.creeperhost.minetogethercommunity.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class Config {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static Config INSTANCE = new Config();
    private static File file;

    public String curseProjectID = "";
    public boolean moveButtonsOnPauseMenu = true;
    public boolean logChatToConsole = false;
    public boolean debugMode = false;
    public boolean dumpConnectPackets = false;
    public boolean pauseScreenButtons = true;
    public String issueTrackerUrl = "https://pste.ch/";

    public static Config instance() {
        return INSTANCE;
    }

    public static void load(File configFile) {
        file = configFile;
        if (file.isFile()) {
            try (FileReader reader = new FileReader(file)) {
                Config loaded = GSON.fromJson(reader, Config.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                }
            } catch (IOException ex) {
                LOGGER.error("Failed to read config file {}, using defaults", file, ex);
                INSTANCE = new Config();
            }
        }
        save();
    }

    public static void save() {
        if (file == null) return;
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            LOGGER.warn("Failed to create config directory {}", parent);
            return;
        }
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException ex) {
            LOGGER.error("Failed to save config file {}", file, ex);
        }
    }
}
