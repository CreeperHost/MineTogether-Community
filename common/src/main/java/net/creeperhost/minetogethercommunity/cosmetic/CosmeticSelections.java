package net.creeperhost.minetogethercommunity.cosmetic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stores the player's locally selected cosmetics, persisted as
 * {@code local/minetogether/cosmetics/selections.json}.
 */
public class CosmeticSelections {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static @Nullable CosmeticSelections INSTANCE;
    private static @Nullable Path filePath;

    // ── Selected cosmetic IDs — empty string means "none" ─────────────────────
    public String selectedHatId = "";
    public String selectedCapeId = "";

    // ── Singleton access ───────────────────────────────────────────────────────

    public static CosmeticSelections instance() {
        if (INSTANCE == null) {
            synchronized (CosmeticSelections.class) {
                if (INSTANCE == null) {
                    Path path = Platform.getGameFolder()
                            .resolve("local/minetogether/cosmetics/selections.json");
                    INSTANCE = load(path);
                    filePath = path;
                }
            }
        }
        return INSTANCE;
    }

    public static void save() {
        if (INSTANCE == null || filePath == null) return;
        try {
            Files.createDirectories(filePath.getParent());
            try (Writer w = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save cosmetic selections to {}", filePath, e);
        }
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private static CosmeticSelections load(Path path) {
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                CosmeticSelections loaded = GSON.fromJson(r, CosmeticSelections.class);
                if (loaded != null) return loaded;
            } catch (IOException e) {
                LOGGER.error("Failed to read cosmetic selections from {}, using defaults", path, e);
            }
        }
        return new CosmeticSelections();
    }
}
