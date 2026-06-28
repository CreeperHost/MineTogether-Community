package net.creeperhost.minetogethercommunity.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.chat.ChatTarget;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class LocalConfig {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether LocalConfig");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static LocalConfig INSTANCE;
    private static File file;

    public boolean chatEnabled = true;
    public boolean friendNotifications = true;
    public boolean chatSettingsSliders = true;
    public boolean mainMenuButtons = true;
    public boolean shiftClickMention = true;
    public Set<String> firstConnect = new HashSet<>();
    public ChatTarget selectedTab = ChatTarget.PUBLIC;
    public boolean activityTelemetry = true;
    public boolean connectPackPrompted = false;
    public boolean connectPackBypass = false;
    public String connectPackKey = "";
    public String connectPackDisplayName = "";
    public String connectPackProjectType = "";
    public String connectPackProjectId = "";
    public String connectPackProjectVersion = "";
    public String connectPackMinecraftVersion = "";
    public int connectPackCreeperHostVersionId = -1;

    public static synchronized LocalConfig instance() {
        if (INSTANCE == null) {
            file = new File(new File(MineTogether.getGameDir(), "local/minetogether"), MineTogether.MOD_ID + ".json");
            INSTANCE = load(file);
            save();
        }
        return INSTANCE;
    }

    public static void save() {
        if (file == null || INSTANCE == null) return;
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            LOGGER.warn("Failed to create local config directory {}", parent);
            return;
        }
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException ex) {
            LOGGER.error("Failed to save local config {}", file, ex);
        }
    }

    private static LocalConfig load(File file) {
        if (file.isFile()) {
            try (FileReader reader = new FileReader(file)) {
                LocalConfig loaded = GSON.fromJson(reader, LocalConfig.class);
                if (loaded != null) return sanitize(loaded);
            } catch (IOException ex) {
                LOGGER.error("Failed to read local config {}, using defaults", file, ex);
            }
        }
        return new LocalConfig();
    }

    private static LocalConfig sanitize(LocalConfig config) {
        if (config.firstConnect == null) {
            config.firstConnect = new HashSet<>();
        }
        if (config.selectedTab == null) {
            config.selectedTab = ChatTarget.PUBLIC;
        }
        if (config.connectPackKey == null) config.connectPackKey = "";
        if (config.connectPackDisplayName == null) config.connectPackDisplayName = "";
        if (config.connectPackProjectType == null) config.connectPackProjectType = "";
        if (config.connectPackProjectId == null) config.connectPackProjectId = "";
        if (config.connectPackProjectVersion == null) config.connectPackProjectVersion = "";
        if (config.connectPackMinecraftVersion == null) config.connectPackMinecraftVersion = "";
        return config;
    }
}
