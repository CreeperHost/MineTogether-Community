package net.creeperhost.minetogethercommunity.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;
import net.covers1624.quack.gson.JsonUtils;
import net.creeperhost.minetogether.lib.web.requests.GetCurseForgeVersionRequest;
import net.creeperhost.minetogether.lib.web.requests.GetModpacksCHVersionRequest;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.config.Config;
import org.apache.commons.lang3.StringUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Created by covers1624 on 25/10/22.
 */
public class ModPackInfo {
    public static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder()
                    .setNameFormat("mt-packinfo-request")
                    .setDaemon(true)
                    .build()
    );

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Logger LOGGER = LogManager.getLogger();

    private static CompletableFuture<VersionInfo> initTask;

    public static boolean isParsable(String str) {
        if (str == null || str.isEmpty()) return false;
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static void init() {
        initTask = CompletableFuture.supplyAsync(() -> new VersionInfo().init(), EXECUTOR);
    }

    public static VersionInfo getInfo() {
        try {
            return initTask.get();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.warn("Failed to retrieve version data", e);
            return new VersionInfo();
        }
    }

    //Note Callback may be called from a different thread.
    public static void waitForInfo(Consumer<VersionInfo> callback) {
        initTask.thenAccept(callback);
    }

    public static class ModpackVersionManifest {

        public long id;
        public long parent;
    }

    public static class FTBInstanceNew {
        public long id = -1;
        public long packType = -1;
        public long versionId = -1;
    }

    public static class CurseInstance {
        public long projectID = -1;
    }

    public static class Auxilium {
        public long id = -1;
        public AuxiliumVersion version = null;
    }

    public static class AuxiliumVersion {
        public long id = -1;
        public String name = "";
        public String type = "";
    }

    public static class VersionInfo {
        public String curseID = StringUtils.stripToEmpty(Config.instance().curseProjectID);
        public String websiteID = "";
        public String base64FTBID = "";
        public String ftbPackID = "";
        public String realName = "{\"p\": \"-1\"}";

        public VersionInfo() {
            if (!curseID.isEmpty() && !isParsable(curseID)) {
                LOGGER.error("Detected invalid curseID: {}", curseID);
                curseID = "";
            }
        }

        public VersionInfo init() {
            tryParseLauncherFiles();

            Map<String, String> json = new HashMap<>();
            if (ftbPackID.isEmpty()) {
                json.put("p", isParsable(curseID) ? curseID : "-1");
            } else {
                json.put("p", ftbPackID);
                if (!base64FTBID.isEmpty()) {
                    json.put("b", base64FTBID);
                }
            }

            realName = GSON.toJson(json);
            return this;
        }

        /**
         * Attempts to detect the modpack identity from launcher-specific files.
         * Priority order:
         * 1. Auxilium metadata (FTB App config/metadata.json)
         * 2. FTB version.json (old FTB launcher, game folder)
         * 3. instance.json (FTB App new format, game folder — handles both FTB and CurseForge packs)
         * 4. minecraftinstance.json (CurseForge Launcher, game folder)
         * 5. instance.cfg (MultiMC/Prism, parent folder — handles CurseForge, FTB, and iconKey fallback)
         */
        private void tryParseLauncherFiles() {
            // 1. Auxilium (FTB App metadata in config folder)
            if (readAuxiliumMetadata()) return;

            // 2. Old FTB Launcher version.json
            Path versionJson = Platform.getGameFolder().resolve("version.json");
            if (Files.exists(versionJson) && readFTBVersionJson(versionJson)) return;

            // 3. FTB App instance.json (handles both FTB packType=0 and CurseForge packType=1)
            Path instanceJson = Platform.getGameFolder().resolve("instance.json");
            if (Files.exists(instanceJson) && readInstanceJson(instanceJson)) return;

            // 4. CurseForge Launcher minecraftinstance.json
            Path curseJson = Platform.getGameFolder().resolve("minecraftinstance.json");
            if (Files.exists(curseJson) && readCurseInstance(curseJson)) return;

            // 5. MultiMC/Prism instance.cfg (parent folder)
            if (readMultiMc()) return;

            // 6. Fall back to curseID from config if set
            if (!curseID.isEmpty()) {
                fetchWebsiteIDCurse();
                return;
            }

            LOGGER.info("Could not find a supported launcher modpack identity.");
        }

        private boolean readAuxiliumMetadata() {
            Path auxilium = Platform.getConfigFolder().resolve("metadata.json");
            if (!Files.exists(auxilium)) return false;

            try {
                Auxilium aux = JsonUtils.parse(GSON, auxilium, Auxilium.class);
                if (aux.id <= 0 || aux.version == null || aux.version.id <= 0) {
                    return false;
                }
                LOGGER.info("Found auxilium id: {} version: {}", aux.id, aux.version.id);
                ftbPackID = "m" + aux.id;
                base64FTBID = encodeFTB(aux.id, aux.version.id);
                return fetchWebsiteIDFTB();
            } catch (Exception e) {
                LOGGER.warn("Failed to load pack id from metadata.json", e);
                return false;
            }
        }

        private boolean readFTBVersionJson(Path versionJson) {
            try {
                ModpackVersionManifest manifest = JsonUtils.parse(GSON, versionJson, ModpackVersionManifest.class);
                if (manifest.parent <= 0 || manifest.id <= 0) return false;
                ftbPackID = "m" + manifest.parent;
                base64FTBID = encodeFTB(manifest.parent, manifest.id);
                return fetchWebsiteIDFTB();
            } catch (Exception ex) {
                LOGGER.warn("Failed to read FTB version manifest {}", versionJson, ex);
                return false;
            }
        }

        private boolean readInstanceJson(Path path) {
            try {
                FTBInstanceNew manifest = JsonUtils.parse(GSON, path, FTBInstanceNew.class);
                if (manifest.id <= 0) return false;
                // FTB pack
                if (manifest.packType == 0 && manifest.versionId > 0) {
                    ftbPackID = "m" + manifest.id;
                    base64FTBID = encodeFTB(manifest.id, manifest.versionId);
                    return fetchWebsiteIDFTB();
                }
                // CurseForge pack
                if (manifest.packType == 1) {
                    curseID = String.valueOf(manifest.id);
                    LOGGER.info("Extracted CurseID {} from instance.json", curseID);
                    return fetchWebsiteIDCurse();
                }
                return false;
            } catch (Exception ex) {
                LOGGER.warn("Failed to read launcher instance {}", path, ex);
                return false;
            }
        }

        private boolean readCurseInstance(Path path) {
            try {
                CurseInstance instance = JsonUtils.parse(GSON, path, CurseInstance.class);
                if (instance.projectID <= 0) return false;
                curseID = String.valueOf(instance.projectID);
                LOGGER.info("Extracted CurseID {} from minecraftinstance.json", curseID);
                return fetchWebsiteIDCurse();
            } catch (Exception ex) {
                LOGGER.warn("Failed to read Curse instance {}", path, ex);
                return false;
            }
        }

        private boolean readMultiMc() {
            Path parent = Platform.getGameFolder().getParent();
            if (parent == null) return false;
            Path instanceCfg = parent.resolve("instance.cfg");
            if (!Files.exists(instanceCfg)) return false;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(instanceCfg)))) {
                Map<String, String> values = new HashMap<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    int equals = line.indexOf('=');
                    if (equals <= 0) continue;
                    String key = line.substring(0, equals).trim();
                    String value = StringUtils.stripToEmpty(line.substring(equals + 1));
                    values.put(key, value);
                }
                return readMultiMcValues(values);
            } catch (Exception ex) {
                LOGGER.warn("Failed to read MultiMC/Prism instance config {}", instanceCfg, ex);
                return false;
            }
        }

        private boolean readMultiMcValues(Map<String, String> values) {
            String packType = StringUtils.lowerCase(StringUtils.stripToEmpty(values.get("ManagedPackType")));
            String packId = StringUtils.stripToEmpty(values.get("ManagedPackID"));
            String versionId = StringUtils.stripToEmpty(values.get("ManagedPackVersionID"));

            // Fallback: try iconKey if ManagedPack fields are empty
            if (packType.isEmpty() || packId.isEmpty()) {
                String iconKey = StringUtils.stripToEmpty(values.get("iconKey"));
                String[] split = StringUtils.split(iconKey, '_');
                if (split != null && split.length >= 2) {
                    packType = StringUtils.lowerCase(StringUtils.stripToEmpty(split[0]));
                    packId = StringUtils.stripToEmpty(split[1]);
                }
            }

            if (packType.isEmpty() || packId.isEmpty()) return false;

            // CurseForge pack types
            if ("flame".equals(packType) || "curseforge".equals(packType) || "curse".equals(packType)) {
                if (!isParsable(packId)) return false;
                curseID = packId;
                LOGGER.info("Extracted CurseID {} from instance.cfg (type: {})", curseID, packType);
                return fetchWebsiteIDCurse();
            }

            // FTB pack type
            if ("ftb".equals(packType)) {
                if (!isParsable(packId)) return false;
                ftbPackID = "m" + packId;
                if (isParsable(versionId)) {
                    base64FTBID = encodeFTB(packId, versionId);
                    return fetchWebsiteIDFTB();
                }
                LOGGER.info("Found FTB pack {} from instance.cfg but no version ID", packId);
                return true;
            }

            return false;
        }

        private boolean fetchWebsiteIDCurse() {
            try {
                if (!isParsable(curseID)) return false;
                GetCurseForgeVersionRequest.Response response = MineTogether.API.execute(new GetCurseForgeVersionRequest(curseID)).apiResponse();
                if (response.getStatus().equals("error") || response.id.isEmpty()) return false;
                websiteID = response.id;
                return true;
            } catch (IOException ex) {
                LOGGER.warn("Failed to resolve CurseForge pack id {}", curseID, ex);
                return false;
            }
        }

        private boolean fetchWebsiteIDFTB() {
            try {
                if (base64FTBID.isEmpty()) return false;
                GetModpacksCHVersionRequest.Response response = MineTogether.API.execute(new GetModpacksCHVersionRequest(base64FTBID)).apiResponse();
                if (response.getStatus().equals("error") || response.id.isEmpty()) return false;
                websiteID = response.id;
                return true;
            } catch (IOException ex) {
                LOGGER.warn("Failed to resolve FTB pack id {}", base64FTBID, ex);
                return false;
            }
        }
    }

    private static String encodeFTB(Object packId, Object versionId) {
        return Base64.getEncoder().encodeToString((String.valueOf(packId) + versionId).getBytes(StandardCharsets.UTF_8));
    }
}
