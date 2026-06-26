package net.creeperhost.minetogethercommunity.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.creeperhost.minetogether.lib.web.requests.GetCurseForgeVersionRequest;
import net.creeperhost.minetogether.lib.web.requests.GetModpacksCHVersionRequest;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class ModPackInfo {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether PackInfo");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "mt-packinfo-request");
        thread.setDaemon(true);
        return thread;
    });

    private static CompletableFuture<VersionInfo> initTask;

    public static void init() {
        initTask = CompletableFuture.supplyAsync(() -> new VersionInfo().init(), EXECUTOR);
    }

    public static VersionInfo getInfo() {
        try {
            return initTask == null ? new VersionInfo() : initTask.get();
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

    public static class VersionInfo {
        public String curseID = StringUtils.stripToEmpty(Config.instance().curseProjectID);
        public String websiteID = "";
        public String base64FTBID = "";
        public String ftbPackID = "";
        public String realName = "{\"p\":\"-1\"}";

        public VersionInfo init() {
            tryParseLauncherFiles();
            Map<String, String> json = new HashMap<>();
            if (ftbPackID.isEmpty()) {
                json.put("p", NumberUtils.isParsable(curseID) ? curseID : "-1");
            } else {
                json.put("p", ftbPackID);
                json.put("b", base64FTBID);
            }
            realName = GSON.toJson(json);
            return this;
        }

        private void tryParseLauncherFiles() {
            File versionJson = new File(MineTogether.getGameDir(), "version.json");
            if (versionJson.isFile() && readFTBVersion(versionJson)) return;

            File instanceJson = new File(MineTogether.getGameDir(), "minecraftinstance.json");
            if (instanceJson.isFile() && readCurseInstance(instanceJson)) return;

            if (NumberUtils.isParsable(curseID)) {
                fetchWebsiteIDCurse();
            }
        }

        private boolean readFTBVersion(File file) {
            try (FileReader reader = new FileReader(file)) {
                ModpackVersionManifest manifest = GSON.fromJson(reader, ModpackVersionManifest.class);
                if (manifest == null || manifest.parent <= 0 || manifest.id <= 0) return false;
                ftbPackID = "m" + manifest.parent;
                base64FTBID = Base64.getEncoder().encodeToString((String.valueOf(manifest.parent) + manifest.id).getBytes(StandardCharsets.UTF_8));
                GetModpacksCHVersionRequest.Response response = MineTogether.API.execute(new GetModpacksCHVersionRequest(base64FTBID)).apiResponse();
                if (response.getStatus().equals("error") || response.id.isEmpty()) return false;
                websiteID = response.id;
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
                return fetchWebsiteIDCurse();
            } catch (Exception ex) {
                LOGGER.warn("Failed to read Curse instance {}", file, ex);
                return false;
            }
        }

        private boolean fetchWebsiteIDCurse() {
            try {
                if (!NumberUtils.isParsable(curseID)) return false;
                String resolvedID = MineTogether.API.execute(new GetCurseForgeVersionRequest(curseID)).apiResponse().id;
                if (resolvedID.isEmpty()) return false;
                websiteID = resolvedID;
                return true;
            } catch (IOException ex) {
                LOGGER.warn("Failed to resolve CurseForge pack id {}", curseID, ex);
                return false;
            }
        }
    }

    public static class ModpackVersionManifest {
        public long id;
        public long parent;
    }

    public static class CurseInstance {
        public long projectID = -1;
    }
}
