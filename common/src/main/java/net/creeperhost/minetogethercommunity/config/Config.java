package net.creeperhost.minetogethercommunity.config;

import blue.endless.jankson.*;
import blue.endless.jankson.api.SyntaxError;
import net.creeperhost.minetogethercommunity.chat.ChatTarget;
import net.creeperhost.minetogethercommunity.MineTogetherPlatform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static net.creeperhost.minetogethercommunity.MineTogether.MOD_ID;

/**
 * Created by covers1624 on 20/6/22.
 */
public class Config {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Jankson JANKSON = Jankson.builder()
            .registerSerializer(Path.class, (p, m) -> JsonPrimitive.of(p.toAbsolutePath().toString()))
            .registerDeserializer(JsonPrimitive.class, Path.class, (p, m) -> Paths.get(p.asString()))
            .build();
    private static final JsonGrammar GRAMMAR = JsonGrammar.JSON5;

    @Nullable
    private static Config INSTANCE;
    @Nullable
    private static Path filePath;

    public static Config instance() {
        if (INSTANCE == null) {
            loadConfig(MineTogetherPlatform.getConfigFolder().resolve(MOD_ID + ".json"));
        }

        return INSTANCE;
    }

    public static void save() {
        assert INSTANCE != null;
        assert filePath != null;

        try {
            Files.createDirectories(filePath.toAbsolutePath().getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
                writer.append(JANKSON.toJson(INSTANCE).toJson(GRAMMAR));
                writer.flush();
            }
        } catch (IOException ex) {
            LOGGER.fatal("Failed to save config file to: " + filePath.toAbsolutePath(), ex);
        }
    }

    private static synchronized void loadConfig(Path file) {
        if (INSTANCE != null) return; // Sync bailout.

        Config config;
        JsonObject json = null;
        if (Files.exists(file)) {
            try (InputStream is = Files.newInputStream(file)) {
                json = JANKSON.load(is);
                config = JANKSON.fromJson(json, Config.class);
            } catch (IOException | SyntaxError ex) {
                LOGGER.fatal("Failed to read config file from '" + file.toAbsolutePath() + "' - Resetting to default.", ex);
                config = new Config();
            }
        } else {
            config = new Config();
        }

        filePath = file;
        INSTANCE = config;
        save();
    }

    private static @Nullable String getString(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (!(element instanceof JsonPrimitive)) return null;

        return ((JsonPrimitive) element).asString();
    }

    private static @Nullable Boolean getBoolean(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (!(element instanceof JsonPrimitive)) return null;

        return ((JsonPrimitive) element).asBoolean(true);
    }

    @Comment ("For modpack creators. Enter your CurseForge project id here.")
    public String curseProjectID = "";

    @Comment ("If the pause menu buttons should be moved around to insert the Open To Friends button.")
    public boolean moveButtonsOnPauseMenu = true;

    @Comment ("If all chat messages should be logged to console.")
    public boolean logChatToConsole = false;

    @Comment ("Create _VERY_ verbose logs. May create hugenorums log files.")
    public boolean debugMode = false;

    @Comment ("Enables dumping all MTConnect packets to logs.")
    public boolean dumpConnectPackets = false;

    @Comment ("If pause screen buttons are enabled.")
    public boolean pauseScreenButtons = true;

    @Nullable
    @Comment ("If not null, replaces the Bug Reports button on the pause screen to open this link.")
    public String issueTrackerUrl = "https://pste.ch/";
}
