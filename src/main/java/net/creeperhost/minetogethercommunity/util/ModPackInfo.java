package net.creeperhost.minetogethercommunity.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.creeperhost.minetogether.lib.web.requests.GetCurseForgeVersionRequest;
import net.creeperhost.minetogether.lib.web.requests.GetModpacksCHVersionRequest;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.config.LocalConfig;
import org.apache.commons.lang3.StringUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class ModPackInfo {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether PackInfo");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    public static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "mt-packinfo-request");
        thread.setDaemon(true);
        return thread;
    });

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

    public static void reload() {
        init();
        waitForInfo(info -> MineTogether.AUTH.setHeader("Identifier", info.realName));
    }

    public static VersionInfo getInfo() {
        try {
            return initTask == null ? new VersionInfo().init() : initTask.get();
        } catch (Exception ex) {
            LOGGER.warn("Failed to retrieve pack info", ex);
            return new VersionInfo();
        }
    }

    public static void waitForInfo(Consumer<VersionInfo> callback) {
        if (initTask != null) {
            initTask.thenAccept(callback);
        }
    }

    public static boolean shouldPromptForManualSelection() {
        VersionInfo info = getInfo();
        LocalConfig config = LocalConfig.instance();
        return !info.hasConnectPackKey() && !config.connectPackPrompted && !config.connectPackBypass;
    }

    public static CompletableFuture<VersionInfo> detectLauncherInfo() {
        return CompletableFuture.supplyAsync(() -> new VersionInfo(false).init(), EXECUTOR);
    }

    public static class VersionInfo {
        private final boolean allowManualOverride;
        public String curseID = "";
        public String websiteID = "";
        public String base64FTBID = "";
        public String ftbPackID = "";
        public String modrinthProjectID = "";
        public String modrinthVersionID = "";
        public String realName = "{\"p\":\"-1\"}";

        public VersionInfo() {
            this(true);
        }

        private VersionInfo(boolean allowManualOverride) {
            this.allowManualOverride = allowManualOverride;
        }

        public VersionInfo init() {
            if (!(allowManualOverride && applyManualOverride())) {
                tryParseLauncherFiles();
            }

            Map<String, String> json = new HashMap<>();
            if (!modrinthProjectID.isEmpty()) {
                json.put("p", "mr:" + modrinthProjectID);
                if (!modrinthVersionID.isEmpty()) {
                    json.put("v", modrinthVersionID);
                }
            } else if (!ftbPackID.isEmpty()) {
                json.put("p", ftbPackID);
                if (!base64FTBID.isEmpty()) {
                    json.put("b", base64FTBID);
                }
            } else {
                json.put("p", isParsable(curseID) ? curseID : "-1");
            }
            realName = GSON.toJson(json);
            return this;
        }

        private boolean applyManualOverride() {
            LocalConfig config = LocalConfig.instance();
            if (config.connectPackBypass) {
                return true;
            }
            if (StringUtils.isBlank(config.connectPackKey)) {
                return false;
            }

            String type = StringUtils.stripToEmpty(config.connectPackProjectType).toLowerCase(Locale.ROOT);
            if ("ftb".equals(type) || !isParsable(config.connectPackKey)) {
                base64FTBID = config.connectPackKey;
                if (!StringUtils.isBlank(config.connectPackProjectId)) {
                    ftbPackID = "m" + config.connectPackProjectId;
                }
            } else {
                curseID = config.connectPackKey;
            }

            if (config.connectPackCreeperHostVersionId > 0) {
                websiteID = String.valueOf(config.connectPackCreeperHostVersionId);
            }
            return hasConnectPackKey();
        }

        private void tryParseLauncherFiles() {
            if (readAuxiliumMetadata()) return;

            File versionJson = new File(MineTogether.getGameDir(), "version.json");
            if (versionJson.isFile() && readFTBVersion(versionJson)) return;

            File instanceJson = new File(MineTogether.getGameDir(), "instance.json");
            if (instanceJson.isFile() && readInstanceJson(instanceJson)) return;

            File curseJson = new File(MineTogether.getGameDir(), "minecraftinstance.json");
            if (curseJson.isFile() && readCurseInstance(curseJson)) return;

            if (readMultiMc()) return;

            // Native Modrinth App (raw byte scan of app.db)
            if (readModrinthApp()) return;

            LOGGER.info("Could not find a supported launcher modpack identity.");
        }

        private boolean readAuxiliumMetadata() {
            File auxilium = new File(MineTogether.getConfigDir(), "metadata.json");
            if (!auxilium.isFile()) return false;

            try (FileReader reader = new FileReader(auxilium)) {
                Auxilium manifest = GSON.fromJson(reader, Auxilium.class);
                if (manifest == null || manifest.id <= 0 || manifest.version == null || manifest.version.id <= 0) {
                    return false;
                }
                ftbPackID = "m" + manifest.id;
                base64FTBID = encodeFTB(manifest.id, manifest.version.id);
                return fetchWebsiteIDFTB();
            } catch (Exception ex) {
                LOGGER.warn("Failed to read FTB Auxilium metadata {}", auxilium, ex);
                return false;
            }
        }

        private boolean readInstanceJson(File file) {
            try (FileReader reader = new FileReader(file)) {
                FTBInstanceNew manifest = GSON.fromJson(reader, FTBInstanceNew.class);
                if (manifest == null || manifest.id <= 0) return false;
                if (manifest.packType == 0 && manifest.versionId > 0) {
                    ftbPackID = "m" + manifest.id;
                    base64FTBID = encodeFTB(manifest.id, manifest.versionId);
                    return fetchWebsiteIDFTB();
                }
                if (manifest.packType == 1) {
                    curseID = String.valueOf(manifest.id);
                    LOGGER.info("Extracted CurseID " + curseID + " from instance.json");
                    return fetchWebsiteIDCurse();
                }
                return false;
            } catch (Exception ex) {
                LOGGER.warn("Failed to read launcher instance {}", file, ex);
                return false;
            }
        }

        private boolean readMultiMc() {
            File parent = MineTogether.getGameDir().getParentFile();
            if (parent == null) return false;
            File manifest = new File(parent, "instance.cfg");
            if (!manifest.exists()) return false;

            try (BufferedReader reader = new BufferedReader(new FileReader(manifest))) {
                Map<String, String> values = new HashMap<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    int equals = line.indexOf('=');
                    if (equals <= 0) continue;
                    String key = line.substring(0, equals).trim();
                    String value = StringUtils.stripToEmpty(line.substring(equals + 1));
                    values.put(key, value);
                }
                return readMultiMc(values);
            } catch (Exception ex) {
                LOGGER.warn("Failed to read MultiMC instance config {}", manifest, ex);
                return false;
            }
        }

        private boolean readMultiMc(Map<String, String> values) {
            String packType = StringUtils.lowerCase(StringUtils.stripToEmpty(values.get("ManagedPackType")));
            String packId = StringUtils.stripToEmpty(values.get("ManagedPackID"));
            String versionId = StringUtils.stripToEmpty(values.get("ManagedPackVersionID"));

            if (packType.isEmpty() || packId.isEmpty()) {
                String iconKey = StringUtils.stripToEmpty(values.get("iconKey"));
                String[] split = StringUtils.split(iconKey, '_');
                if (split != null && split.length >= 2) {
                    packType = StringUtils.lowerCase(StringUtils.stripToEmpty(split[0]));
                    packId = StringUtils.stripToEmpty(split[1]);
                }
            }

            if (packType.isEmpty() || packId.isEmpty()) return false;
            if ("flame".equals(packType) || "curseforge".equals(packType) || "curse".equals(packType)) {
                if (!isParsable(packId)) return false;
                curseID = packId;
                return fetchWebsiteIDCurse();
            }
            if ("ftb".equals(packType)) {
                if (!isParsable(packId)) return false;
                ftbPackID = "m" + packId;
                if (!isParsable(versionId)) return true;
                base64FTBID = encodeFTB(packId, versionId);
                return fetchWebsiteIDFTB();
            }
            // Modrinth pack type
            if ("modrinth".equals(packType)) {
                if (packId.isEmpty()) return false;
                modrinthProjectID = packId;
                modrinthVersionID = StringUtils.stripToEmpty(versionId);
                LOGGER.info("Detected Modrinth pack " + packId + " version " + versionId + " from Prism/MultiMC");
                return true;
            }
            return false;
        }

        private boolean readFTBVersion(File file) {
            try (FileReader reader = new FileReader(file)) {
                ModpackVersionManifest manifest = GSON.fromJson(reader, ModpackVersionManifest.class);
                if (manifest == null || manifest.parent <= 0 || manifest.id <= 0) return false;
                ftbPackID = "m" + manifest.parent;
                base64FTBID = encodeFTB(manifest.parent, manifest.id);
                return fetchWebsiteIDFTB();
            } catch (Exception ex) {
                LOGGER.warn("Failed to read FTB version manifest {}", file, ex);
                return false;
            }
        }

        private boolean readCurseInstance(File file) {
            try (FileReader reader = new FileReader(file)) {
                CurseInstance instance = GSON.fromJson(reader, CurseInstance.class);
                if (instance == null || instance.projectID <= 0) return false;
                curseID = String.valueOf(instance.projectID);
                return fetchWebsiteIDCurse();
            } catch (Exception ex) {
                LOGGER.warn("Failed to read Curse instance {}", file, ex);
                return false;
            }
        }

        public boolean hasConnectPackKey() {
            return !StringUtils.isBlank(base64FTBID) || !StringUtils.isBlank(curseID) || !StringUtils.isBlank(modrinthProjectID);
        }

        public String getConnectPackKey() {
            if (!StringUtils.isBlank(modrinthProjectID)) return "mr:" + modrinthProjectID;
            if (!StringUtils.isBlank(base64FTBID)) return base64FTBID;
            if (!StringUtils.isBlank(curseID)) return curseID;
            return null;
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

        /**
         * Detects Modrinth App instances by checking if the game directory is
         * under ModrinthApp/profiles/ and scanning app.db for pack metadata.
         */
        private boolean readModrinthApp() {
            File gameDir = MineTogether.getGameDir();
            File profilesDir = gameDir.getParentFile();
            if (profilesDir == null || !"profiles".equals(profilesDir.getName())) return false;
            File modrinthRoot = profilesDir.getParentFile();
            if (modrinthRoot == null) return false;

            File appDb = new File(modrinthRoot, "app.db");
            if (!appDb.isFile()) return false;

            String instanceName = gameDir.getName();
            LOGGER.info("Detected Modrinth App environment, scanning app.db for instance '" + instanceName + "'");

            try (RandomAccessFile raf = new RandomAccessFile(appDb, "r")) {
                long fileSize = raf.length();
                if (fileSize > 64 * 1024 * 1024) {
                    LOGGER.warn("Modrinth app.db is too large (" + fileSize + " bytes), skipping scan");
                    return false;
                }
                byte[] buffer = new byte[(int) fileSize];
                raf.readFully(buffer);
                String content = new String(buffer, StandardCharsets.UTF_8);

                String marker = "modrinth_modpack";
                int idx = -1;
                while ((idx = content.indexOf(marker, idx + 1)) >= 0) {
                    int afterMarker = idx + marker.length();
                    if (afterMarker + 16 > content.length()) continue;

                    String projectId = content.substring(afterMarker, afterMarker + 8);
                    String versionId = content.substring(afterMarker + 8, afterMarker + 16);

                    if (!isBase62(projectId) || !isBase62(versionId)) continue;

                    // Verify this row belongs to our instance by checking nearby bytes
                    int searchStart = Math.max(0, idx - 300);
                    String nearby = content.substring(searchStart, idx);
                    if (nearby.contains(instanceName)) {
                        modrinthProjectID = projectId;
                        modrinthVersionID = versionId;
                        LOGGER.info("Detected Modrinth pack " + projectId + " version " + versionId
                                + " from Modrinth App (instance: " + instanceName + ")");
                        return true;
                    }
                }
                LOGGER.info("Modrinth App app.db scanned but no matching instance found for '" + instanceName + "'");
            } catch (Exception ex) {
                LOGGER.warn("Failed to scan Modrinth App database {}", appDb, ex);
            }
            return false;
        }

        private static boolean isBase62(String s) {
            if (s == null || s.length() != 8) return false;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (!Character.isLetterOrDigit(c)) return false;
            }
            return true;
        }
    }

    private static String encodeFTB(Object packId, Object versionId) {
        return Base64.getEncoder().encodeToString((String.valueOf(packId) + versionId).getBytes(StandardCharsets.UTF_8));
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
        public AuxiliumVersion version;
    }

    public static class AuxiliumVersion {
        public long id = -1;
        public String name = "";
        public String type = "";
    }
}
