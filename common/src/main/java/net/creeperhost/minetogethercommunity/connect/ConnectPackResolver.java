package net.creeperhost.minetogethercommunity.connect;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.creeperhost.minetogether.lib.web.ApiClientResponse;
import net.creeperhost.minetogether.lib.web.ApiRequest;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.util.ModrinthPackLookup;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static net.creeperhost.minetogether.lib.web.WebConstants.CH;

public class ConnectPackResolver {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String UNKNOWN_MODPACK = "Unknown modpack";
    private static final ExecutorService LOOKUP_EXECUTOR = Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder()
                    .setNameFormat("mt-connect-pack-lookup")
                    .setDaemon(true)
                    .build()
    );
    private static final Map<String, CompletableFuture<String>> NAME_CACHE = new ConcurrentHashMap<>();

    public static Component displayName(@Nullable String key) {
        if (StringUtils.isBlank(key)) {
            return Component.translatable("minetogether.connect.pack.unknown");
        }

        CompletableFuture<String> future = NAME_CACHE.computeIfAbsent(key, packKey -> CompletableFuture.supplyAsync(() -> resolveName(packKey), LOOKUP_EXECUTOR));
        if (!future.isDone()) {
            return Component.translatable("minetogether.connect.pack.unknown");
        }
        return Component.literal(future.getNow(UNKNOWN_MODPACK));
    }

    public static void prefetch(@Nullable String key) {
        if (!StringUtils.isBlank(key)) {
            NAME_CACHE.computeIfAbsent(key, packKey -> CompletableFuture.supplyAsync(() -> resolveName(packKey), LOOKUP_EXECUTOR));
        }
    }

    public static List<SearchResult> search(String query) throws IOException {
        if (StringUtils.isBlank(query)) {
            return List.of();
        }
        SearchResponse response = MineTogether.API.execute(new SearchRequest(query.trim())).apiResponse();
        if (response.modpacks == null || response.modpacks.mc == null) {
            return List.of();
        }
        return response.modpacks.mc;
    }

    public static @Nullable ManualSelection resolveSelection(SearchResult result) throws IOException {
        DetailResponse detail = MineTogether.API.execute(new DetailRequest(result.id)).apiResponse();
        ManualSelection selection = fromMeta(detail, result.displayLabel);
        if (selection != null) {
            return selection;
        }

        String curseId = detail.pack != null ? detail.pack.curseforge : null;
        if (StringUtils.isBlank(curseId) && result.curseforgeId > 0) {
            curseId = String.valueOf(result.curseforgeId);
        }
        if (NumberUtils.isParsable(curseId)) {
            return new ManualSelection(
                    curseId,
                    "curseforge",
                    curseId,
                    "",
                    firstNonBlank(detail.displayName(), result.displayLabel, curseId),
                    detail.versionFor(),
                    detail.id
            );
        }
        return null;
    }

    private static String resolveName(String key) {
        try {
            if (StringUtils.startsWithIgnoreCase(key, "mr:")) {
                ModrinthPackLookup.Result result = ModrinthPackLookup.lookup(key.substring(3));
                if (result.isNotFound()) return UNKNOWN_MODPACK;
                if (!result.isSuccessful()) {
                    LOGGER.warn("Connect Modrinth name lookup for {} returned HTTP {}", key, result.statusCode());
                    return UNKNOWN_MODPACK;
                }
                return StringUtils.isBlank(result.name()) ? UNKNOWN_MODPACK : result.name();
            }

            ApiClientResponse<LookupResponse> result;
            if (NumberUtils.isParsable(key)) {
                result = MineTogether.API.execute(new CurseForgeLookupRequest(key));
            } else {
                result = MineTogether.API.execute(new ModpacksChLookupRequest(key));
            }
            if (result.statusCode() == 404) {
                return UNKNOWN_MODPACK;
            }
            if (result.statusCode() < 200 || result.statusCode() >= 300 || !result.hasBody()) {
                LOGGER.warn("Connect modpack name lookup for {} returned HTTP {}", key, result.statusCode());
                return UNKNOWN_MODPACK;
            }
            LookupResponse response = result.apiResponse();
            if ("success".equalsIgnoreCase(response.status) && !StringUtils.isBlank(response.name)) {
                return response.name;
            }
        } catch (Throwable ex) {
            LOGGER.warn("Failed to resolve Connect modpack name for {}", key, ex);
        }
        return UNKNOWN_MODPACK;
    }

    static @Nullable ManualSelection fromMeta(DetailResponse detail, @Nullable String fallbackName) {
        if (detail.meta == null || StringUtils.isBlank(detail.meta.projectType) || StringUtils.isBlank(detail.meta.projectId)) {
            return null;
        }

        String type = detail.meta.projectType.toLowerCase(Locale.ROOT);
        String displayName = firstNonBlank(detail.displayName(), fallbackName, detail.name);
        if ("curseforge".equals(type)) {
            return new ManualSelection(
                    detail.meta.projectId,
                    type,
                    detail.meta.projectId,
                    StringUtils.stripToEmpty(detail.meta.projectVersion),
                    displayName,
                    StringUtils.stripToEmpty(detail.meta.versionFor),
                    detail.id
            );
        }
        if ("ftb".equals(type) && !StringUtils.isBlank(detail.meta.projectVersion)) {
            String key = Base64.getEncoder().encodeToString((detail.meta.projectId + detail.meta.projectVersion).getBytes(StandardCharsets.UTF_8));
            return new ManualSelection(
                    key,
                    type,
                    detail.meta.projectId,
                    detail.meta.projectVersion,
                    displayName,
                    StringUtils.stripToEmpty(detail.meta.versionFor),
                    detail.id
            );
        }
        if ("modrinth".equals(type)) {
            return new ManualSelection(
                    "mr:" + detail.meta.projectId,
                    type,
                    detail.meta.projectId,
                    StringUtils.stripToEmpty(detail.meta.projectVersion),
                    displayName,
                    StringUtils.stripToEmpty(detail.meta.versionFor),
                    detail.id
            );
        }
        return null;
    }

    private static String firstNonBlank(@Nullable String first, @Nullable String second, @Nullable String third) {
        if (!StringUtils.isBlank(first)) return first;
        if (!StringUtils.isBlank(second)) return second;
        if (!StringUtils.isBlank(third)) return third;
        return UNKNOWN_MODPACK;
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public record ManualSelection(String connectKey, String projectType, String projectId, String projectVersion, String displayName, String minecraftVersion, int creeperHostVersionId) { }

    public static class SearchResult {
        public int id;
        public String displayName = "";
        public String displayVersion = "";
        public String displayLabel = "";
        public int installCount;
        public int curseforgeId;

        public String label() {
            return firstNonBlank(displayLabel, displayName, String.valueOf(id));
        }
    }

    public static class SearchResponse {
        public Modpacks modpacks;
        public int totalResults;
    }

    public static class Modpacks {
        public List<SearchResult> mc = new ArrayList<>();
    }

    public static class DetailResponse {
        public String status = "";
        public int id;
        public String name = "";
        public Pack pack;
        public Meta meta;

        public String displayName() {
            return pack != null ? firstNonBlank(pack.displayName, name, String.valueOf(id)) : firstNonBlank(name, null, String.valueOf(id));
        }

        public String versionFor() {
            return meta != null ? StringUtils.stripToEmpty(meta.versionFor) : "";
        }
    }

    public static class Pack {
        public String displayName = "";
        public String curseforge = "";
        public String displayVersion = "";
        public String humanVersion = "";
    }

    public static class Meta {
        public String projectType = "";
        public String projectId = "";
        public String projectVersion = "";
        public String versionFor = "";
    }

    public static class LookupResponse {
        public String status = "";
        public int id;
        public String name = "";
    }

    private static class SearchRequest extends ApiRequest<SearchResponse> {
        public SearchRequest(String query) {
            super("GET", CH + "json/modpacks/mc/search/unique/" + encodePath(query), SearchResponse.class);
        }
    }

    private static class DetailRequest extends ApiRequest<DetailResponse> {
        public DetailRequest(int id) {
            super("GET", CH + "json/modpacks/name/" + id + "?shape=humanVersion", DetailResponse.class);
        }
    }

    private static class CurseForgeLookupRequest extends ApiRequest<LookupResponse> {
        public CurseForgeLookupRequest(String projectId) {
            super("GET", CH + "json/modpacks/curseforge/" + encodePath(projectId), LookupResponse.class);
        }
    }

    private static class ModpacksChLookupRequest extends ApiRequest<LookupResponse> {
        public ModpacksChLookupRequest(String key) {
            super("GET", CH + "json/modpacks/modpacksch/" + encodePath(key), LookupResponse.class);
        }
    }
}
