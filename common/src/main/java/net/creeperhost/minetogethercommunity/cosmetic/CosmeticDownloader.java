package net.creeperhost.minetogethercommunity.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.architectury.platform.Platform;
import net.creeperhost.minetogethercommunity.cosmetic.cape.Cape;
import net.creeperhost.minetogethercommunity.cosmetic.hat.Hat;
import net.creeperhost.minetogethercommunity.cosmetic.tail.Tail;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailModel;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailModelParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages cosmetic asset lifecycle in two distinct phases:
 *
 * <ol>
 *   <li><b>Catalog fetch</b> — {@link #startCatalogFetch()} downloads lightweight metadata
 *       (id, name, locked state, etc.) for every available cosmetic, paginated from the
 *       catalog API. This is fast and starts immediately when the player enters a world.</li>
 *   <li><b>On-demand asset download</b> — {@link #ensureAssetLoaded(String, String)} fetches
 *       the actual 3-D model / texture for a single cosmetic, triggered when a player is seen
 *       wearing it or when the user selects it in the GUI.</li>
 * </ol>
 *
 * All network and file-I/O work runs on daemon threads; results are registered on the
 * Minecraft main thread where required (texture registration).
 */
public class CosmeticDownloader {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final String CATALOG_BASE_URL = "https://api.creeper.host";
    private static final String CDN_BASE_URL = "https://cosmetic.cdn.minetogether.io";
    private static final int PAGE_LIMIT = 100;

    // ── Singleton ──────────────────────────────────────────────────────────────

    private static volatile CosmeticDownloader INSTANCE;

    public static CosmeticDownloader instance() {
        if (INSTANCE == null) {
            synchronized (CosmeticDownloader.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CosmeticDownloader(
                            Platform.getGameFolder().resolve("local/minetogether/cosmetics")
                    );
                }
            }
        }
        return INSTANCE;
    }

    // ── State ──────────────────────────────────────────────────────────────────

    private final Path cacheBase;

    /** Shared HTTP client — reused across all download threads. */
    private final HttpClient httpClient;

    // Catalog (lightweight metadata — populated eagerly by startCatalogFetch)
    private final List<CosmeticItem> hatCatalogList  = new CopyOnWriteArrayList<>();
    private final List<CosmeticItem> capeCatalogList = new CopyOnWriteArrayList<>();
    private final List<CosmeticItem> tailCatalogList = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, CosmeticItem> hatCatalogById  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CosmeticItem> capeCatalogById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CosmeticItem> tailCatalogById = new ConcurrentHashMap<>();

    // Loaded assets (heavy — populated lazily by ensureAssetLoaded)
    private final ConcurrentHashMap<String, Hat>  loadedHats  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Cape> loadedCapes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Tail> loadedTails = new ConcurrentHashMap<>();

    /** IDs for which an asset download is currently in flight. Prevents duplicate requests. */
    private final Set<String> loadingAssetIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Catalog fetch state
    private volatile boolean catalogLoading = false;
    private volatile boolean catalogLoaded  = false;

    // ── Constructor ────────────────────────────────────────────────────────────

    private CosmeticDownloader(Path cacheBase) {
        this.cacheBase = cacheBase;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Starts an asynchronous fetch of the cosmetic catalog (metadata only — no assets).
     * Idempotent: safe to call multiple times; only the first call has any effect.
     */
    public void startCatalogFetch() {
        synchronized (this) {
            if (catalogLoading || catalogLoaded) return;
            catalogLoading = true;
        }
        Thread t = new Thread(this::fetchCatalog, "CosmeticsCatalogFetch");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Ensures the rendered assets for the given cosmetic (model geometry + texture) are
     * downloaded and registered.  No-op if already loaded or currently loading.
     *
     * @param slot {@code "hat"} or {@code "cape"}
     * @param id   The cosmetic ID from the catalog
     */
    public void ensureAssetLoaded(String slot, String id) {
        if (id == null || id.isEmpty()) return;
        // Skip if already fully loaded
        if ("hat".equals(slot)  && loadedHats.containsKey(id))  return;
        if ("cape".equals(slot) && loadedCapes.containsKey(id)) return;
        if ("tail".equals(slot) && loadedTails.containsKey(id)) return;
        // Claim the download slot — only one thread proceeds per id
        if (!loadingAssetIds.add(id)) return;

        Thread t = new Thread(() -> {
            try {
                if ("hat".equals(slot)) {
                    downloadAndRegisterHat(id);
                } else if ("cape".equals(slot)) {
                    downloadAndRegisterCape(id);
                } else if ("tail".equals(slot)) {
                    downloadAndRegisterTail(id);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to download {} asset '{}'", slot, id, e);
                loadingAssetIds.remove(id); // allow retry on next request
            }
        }, "CosmeticAssetLoad-" + id);
        t.setDaemon(true);
        t.start();
    }

    // ── Catalog accessors ──────────────────────────────────────────────────────

    public List<CosmeticItem> getHatCatalog() {
        return Collections.unmodifiableList(hatCatalogList);
    }

    public List<CosmeticItem> getCapeCatalog() {
        return Collections.unmodifiableList(capeCatalogList);
    }

    public List<CosmeticItem> getTailCatalog() {
        return Collections.unmodifiableList(tailCatalogList);
    }

    /** @return {@code true} while the catalog is being fetched from the server. */
    public boolean isCatalogLoading() { return catalogLoading; }

    /** @return {@code true} once the catalog fetch has completed (success or failure). */
    public boolean isCatalogLoaded()  { return catalogLoaded; }

    // ── Asset accessors ────────────────────────────────────────────────────────

    /**
     * Returns the fully loaded {@link Hat} for the given id, or {@code null} if not yet
     * downloaded.  Triggers a download via {@link #ensureAssetLoaded} if not already in
     * progress.
     */
    public @Nullable Hat getLoadedHat(String id) {
        return loadedHats.get(id);
    }

    public @Nullable Cape getLoadedCape(String id) {
        return loadedCapes.get(id);
    }

    public @Nullable Tail getLoadedTail(String id) {
        return loadedTails.get(id);
    }

    /** @return {@code true} if an asset download for {@code id} is currently in flight. */
    public boolean isAssetLoading(String id) {
        return loadingAssetIds.contains(id);
    }

    // ── Internal: catalog fetch ────────────────────────────────────────────────

    private void fetchCatalog() {
        try {
            Files.createDirectories(cacheBase.resolve("hats"));
            Files.createDirectories(cacheBase.resolve("capes"));
            Files.createDirectories(cacheBase.resolve("tails"));

            fetchCatalogForSlot("hat");
            fetchCatalogForSlot("cape");
            fetchCatalogForSlot("tail");
            LOGGER.info("Cosmetic catalog loaded: {} hats, {} capes, {} tails",
                    hatCatalogList.size(), capeCatalogList.size(), tailCatalogList.size());
        } catch (Exception e) {
            LOGGER.error("Failed to fetch cosmetics catalog", e);
        } finally {
            catalogLoading = false;
            catalogLoaded  = true;
        }
    }

    /** Pages through the catalog API and populates the in-memory catalog lists/maps. */
    private void fetchCatalogForSlot(String slot) throws IOException, InterruptedException {
        String cursor = null;
        int total = 0;

        do {
            StringBuilder url = new StringBuilder(CATALOG_BASE_URL)
                    .append("/minetogether/cosmetics/catalog")
                    .append("?slot=").append(slot)
                    .append("&limit=").append(PAGE_LIMIT);
            if (cursor != null) url.append("&cursor=").append(cursor);

            byte[] body = fetchBytes(url.toString());
            var root = JsonParser.parseReader(new InputStreamReader(
                    new java.io.ByteArrayInputStream(body), StandardCharsets.UTF_8
            )).getAsJsonObject();

            JsonArray items = root.getAsJsonArray("cosmetics");
            if (items == null) {
                LOGGER.warn("No 'cosmetics' array in catalog response for slot '{}'", slot);
                break;
            }

            for (JsonElement el : items) {
                var obj     = el.getAsJsonObject();
                String id   = obj.get("id").getAsString();
                String name = obj.get("name").getAsString();
                String author = obj.has("author") && !obj.get("author").isJsonNull()
                        ? obj.get("author").getAsString() : "";
                boolean locked = obj.get("locked").getAsBoolean();
                String howToUnlock = obj.has("howToUnlock") && !obj.get("howToUnlock").isJsonNull()
                        ? obj.get("howToUnlock").getAsString() : null;

                CosmeticItem item = new CosmeticItem(id, name, author, "", locked, howToUnlock);
                if ("hat".equals(slot)) {
                    hatCatalogList.add(item);
                    hatCatalogById.put(id, item);
                } else if ("cape".equals(slot)) {
                    capeCatalogList.add(item);
                    capeCatalogById.put(id, item);
                } else {
                    tailCatalogList.add(item);
                    tailCatalogById.put(id, item);
                }
                total++;
            }

            cursor = root.has("nextCursor") && !root.get("nextCursor").isJsonNull()
                    ? root.get("nextCursor").getAsString() : null;

        } while (cursor != null);

        LOGGER.info("Fetched {} {} catalog entries", total, slot);
    }

    // ── Internal: on-demand asset download ────────────────────────────────────

    /**
     * Downloads the {@code .tc2} asset for a hat, parses it via {@link TechneLoader}, and
     * registers it on the Minecraft main thread (after TechneLoader's own texture registration).
     */
    private void downloadAndRegisterHat(String id) throws Exception {
        CosmeticItem item = catalogItemOrFallback(hatCatalogById, id);
        if (!hatCatalogById.containsKey(id)) {
            LOGGER.debug("Hat asset '{}' requested before catalog entry was available; using fallback metadata", id);
        }

        Path itemDir = cacheBase.resolve("hats").resolve(id);
        List<String> files = fetchAndCacheFiles(CDN_BASE_URL + "/hat/" + id, itemDir);

        String tc2File = files.stream()
                .filter(f -> f.toLowerCase().endsWith(".tc2"))
                .findFirst()
                .orElseThrow(() -> new IOException("No .tc2 file in metadata for hat '" + id + "'"));

        byte[] data = Files.readAllBytes(itemDir.resolve(tc2File));
        // TechneLoader internally queues texture registration on the Minecraft main thread.
        Hat hat = TechneLoader.load(id, item.displayName(), item.author(), item.mod(),
                item.locked(), item.howToUnlock(), data);

        // Schedule the registry addition AFTER TechneLoader's own Minecraft.execute() so the
        // texture is guaranteed to be registered before any render layer can see this hat.
        Minecraft.getInstance().execute(() -> {
            loadedHats.put(id, hat);
            loadingAssetIds.remove(id);
            LOGGER.info("Hat asset ready: '{}'", id);
        });
    }

    /**
     * Downloads the {@code .png} asset for a cape and registers it on the Minecraft main thread.
     */
    private void downloadAndRegisterCape(String id) throws Exception {
        CosmeticItem item = catalogItemOrFallback(capeCatalogById, id);
        if (!capeCatalogById.containsKey(id)) {
            LOGGER.debug("Cape asset '{}' requested before catalog entry was available; using fallback metadata", id);
        }

        Path itemDir = cacheBase.resolve("capes").resolve(id);
        List<String> files = fetchAndCacheFiles(CDN_BASE_URL + "/cape/" + id, itemDir);

        String pngFile = files.stream()
                .filter(f -> f.toLowerCase().endsWith(".png"))
                .findFirst()
                .orElseThrow(() -> new IOException("No .png file in metadata for cape '" + id + "'"));

        byte[] data = Files.readAllBytes(itemDir.resolve(pngFile));
        ResourceLocation location = textureLocation("cape", id);

        Minecraft.getInstance().execute(() -> {
            try {
                NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(data));
                DynamicTexture tex = new DynamicTexture(img);
                Minecraft.getInstance().getTextureManager().register(location, tex);
                Cape cape = new Cape(id, item.displayName(), item.author(), item.mod(),
                        item.locked(), item.howToUnlock(), location, img.getWidth(), img.getHeight());
                loadedCapes.put(id, cape);
                loadingAssetIds.remove(id);
                LOGGER.info("Cape asset ready: '{}' ({}x{})", id, img.getWidth(), img.getHeight());
            } catch (Exception e) {
                LOGGER.error("Failed to register cape texture '{}'", id, e);
                loadingAssetIds.remove(id);
            }
        });
    }

    /**
     * Downloads the {@code model.json} and texture {@code .png} for a tail, parses the block-model
     * JSON, and registers the texture + model on the Minecraft main thread.
     */
    private void downloadAndRegisterTail(String id) throws Exception {
        CosmeticItem item = catalogItemOrFallback(tailCatalogById, id);
        if (!tailCatalogById.containsKey(id)) {
            LOGGER.debug("Tail asset '{}' requested before catalog entry was available; using fallback metadata", id);
        }

        Path itemDir = cacheBase.resolve("tails").resolve(id);
        List<String> files = fetchAndCacheFiles(CDN_BASE_URL + "/tail/" + id, itemDir);

        String jsonFile = files.stream()
                .filter(f -> f.toLowerCase().endsWith(".json"))
                .findFirst()
                .orElseThrow(() -> new IOException("No .json file in metadata for tail '" + id + "'"));

        String pngFile = files.stream()
                .filter(f -> f.toLowerCase().endsWith(".png"))
                .findFirst()
                .orElseThrow(() -> new IOException("No .png file in metadata for tail '" + id + "'"));

        byte[] jsonData = Files.readAllBytes(itemDir.resolve(jsonFile));
        byte[] pngData  = Files.readAllBytes(itemDir.resolve(pngFile));

        JsonObject modelRoot = JsonParser.parseReader(new java.io.InputStreamReader(
                new java.io.ByteArrayInputStream(jsonData), java.nio.charset.StandardCharsets.UTF_8
        )).getAsJsonObject();

        var elements = net.creeperhost.minetogethercommunity.cosmetic.tail.TailModelParser.parse(modelRoot);
        int texW = modelRoot.has("texture_size") ? modelRoot.getAsJsonArray("texture_size").get(0).getAsInt() : 64;
        int texH = modelRoot.has("texture_size") ? modelRoot.getAsJsonArray("texture_size").get(1).getAsInt() : 32;
        LOGGER.info("Tail '{}' parsed: {} elements, texSize={}x{}", id, elements.size(), texW, texH);

        ResourceLocation location = textureLocation("tail", id);

        Minecraft.getInstance().execute(() -> {
            try {
                NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(pngData));
                DynamicTexture tex = new DynamicTexture(img);
                Minecraft.getInstance().getTextureManager().register(location, tex);

                var model = new net.creeperhost.minetogethercommunity.cosmetic.tail.TailModel(elements, texW, texH);
                Tail tail = new Tail(id, item.displayName(), item.author(), item.mod(),
                        item.locked(), item.howToUnlock(), location, texW, texH, elements, model);
                loadedTails.put(id, tail);
                loadingAssetIds.remove(id);
                LOGGER.info("Tail asset ready: '{}'", id);
            } catch (Exception e) {
                LOGGER.error("Failed to register tail texture '{}'", id, e);
                loadingAssetIds.remove(id);
            }
        });
    }

    // ── Internal: CDN helpers ──────────────────────────────────────────────────

    /**
     * Fetches {@code metadata.json} from the CDN, then downloads any listed files that are
     * not already cached on disk.
     *
     * @return the list of filenames declared in the metadata
     */
    private List<String> fetchAndCacheFiles(String cdnBase, Path itemDir)
            throws IOException, InterruptedException {
        Files.createDirectories(itemDir);

        String metaUrl = cdnBase + "/metadata.json";
        byte[] metaBytes = fetchBytes(metaUrl);

        var meta = JsonParser.parseReader(new InputStreamReader(
                new java.io.ByteArrayInputStream(metaBytes), StandardCharsets.UTF_8
        )).getAsJsonObject();

        JsonArray filesArray = meta.getAsJsonArray("files");
        if (filesArray == null || filesArray.isEmpty())
            throw new IOException("No 'files' array in metadata at " + metaUrl);

        List<String> fileNames = new ArrayList<>();
        for (JsonElement el : filesArray) {
            String filename = el.getAsString();
            fileNames.add(filename);

            Path dest = itemDir.resolve(filename);
            if (Files.exists(dest)) {
                LOGGER.debug("  [cached] {}", filename);
            } else {
                String fileUrl = cdnBase + "/" + filename;
                LOGGER.debug("  [download] {}", fileUrl);
                byte[] fileData = fetchBytes(fileUrl);
                Files.write(dest, fileData);
            }
        }
        return fileNames;
    }

    private byte[] fetchBytes(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200)
            throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        return resp.body();
    }

    private static ResourceLocation textureLocation(String slot, String id) {
        String safeId = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        return ResourceLocation.fromNamespaceAndPath("minetogethercommunity", slot + "/" + safeId);
    }

    private static CosmeticItem catalogItemOrFallback(ConcurrentHashMap<String, CosmeticItem> catalog, String id) {
        CosmeticItem item = catalog.get(id);
        return item != null ? item : fallbackCatalogItem(id);
    }

    private static CosmeticItem fallbackCatalogItem(String id) {
        return new CosmeticItem(id, id, "", "", false, null);
    }
}
