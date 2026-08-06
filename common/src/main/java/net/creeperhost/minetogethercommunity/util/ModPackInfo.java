package net.creeperhost.minetogethercommunity.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.covers1624.quack.gson.JsonUtils;
import net.creeperhost.minetogether.lib.web.ApiClientResponse;
import net.creeperhost.minetogether.lib.web.requests.GetCurseForgeVersionRequest;
import net.creeperhost.minetogether.lib.web.requests.GetModpacksCHVersionRequest;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.MineTogetherPlatform;
import net.creeperhost.minetogethercommunity.config.Config;
import net.creeperhost.minetogethercommunity.config.LocalConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Resolves the current launcher instance to a canonical modpack identity.
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
    private static final int MAX_MODRINTH_DB_BYTES = 64 * 1024 * 1024;

    private static CompletableFuture<VersionInfo> initTask;

    public static void init() {
        initTask = CompletableFuture.supplyAsync(() -> new VersionInfo().init(), EXECUTOR);
    }

    public static void reload() {
        initTask = CompletableFuture.supplyAsync(() -> new VersionInfo().init(), EXECUTOR);
        waitForInfo(info -> MineTogether.AUTH.setHeader("Identifier", info.realName));
    }

    public static VersionInfo getInfo() {
        try {
            return initTask.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while retrieving modpack information", ex);
            return new VersionInfo();
        } catch (ExecutionException ex) {
            LOGGER.warn("Failed to retrieve modpack information", ex);
            return new VersionInfo();
        }
    }

    // Note: callback may be called from a different thread.
    public static void waitForInfo(Consumer<VersionInfo> callback) {
        initTask.thenAccept(callback);
    }

    public static boolean shouldPromptForManualSelection() {
        VersionInfo info = getInfo();
        LocalConfig config = LocalConfig.instance();
        return !info.hasConnectPackKey() && !config.connectPackPrompted && !config.connectPackBypass;
    }

    public static CompletableFuture<VersionInfo> detectLauncherInfo() {
        return CompletableFuture.supplyAsync(() -> new VersionInfo(false).init(), EXECUTOR);
    }

    public enum PackSource {
        UNKNOWN,
        CURSEFORGE,
        FTB,
        MODRINTH;

        public static PackSource fromConfig(String value) {
            return switch (StringUtils.lowerCase(StringUtils.stripToEmpty(value))) {
                case "curse", "curseforge", "flame" -> CURSEFORGE;
                case "ftb" -> FTB;
                case "modrinth" -> MODRINTH;
                default -> UNKNOWN;
            };
        }

        public String telemetryName() {
            return this == CURSEFORGE ? "curse" : name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Canonical pack identity. The connect key remains explicit so legacy manual
     * FTB selections can be preserved even when their original version is unknown.
     */
    public record PackIdentity(PackSource source, String projectId, String versionId, String connectKey, String websiteId) {
        public PackIdentity {
            source = source == null ? PackSource.UNKNOWN : source;
            projectId = StringUtils.stripToEmpty(projectId);
            versionId = StringUtils.stripToEmpty(versionId);
            connectKey = StringUtils.stripToEmpty(connectKey);
            websiteId = StringUtils.stripToEmpty(websiteId);
        }

        public static PackIdentity unknown() {
            return new PackIdentity(PackSource.UNKNOWN, "", "", "", "");
        }

        public static PackIdentity curseForge(String projectId) {
            String cleanProjectId = StringUtils.stripToEmpty(projectId);
            return new PackIdentity(PackSource.CURSEFORGE, cleanProjectId, "", cleanProjectId, "");
        }

        public static PackIdentity ftb(String projectId, String versionId) {
            String cleanProjectId = StringUtils.stripToEmpty(projectId);
            String cleanVersionId = StringUtils.stripToEmpty(versionId);
            String key = cleanProjectId.isEmpty() || cleanVersionId.isEmpty() ? "" : encodeFTB(cleanProjectId, cleanVersionId);
            return new PackIdentity(PackSource.FTB, cleanProjectId, cleanVersionId, key, "");
        }

        public static PackIdentity ftbManual(String projectId, String versionId, String connectKey) {
            return new PackIdentity(PackSource.FTB, projectId, versionId, connectKey, "");
        }

        public static PackIdentity modrinth(String projectId, String versionId) {
            String cleanProjectId = stripModrinthPrefix(projectId);
            String key = cleanProjectId.isEmpty() ? "" : "mr:" + cleanProjectId;
            return new PackIdentity(PackSource.MODRINTH, cleanProjectId, versionId, key, "");
        }

        public boolean isKnown() {
            return source != PackSource.UNKNOWN && !projectId.isEmpty();
        }

        public boolean hasConnectKey() {
            return !connectKey.isEmpty();
        }

        public PackIdentity withWebsiteId(String value) {
            return new PackIdentity(source, projectId, versionId, connectKey, value);
        }

        public String identifierJson() {
            Map<String, String> values = new LinkedHashMap<>();
            switch (source) {
                case MODRINTH -> {
                    values.put("p", projectId.isEmpty() ? "-1" : "mr:" + projectId);
                    if (!versionId.isEmpty()) values.put("v", versionId);
                }
                case FTB -> {
                    values.put("p", projectId.isEmpty() ? "-1" : "m" + projectId);
                    if (!connectKey.isEmpty()) values.put("b", connectKey);
                }
                case CURSEFORGE -> values.put("p", NumberUtils.isParsable(projectId) ? projectId : "-1");
                default -> values.put("p", "-1");
            }
            return GSON.toJson(values);
        }

        public String telemetryPackId() {
            return switch (source) {
                case FTB -> projectId.isEmpty() ? "" : "m" + projectId;
                case CURSEFORGE, MODRINTH -> projectId;
                default -> "";
            };
        }

        public String telemetryVersionId() {
            return source == PackSource.FTB ? connectKey : versionId;
        }

        public String lookupIdentifier() {
            return source == PackSource.MODRINTH && !versionId.isEmpty() ? versionId : projectId;
        }
    }

    public static class ModpackVersionManifest {
        public long id;
        public long parent;
    }

    public static class FTBInstanceNew {
        public long id;
        public long packType;
        public long versionId;
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

    public static class VersionInfo {
        private final boolean allowManualOverride;
        private PackIdentity identity = PackIdentity.unknown();

        // Legacy fields retained while callers migrate to PackIdentity.
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
                PackIdentity detected = detectLauncherIdentity();
                if (detected != null) identity = detected;
            }

            if (identity.isKnown() && identity.websiteId().isEmpty()) {
                String websiteId = resolveWebsiteId(identity);
                if (!websiteId.isEmpty()) identity = identity.withWebsiteId(websiteId);
            }

            syncLegacyFields();
            debugInfo("pack identity source={} project={} version={} websiteId={} connectKey={}",
                    identity.source(), identity.projectId(), identity.versionId(), identity.websiteId(), identity.connectKey());
            return this;
        }

        public PackIdentity getPackIdentity() {
            return identity;
        }

        private @Nullable PackIdentity detectLauncherIdentity() {
            PackIdentity detected;

            detected = readAuxiliumMetadata();
            if (detected != null) return detected;

            detected = readFTBVersionJson(MineTogetherPlatform.getGameFolder().resolve("version.json"));
            if (detected != null) return detected;

            detected = readInstanceJson(MineTogetherPlatform.getGameFolder().resolve("instance.json"));
            if (detected != null) return detected;

            detected = readCurseInstance(MineTogetherPlatform.getGameFolder().resolve("minecraftinstance.json"));
            if (detected != null) return detected;

            detected = readMultiMc();
            if (detected != null) return detected;

            detected = readModrinthApp();
            if (detected != null) return detected;

            debugInfo("no supported launcher modpack identity found");
            return null;
        }

        private @Nullable PackIdentity readAuxiliumMetadata() {
            Path path = MineTogetherPlatform.getConfigFolder().resolve("metadata.json");
            if (!Files.exists(path)) return null;
            try {
                Auxilium aux = JsonUtils.parse(GSON, path, Auxilium.class);
                if (aux.id <= 0 || aux.version == null || aux.version.id <= 0) return null;
                debugInfo("detected FTB pack {} version {} from Auxilium metadata", aux.id, aux.version.id);
                return PackIdentity.ftb(String.valueOf(aux.id), String.valueOf(aux.version.id));
            } catch (Exception ex) {
                LOGGER.warn("Failed to load pack id from metadata.json", ex);
                return null;
            }
        }

        private @Nullable PackIdentity readFTBVersionJson(Path path) {
            if (!Files.exists(path)) return null;
            try {
                ModpackVersionManifest manifest = JsonUtils.parse(GSON, path, ModpackVersionManifest.class);
                if (manifest.parent <= 0 || manifest.id <= 0) return null;
                debugInfo("detected FTB pack {} version {} from version.json", manifest.parent, manifest.id);
                return PackIdentity.ftb(String.valueOf(manifest.parent), String.valueOf(manifest.id));
            } catch (Exception ex) {
                LOGGER.warn("Failed to read FTB version manifest {}", path, ex);
                return null;
            }
        }

        private @Nullable PackIdentity readInstanceJson(Path path) {
            if (!Files.exists(path)) return null;
            try {
                FTBInstanceNew manifest = JsonUtils.parse(GSON, path, FTBInstanceNew.class);
                if (manifest.id <= 0) return null;
                if (manifest.packType == 0 && manifest.versionId > 0) {
                    debugInfo("detected FTB pack {} version {} from instance.json", manifest.id, manifest.versionId);
                    return PackIdentity.ftb(String.valueOf(manifest.id), String.valueOf(manifest.versionId));
                }
                if (manifest.packType == 1) {
                    debugInfo("detected CurseForge pack {} from instance.json", manifest.id);
                    return PackIdentity.curseForge(String.valueOf(manifest.id));
                }
            } catch (Exception ex) {
                LOGGER.warn("Failed to read launcher instance {}", path, ex);
            }
            return null;
        }

        private @Nullable PackIdentity readCurseInstance(Path path) {
            if (!Files.exists(path)) return null;
            try {
                CurseInstance instance = JsonUtils.parse(GSON, path, CurseInstance.class);
                if (instance.projectID <= 0) return null;
                debugInfo("detected CurseForge pack {} from minecraftinstance.json", instance.projectID);
                return PackIdentity.curseForge(String.valueOf(instance.projectID));
            } catch (Exception ex) {
                LOGGER.warn("Failed to read CurseForge instance {}", path, ex);
                return null;
            }
        }

        private @Nullable PackIdentity readMultiMc() {
            Path parent = MineTogetherPlatform.getGameFolder().getParent();
            if (parent == null) return null;
            Path path = parent.resolve("instance.cfg");
            if (!Files.exists(path)) return null;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(path)))) {
                Map<String, String> values = new LinkedHashMap<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    int equals = line.indexOf('=');
                    if (equals <= 0) continue;
                    values.put(line.substring(0, equals).trim(), StringUtils.stripToEmpty(line.substring(equals + 1)));
                }
                PackIdentity result = identityFromMultiMcValues(values);
                if (result != null) {
                    debugInfo("detected {} pack {} version {} from Prism/MultiMC", result.source(), result.projectId(), result.versionId());
                }
                return result;
            } catch (Exception ex) {
                LOGGER.warn("Failed to read MultiMC/Prism instance config {}", path, ex);
                return null;
            }
        }

        private @Nullable PackIdentity readModrinthApp() {
            Path gameDir = MineTogetherPlatform.getGameFolder();
            Path profilesDir = gameDir.getParent();
            if (profilesDir == null || !"profiles".equalsIgnoreCase(profilesDir.getFileName().toString())) return null;
            Path root = profilesDir.getParent();
            if (root == null) return null;
            Path appDb = root.resolve("app.db");
            if (!Files.exists(appDb)) return null;

            try (RandomAccessFile file = new RandomAccessFile(appDb.toFile(), "r")) {
                long size = file.length();
                if (size > MAX_MODRINTH_DB_BYTES) {
                    LOGGER.warn("Modrinth app.db is too large ({} bytes), skipping scan", size);
                    return null;
                }
                byte[] bytes = new byte[(int) size];
                file.readFully(bytes);
                PackIdentity result = findModrinthAppIdentity(bytes, gameDir.getFileName().toString());
                if (result != null) {
                    debugInfo("detected Modrinth pack {} version {} from Modrinth App", result.projectId(), result.versionId());
                } else {
                    debugInfo("Modrinth app.db contained no matching pack for instance {}", gameDir.getFileName());
                }
                return result;
            } catch (Exception ex) {
                LOGGER.warn("Failed to scan Modrinth App database {}", appDb, ex);
                return null;
            }
        }

        private String resolveWebsiteId(PackIdentity pack) {
            return switch (pack.source()) {
                case CURSEFORGE -> resolveCurseForgeWebsiteId(pack.projectId());
                case FTB -> resolveFtbWebsiteId(pack.connectKey());
                case MODRINTH -> resolveModrinthWebsiteId(pack.lookupIdentifier());
                default -> "";
            };
        }

        private String resolveCurseForgeWebsiteId(String projectId) {
            if (!NumberUtils.isParsable(projectId)) return "";
            try {
                ApiClientResponse<GetCurseForgeVersionRequest.Response> result = MineTogether.API.execute(new GetCurseForgeVersionRequest(projectId));
                if (result.statusCode() == 404) {
                    debugInfo("no CreeperHost mapping found for CurseForge pack {}", projectId);
                    return "";
                }
                if (!isSuccessful(result.statusCode()) || !result.hasBody()) {
                    LOGGER.warn("CurseForge pack lookup for {} returned HTTP {}", projectId, result.statusCode());
                    return "";
                }
                return StringUtils.stripToEmpty(result.apiResponse().id);
            } catch (IOException ex) {
                LOGGER.warn("Failed to resolve CurseForge pack {}", projectId, ex);
                return "";
            }
        }

        private String resolveFtbWebsiteId(String connectKey) {
            if (StringUtils.isBlank(connectKey)) return "";
            try {
                ApiClientResponse<GetModpacksCHVersionRequest.Response> result = MineTogether.API.execute(new GetModpacksCHVersionRequest(connectKey));
                if (result.statusCode() == 404) {
                    debugInfo("no CreeperHost mapping found for FTB pack {}", connectKey);
                    return "";
                }
                if (!isSuccessful(result.statusCode()) || !result.hasBody()) {
                    LOGGER.warn("FTB pack lookup for {} returned HTTP {}", connectKey, result.statusCode());
                    return "";
                }
                return StringUtils.stripToEmpty(result.apiResponse().id);
            } catch (IOException ex) {
                LOGGER.warn("Failed to resolve FTB pack {}", connectKey, ex);
                return "";
            }
        }

        private String resolveModrinthWebsiteId(String identifier) {
            if (StringUtils.isBlank(identifier)) return "";
            try {
                ModrinthPackLookup.Result result = ModrinthPackLookup.lookup(identifier, identity.identifierJson());
                if (result.isNotFound()) {
                    debugInfo("no CreeperHost mapping found for Modrinth identifier {}", identifier);
                    return "";
                }
                if (!result.isSuccessful()) {
                    LOGGER.warn("Modrinth pack lookup for {} returned HTTP {}", identifier, result.statusCode());
                    return "";
                }
                return result.id();
            } catch (IOException ex) {
                LOGGER.warn("Failed to resolve Modrinth identifier {}", identifier, ex);
                return "";
            }
        }

        public boolean hasConnectPackKey() {
            return identity.hasConnectKey();
        }

        public @Nullable String getConnectPackKey() {
            return identity.hasConnectKey() ? identity.connectKey() : null;
        }

        private boolean applyManualOverride() {
            LocalConfig config = LocalConfig.instance();
            if (config.connectPackBypass) return true;
            String key = StringUtils.stripToEmpty(config.connectPackKey);
            if (key.isEmpty()) return false;

            PackSource source = PackSource.fromConfig(config.connectPackProjectType);
            if (source == PackSource.UNKNOWN) {
                if (StringUtils.startsWithIgnoreCase(key, "mr:")) source = PackSource.MODRINTH;
                else if (NumberUtils.isParsable(key)) source = PackSource.CURSEFORGE;
                else source = PackSource.FTB;
            }

            String projectId = StringUtils.stripToEmpty(config.connectPackProjectId);
            String versionId = StringUtils.stripToEmpty(config.connectPackProjectVersion);
            identity = switch (source) {
                case MODRINTH -> PackIdentity.modrinth(projectId.isEmpty() ? key : projectId, versionId);
                case CURSEFORGE -> PackIdentity.curseForge(projectId.isEmpty() ? key : projectId);
                case FTB -> PackIdentity.ftbManual(projectId, versionId, key);
                default -> PackIdentity.unknown();
            };

            if (config.connectPackCreeperHostVersionId > 0) {
                identity = identity.withWebsiteId(String.valueOf(config.connectPackCreeperHostVersionId));
            }
            return identity.hasConnectKey();
        }

        private void syncLegacyFields() {
            curseID = identity.source() == PackSource.CURSEFORGE ? identity.projectId() : "";
            ftbPackID = identity.source() == PackSource.FTB && !identity.projectId().isEmpty() ? "m" + identity.projectId() : "";
            base64FTBID = identity.source() == PackSource.FTB ? identity.connectKey() : "";
            modrinthProjectID = identity.source() == PackSource.MODRINTH ? identity.projectId() : "";
            modrinthVersionID = identity.source() == PackSource.MODRINTH ? identity.versionId() : "";
            websiteID = identity.websiteId();
            realName = identity.identifierJson();
        }
    }

    static @Nullable PackIdentity identityFromMultiMcValues(Map<String, String> values) {
        String type = StringUtils.lowerCase(StringUtils.stripToEmpty(values.get("ManagedPackType")));
        String projectId = StringUtils.stripToEmpty(values.get("ManagedPackID"));
        String versionId = StringUtils.stripToEmpty(values.get("ManagedPackVersionID"));

        if (type.isEmpty() || projectId.isEmpty()) {
            String[] iconParts = StringUtils.split(StringUtils.stripToEmpty(values.get("iconKey")), '_');
            if (iconParts != null && iconParts.length >= 2) {
                type = StringUtils.lowerCase(StringUtils.stripToEmpty(iconParts[0]));
                projectId = StringUtils.stripToEmpty(iconParts[1]);
            }
        }

        if (type.isEmpty() || projectId.isEmpty()) return null;
        return switch (PackSource.fromConfig(type)) {
            case CURSEFORGE -> NumberUtils.isParsable(projectId) ? PackIdentity.curseForge(projectId) : null;
            case FTB -> NumberUtils.isParsable(projectId) ? PackIdentity.ftb(projectId, versionId) : null;
            case MODRINTH -> PackIdentity.modrinth(projectId, versionId);
            default -> null;
        };
    }

    static @Nullable PackIdentity findModrinthAppIdentity(byte[] bytes, String instanceName) {
        if (bytes == null || StringUtils.isBlank(instanceName)) return null;
        String content = new String(bytes, StandardCharsets.UTF_8);
        String marker = "modrinth_modpack";
        int index = -1;
        while ((index = content.indexOf(marker, index + 1)) >= 0) {
            int idsStart = index + marker.length();
            if (idsStart + 16 > content.length()) continue;
            String projectId = content.substring(idsStart, idsStart + 8);
            String versionId = content.substring(idsStart + 8, idsStart + 16);
            if (!isBase62Id(projectId) || !isBase62Id(versionId)) continue;

            int nearbyStart = Math.max(0, index - 300);
            if (content.substring(nearbyStart, index).contains(instanceName)) {
                return PackIdentity.modrinth(projectId, versionId);
            }
        }
        return null;
    }

    private static boolean isSuccessful(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private static boolean isBase62Id(String value) {
        if (value == null || value.length() != 8) return false;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isLetterOrDigit(value.charAt(i))) return false;
        }
        return true;
    }

    private static String stripModrinthPrefix(String value) {
        String clean = StringUtils.stripToEmpty(value);
        return StringUtils.startsWithIgnoreCase(clean, "mr:") ? clean.substring(3) : clean;
    }

    private static String encodeFTB(Object projectId, Object versionId) {
        return Base64.getEncoder().encodeToString((String.valueOf(projectId) + versionId).getBytes(StandardCharsets.UTF_8));
    }

    private static void debugInfo(String message, Object... args) {
        if (Config.instance().debugMode) {
            LOGGER.info("[MT-PACK-DEBUG] " + message, args);
        }
    }

}
