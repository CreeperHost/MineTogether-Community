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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    static VersionInfo detectLauncherInfo(File gameDir, File configDir) {
        return new VersionInfo(false, gameDir, configDir, false).init();
    }

    public static class VersionInfo {
        private final boolean allowManualOverride;
        private final File gameDir;
        private final File configDir;
        private final boolean resolveMappings;
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
            this(allowManualOverride, MineTogether.getGameDir(), MineTogether.getConfigDir(), true);
        }

        VersionInfo(boolean allowManualOverride, File gameDir, File configDir, boolean resolveMappings) {
            this.allowManualOverride = allowManualOverride;
            this.gameDir = gameDir;
            this.configDir = configDir;
            this.resolveMappings = resolveMappings;
        }

        public VersionInfo init() {
            if (!(allowManualOverride && applyManualOverride())) {
                tryParseLauncherFiles();
            }

            updateIdentifierJson();
            if (resolveMappings && websiteID.isEmpty()) {
                if (!modrinthProjectID.isEmpty()) fetchWebsiteIDModrinth();
                else if (!base64FTBID.isEmpty()) fetchWebsiteIDFTB();
                else if (!curseID.isEmpty()) fetchWebsiteIDCurse();
            }
            updateIdentifierJson();
            return this;
        }

        private void updateIdentifierJson() {
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
            if ("modrinth".equals(type) || StringUtils.startsWithIgnoreCase(config.connectPackKey, "mr:")) {
                modrinthProjectID = StringUtils.stripToEmpty(config.connectPackProjectId);
                if (modrinthProjectID.isEmpty()) {
                    modrinthProjectID = StringUtils.removeStartIgnoreCase(config.connectPackKey, "mr:");
                }
                modrinthVersionID = StringUtils.stripToEmpty(config.connectPackProjectVersion);
            } else if ("ftb".equals(type) || !isParsable(config.connectPackKey)) {
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

            File versionJson = new File(gameDir, "version.json");
            if (versionJson.isFile() && readFTBVersion(versionJson)) return;

            File instanceJson = new File(gameDir, "instance.json");
            if (instanceJson.isFile() && readInstanceJson(instanceJson)) return;

            File curseJson = new File(gameDir, "minecraftinstance.json");
            if (curseJson.isFile() && readCurseInstance(curseJson)) return;

            if (readMultiMc()) return;

            // Native Modrinth App (raw byte scan of app.db)
            if (readModrinthApp()) return;

            LOGGER.info("Could not find a supported launcher modpack identity.");
        }

        private boolean readAuxiliumMetadata() {
            File auxilium = new File(configDir, "metadata.json");
            if (!auxilium.isFile()) return false;

            try (FileReader reader = new FileReader(auxilium)) {
                Auxilium manifest = GSON.fromJson(reader, Auxilium.class);
                if (manifest == null || manifest.id <= 0 || manifest.version == null || manifest.version.id <= 0) {
                    return false;
                }
                ftbPackID = "m" + manifest.id;
                base64FTBID = encodeFTB(manifest.id, manifest.version.id);
                return true;
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
                    return true;
                }
                if (manifest.packType == 1) {
                    curseID = String.valueOf(manifest.id);
                    LOGGER.info("Extracted CurseID " + curseID + " from instance.json");
                    return true;
                }
                return false;
            } catch (Exception ex) {
                LOGGER.warn("Failed to read launcher instance {}", file, ex);
                return false;
            }
        }

        private boolean readMultiMc() {
            File parent = gameDir.getParentFile();
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
                return true;
            }
            if ("ftb".equals(packType)) {
                if (!isParsable(packId)) return false;
                ftbPackID = "m" + packId;
                if (!isParsable(versionId)) return true;
                base64FTBID = encodeFTB(packId, versionId);
                return true;
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
                return true;
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
                return true;
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

        private boolean fetchWebsiteIDModrinth() {
            String identifier = modrinthVersionID.isEmpty() ? modrinthProjectID : modrinthVersionID;
            if (identifier.isEmpty()) return false;
            try {
                ModrinthPackLookup.Result result = ModrinthPackLookup.lookup(identifier, realName);
                if (result.isNotFound()) return false;
                if (!result.isSuccessful()) {
                    LOGGER.warn("Modrinth pack lookup for {} returned HTTP {}", identifier, result.getStatusCode());
                    return false;
                }
                websiteID = result.getId();
                return !websiteID.isEmpty();
            } catch (IOException ex) {
                LOGGER.warn("Failed to resolve Modrinth pack id {}", identifier, ex);
                return false;
            }
        }

        /**
         * Detects Modrinth App instances by checking if the game directory is
         * under ModrinthApp/profiles/ and scanning app.db for pack metadata.
         */
        private boolean readModrinthApp() {
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
                String[] identity = findModrinthAppIdentity(buffer, instanceName);
                if (identity != null) {
                    modrinthProjectID = identity[0];
                    modrinthVersionID = identity[1];
                    LOGGER.info("Detected Modrinth pack " + identity[0] + " version " + identity[1]
                            + " from Modrinth App (instance: " + instanceName + ")");
                    return true;
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

    static String[] findModrinthAppIdentity(byte[] bytes, String instanceName) {
        if (bytes == null || StringUtils.isBlank(instanceName)) return null;
        String content = new String(bytes, StandardCharsets.UTF_8);
        String marker = "modrinth_modpack";
        Set<String> instanceIds = new LinkedHashSet<>();

        int nameIndex = -1;
        while ((nameIndex = content.indexOf(instanceName, nameIndex + 1)) >= 0) {
            int nearbyStart = Math.max(0, nameIndex - 512);
            int instanceIdIndex = content.indexOf("local:", nearbyStart);
            while (instanceIdIndex >= 0 && instanceIdIndex < nameIndex) {
                int end = instanceIdIndex + 42;
                if (end <= content.length()) {
                    String candidate = content.substring(instanceIdIndex, end);
                    if (isModrinthInstanceId(candidate)) instanceIds.add(candidate);
                }
                instanceIdIndex = content.indexOf("local:", instanceIdIndex + 1);
            }
        }

        for (String instanceId : instanceIds) {
            int linkIndex = content.indexOf(instanceId + marker);
            if (linkIndex >= 0) {
                String[] identity = readModrinthIdentityAfter(content, linkIndex + instanceId.length() + marker.length());
                if (identity != null) return identity;
            }
        }

        int index = -1;
        while ((index = content.indexOf(marker, index + 1)) >= 0) {
            int nearbyStart = Math.max(0, index - 300);
            if (content.substring(nearbyStart, index).contains(instanceName)) {
                String[] identity = readModrinthIdentityAfter(content, index + marker.length());
                if (identity != null) return identity;
            }
        }
        return null;
    }

    private static String[] readModrinthIdentityAfter(String content, int start) {
        if (start + 16 > content.length()) return null;
        String projectId = content.substring(start, start + 8);
        String versionId = content.substring(start + 8, start + 16);
        return isBase62(projectId) && isBase62(versionId) ? new String[]{projectId, versionId} : null;
    }

    private static boolean isModrinthInstanceId(String value) {
        if (value == null || value.length() != 42 || !value.startsWith("local:")) return false;
        try {
            String uuid = value.substring(6);
            return UUID.fromString(uuid).toString().equalsIgnoreCase(uuid);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isBase62(String s) {
        if (s == null || s.length() != 8) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isLetterOrDigit(s.charAt(i))) return false;
        }
        return true;
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
