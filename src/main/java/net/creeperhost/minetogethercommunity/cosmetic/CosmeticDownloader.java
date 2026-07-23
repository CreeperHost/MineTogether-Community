package net.creeperhost.minetogethercommunity.cosmetic;

import com.google.common.io.ByteStreams;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.cosmetic.cape.Cape;
import net.creeperhost.minetogethercommunity.cosmetic.emote.Emote;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteAnimation;
import net.creeperhost.minetogethercommunity.cosmetic.emote.EmoteType;
import net.creeperhost.minetogethercommunity.cosmetic.hat.Hat;
import net.creeperhost.minetogethercommunity.cosmetic.hat.HatAnimation;
import net.creeperhost.minetogethercommunity.cosmetic.hat.HatRegistry;
import net.creeperhost.minetogethercommunity.cosmetic.hat.HatModelType;
import net.creeperhost.minetogethercommunity.cosmetic.tail.Tail;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailAnimation;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailElement;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailModel;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailModelParser;
import net.creeperhost.minetogethercommunity.cosmetic.wing.Wing;
import net.creeperhost.minetogethercommunity.cosmetic.wing.WingAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class CosmeticDownloader {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String CATALOG_BASE_URL = "https://api.creeper.host";
    private static final String CDN_BASE_URL = "https://cosmetic.cdn.minetogether.io";
    private static final int PAGE_LIMIT = 100;
    private static final int ASSET_DOWNLOAD_WORKERS = 3;
    private static final Pattern COSMETIC_ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}", Pattern.CASE_INSENSITIVE);

    private static volatile CosmeticDownloader instance;

    private final File cacheBase;
    private final List<CosmeticItem> hatCatalogList = new CopyOnWriteArrayList<CosmeticItem>();
    private final List<CosmeticItem> capeCatalogList = new CopyOnWriteArrayList<CosmeticItem>();
    private final List<CosmeticItem> tailCatalogList = new CopyOnWriteArrayList<CosmeticItem>();
    private final List<CosmeticItem> wingCatalogList = new CopyOnWriteArrayList<CosmeticItem>();
    private final List<CosmeticItem> emoteCatalogList = new CopyOnWriteArrayList<CosmeticItem>();
    private final ConcurrentHashMap<String, CosmeticItem> hatCatalogById = new ConcurrentHashMap<String, CosmeticItem>();
    private final ConcurrentHashMap<String, CosmeticItem> capeCatalogById = new ConcurrentHashMap<String, CosmeticItem>();
    private final ConcurrentHashMap<String, CosmeticItem> tailCatalogById = new ConcurrentHashMap<String, CosmeticItem>();
    private final ConcurrentHashMap<String, CosmeticItem> wingCatalogById = new ConcurrentHashMap<String, CosmeticItem>();
    private final ConcurrentHashMap<String, CosmeticItem> emoteCatalogById = new ConcurrentHashMap<String, CosmeticItem>();
    private final ConcurrentHashMap<String, Hat> loadedHats = new ConcurrentHashMap<String, Hat>();
    private final ConcurrentHashMap<String, Cape> loadedCapes = new ConcurrentHashMap<String, Cape>();
    private final ConcurrentHashMap<String, Tail> loadedTails = new ConcurrentHashMap<String, Tail>();
    private final ConcurrentHashMap<String, Wing> loadedWings = new ConcurrentHashMap<String, Wing>();
    private final ConcurrentHashMap<String, Emote> loadedEmotes = new ConcurrentHashMap<String, Emote>();
    private final Set<String> loadingAssetIds = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Set<String> failedAssetIds = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final AtomicInteger assetWorkerId = new AtomicInteger();
    private final ExecutorService assetDownloadExecutor;
    private volatile boolean catalogLoading;
    private volatile boolean catalogLoaded;

    public static CosmeticDownloader instance() {
        if (instance == null) {
            synchronized (CosmeticDownloader.class) {
                if (instance == null) {
                    instance = new CosmeticDownloader(new File(MineTogether.getGameDir(), "local/minetogether/cosmetics"));
                }
            }
        }
        return instance;
    }

    private CosmeticDownloader(File cacheBase) {
        this.cacheBase = cacheBase;
        this.assetDownloadExecutor = Executors.newFixedThreadPool(ASSET_DOWNLOAD_WORKERS, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "CosmeticAssetWorker-" + assetWorkerId.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public void startCatalogFetch() {
        startCatalogFetch(false);
    }

    public void refreshCatalog() {
        startCatalogFetch(true);
    }

    private void startCatalogFetch(boolean force) {
        synchronized (this) {
            if (catalogLoading || (!force && catalogLoaded)) return;
            catalogLoading = true;
            catalogLoaded = false;
            if (force) {
                clearCatalog();
            }
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                fetchCatalog();
            }
        }, "CosmeticsCatalogFetch");
        thread.setDaemon(true);
        thread.start();
    }

    private void clearCatalog() {
        hatCatalogList.clear();
        capeCatalogList.clear();
        tailCatalogList.clear();
        wingCatalogList.clear();
        emoteCatalogList.clear();
        hatCatalogById.clear();
        capeCatalogById.clear();
        tailCatalogById.clear();
        wingCatalogById.clear();
        emoteCatalogById.clear();
    }

    public void ensureAssetLoaded(final String slot, final String id) {
        if (!isSupportedAssetSlot(slot) || !isValidAssetId(id)) {
            LOGGER.warn("Refusing invalid cosmetic asset request: slot='{}', id='{}'", slot, id);
            return;
        }
        final String assetKey = assetKey(slot, id);
        if (failedAssetIds.contains(assetKey)) return;
        if ("hat".equals(slot) && loadedHats.containsKey(id)) return;
        if ("cape".equals(slot) && loadedCapes.containsKey(id)) return;
        if ("tail".equals(slot) && loadedTails.containsKey(id)) return;
        if ("wing".equals(slot) && loadedWings.containsKey(id)) return;
        if ("emote".equals(slot) && loadedEmotes.containsKey(id)) return;
        if (!loadingAssetIds.add(assetKey)) return;

        assetDownloadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if ("hat".equals(slot)) {
                        downloadAndRegisterHat(id);
                    } else if ("cape".equals(slot)) {
                        downloadAndRegisterCape(id);
                    } else if ("tail".equals(slot)) {
                        downloadAndRegisterTail(id);
                    } else if ("wing".equals(slot)) {
                        downloadAndRegisterWing(id);
                    } else if ("emote".equals(slot)) {
                        downloadAndRegisterEmote(id);
                    } else {
                        loadingAssetIds.remove(assetKey);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to download {} asset '{}'", slot, id, e);
                    if (isPermanentAssetFailure(e)) {
                        failedAssetIds.add(assetKey);
                    }
                    cleanupEmptyAssetDirectory(slot, id);
                    loadingAssetIds.remove(assetKey);
                }
            }
        });
    }

    public static boolean isSupportedAssetSlot(String slot) {
        return "hat".equals(slot) || "cape".equals(slot) || "tail".equals(slot)
                || "wing".equals(slot) || "emote".equals(slot);
    }

    public static boolean isValidAssetId(String id) {
        return id != null && COSMETIC_ID_PATTERN.matcher(id).matches();
    }

    public List<CosmeticItem> getHatCatalog() {
        return Collections.unmodifiableList(hatCatalogList);
    }

    public List<CosmeticItem> getCapeCatalog() {
        return Collections.unmodifiableList(capeCatalogList);
    }

    public List<CosmeticItem> getTailCatalog() {
        return Collections.unmodifiableList(tailCatalogList);
    }

    public List<CosmeticItem> getWingCatalog() {
        return Collections.unmodifiableList(wingCatalogList);
    }

    public List<CosmeticItem> getEmoteCatalog() {
        return Collections.unmodifiableList(emoteCatalogList);
    }

    public boolean isCatalogLoading() {
        return catalogLoading;
    }

    public boolean isCatalogLoaded() {
        return catalogLoaded;
    }

    public Hat getLoadedHat(String id) {
        return loadedHats.get(id);
    }

    public Cape getLoadedCape(String id) {
        return loadedCapes.get(id);
    }

    public Tail getLoadedTail(String id) {
        return loadedTails.get(id);
    }

    public Wing getLoadedWing(String id) {
        return loadedWings.get(id);
    }

    public Emote getLoadedEmote(String id) {
        return loadedEmotes.get(id);
    }

    public boolean isAssetLoading(String slot, String id) {
        return loadingAssetIds.contains(slot + ":" + id);
    }

    public void invalidateAndRefetch() {
        synchronized (this) {
            clearCatalog();
            loadedHats.clear();
            loadedCapes.clear();
            loadedTails.clear();
            loadedWings.clear();
            loadedEmotes.clear();
            loadingAssetIds.clear();
            failedAssetIds.clear();
            catalogLoaded = false;
            catalogLoading = false;
        }
        HatRegistry.clearModelCache();
        try {
            deleteChildren(cacheBase);
        } catch (IOException e) {
            LOGGER.warn("Failed to clear cosmetics cache at {}; cached files may be reused.", cacheBase, e);
        }
        refreshCatalog();
    }

    private void fetchCatalog() {
        try {
            mkdirs(new File(cacheBase, "hats"));
            mkdirs(new File(cacheBase, "capes"));
            mkdirs(new File(cacheBase, "tails"));
            mkdirs(new File(cacheBase, "wings"));
            mkdirs(new File(cacheBase, "emotes"));

            for (String slot : new String[]{"hat", "cape", "tail", "wing", "emote"}) {
                try {
                    fetchCatalogForSlot(slot);
                } catch (Exception e) {
                    LOGGER.error("Failed to fetch remote {} cosmetics catalog; cached cosmetics will still be listed for that slot", slot, e);
                }
            }

            loadLocalCatalog("hats", hatCatalogList, hatCatalogById);
            loadLocalCatalog("capes", capeCatalogList, capeCatalogById);
            loadLocalCatalog("tails", tailCatalogList, tailCatalogById);
            loadLocalCatalog("wings", wingCatalogList, wingCatalogById);
            loadLocalEmoteCatalog();
            LOGGER.info("Cosmetic catalog loaded: {} hats, {} capes, {} tails, {} wings, {} emotes",
                    hatCatalogList.size(), capeCatalogList.size(), tailCatalogList.size(), wingCatalogList.size(), emoteCatalogList.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load cosmetics catalog", e);
        } finally {
            catalogLoading = false;
            catalogLoaded = true;
        }
    }

    private void fetchCatalogForSlot(String slot) throws IOException {
        String cursor = null;
        int total = 0;
        do {
            StringBuilder url = new StringBuilder(CATALOG_BASE_URL)
                    .append("/minetogether/cosmetics/catalog")
                    .append("?slot=").append(slot)
                    .append("&limit=").append(PAGE_LIMIT);
            if (cursor != null) url.append("&cursor=").append(URLEncoder.encode(cursor, "UTF-8"));

            byte[] body = fetchBytes(url.toString());
            JsonObject root = new JsonParser().parse(new InputStreamReader(
                    new ByteArrayInputStream(body), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray items = root.has("cosmetics") && root.get("cosmetics").isJsonArray()
                    ? root.getAsJsonArray("cosmetics") : null;
            if (items == null) break;

            for (JsonElement element : items) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                String id = stringValue(object, "id", "");
                if (id.isEmpty()) continue;
                CosmeticItem item = new CosmeticItem(
                        id,
                        stringValue(object, "name", id),
                        stringValue(object, "author", ""),
                        stringValue(object, "mod", ""),
                        booleanValue(object, "locked", false),
                        nullableString(object, "howToUnlock"));
                addCatalogItem(slot, item);
                total++;
            }

            cursor = nullableString(root, "nextCursor");
        } while (cursor != null && !cursor.isEmpty());
        LOGGER.info("Fetched {} {} catalog entries", total, slot);
    }

    private void loadLocalCatalog(String slotDir, List<CosmeticItem> list, ConcurrentHashMap<String, CosmeticItem> byId) throws IOException {
        File dir = new File(cacheBase, slotDir);
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            File metadata = new File(child, "metadata.json");
            if (!metadata.isFile()) continue;
            InputStream inputStream = Files.newInputStream(metadata.toPath());
            try {
                JsonObject root = new JsonParser().parse(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).getAsJsonObject();
                String id = stringValue(root, "id", child.getName());
                if (byId.containsKey(id)) continue;
                CosmeticItem item = new CosmeticItem(
                        id,
                        stringValue(root, "name", id),
                        stringValue(root, "author", "Local"),
                        stringValue(root, "mod", "local"),
                        booleanValue(root, "locked", false),
                        nullableString(root, "howToUnlock"));
                list.add(item);
                byId.put(id, item);
            } finally {
                inputStream.close();
            }
        }
    }

    private void addCatalogItem(String slot, CosmeticItem item) {
        if ("hat".equals(slot)) {
            hatCatalogList.add(item);
            hatCatalogById.put(item.id(), item);
        } else if ("cape".equals(slot)) {
            capeCatalogList.add(item);
            capeCatalogById.put(item.id(), item);
        } else if ("tail".equals(slot)) {
            tailCatalogList.add(item);
            tailCatalogById.put(item.id(), item);
        } else if ("wing".equals(slot)) {
            wingCatalogList.add(item);
            wingCatalogById.put(item.id(), item);
        } else if ("emote".equals(slot)) {
            emoteCatalogList.add(item);
            emoteCatalogById.put(item.id(), item);
        }
    }

    private void loadLocalEmoteCatalog() throws IOException {
        File dir = new File(cacheBase, "emotes");
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (!child.isDirectory()) continue;
            File metadata = new File(child, "metadata.json");
            if (!metadata.isFile()) continue;
            InputStream inputStream = Files.newInputStream(metadata.toPath());
            try {
                JsonObject root = new JsonParser().parse(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).getAsJsonObject();
                String id = stringValue(root, "id", child.getName());
                if (emoteCatalogById.containsKey(id)) continue;
                EmoteType type = EmoteType.fromMetadata(stringValue(root, "type", EmoteType.SIMPLE.metadataValue()));
                if (!type.isAvailable()) {
                    LOGGER.info("Skipping local emote '{}' because runtime '{}' is not available", id, type.metadataValue());
                    continue;
                }
                CosmeticItem item = new CosmeticItem(
                        id,
                        stringValue(root, "name", id),
                        stringValue(root, "author", "Local"),
                        stringValue(root, "mod", "local"),
                        booleanValue(root, "locked", false),
                        nullableString(root, "howToUnlock"));
                emoteCatalogList.add(item);
                emoteCatalogById.put(id, item);
            } finally {
                inputStream.close();
            }
        }
    }

    private void downloadAndRegisterHat(final String id) throws Exception {
        CosmeticItem item = catalogItemOrFallback(hatCatalogById, id);
        final File itemDir = new File(new File(cacheBase, "hats"), id);
        List<String> files = fetchAndCacheFiles(CDN_BASE_URL + "/hat/" + id, itemDir);
        JsonObject metadata = readMetadata(itemDir);
        HatModelType type = HatModelType.fromMetadata(stringValue(metadata, "type", HatModelType.TC2.metadataValue()));
        if (type == HatModelType.JSON) {
            downloadAndRegisterJsonHat(id, item, itemDir, files);
            return;
        }

        String tc2File = findByExtension(files, ".tc2");
        if (tc2File == null) throw new IOException("No .tc2 file in metadata for hat '" + id + "'");
        final Hat hat = TechneLoader.load(id, item.displayName(), item.author(), item.mod(),
                item.locked(), item.howToUnlock(), Files.readAllBytes(new File(itemDir, tc2File).toPath()));
        net.creeperhost.minetogethercommunity.util.ClientTaskRunner.run(new Runnable() {
            @Override
            public void run() {
                loadedHats.put(id, hat);
                loadingAssetIds.remove("hat:" + id);
                LOGGER.info("Hat asset ready: '{}'", id);
            }
        });
    }

    private void downloadAndRegisterJsonHat(final String id, CosmeticItem item, File itemDir, List<String> files) throws Exception {
        String jsonFile = findModelJson(files);
        String pngFile = findByExtension(files, ".png");
        String animationFile = findExact(files, "animation.json");
        if (jsonFile == null) throw new IOException("No model .json file in metadata for JSON hat '" + id + "'");
        if (pngFile == null) throw new IOException("No .png file in metadata for JSON hat '" + id + "'");

        JsonObject root = parseJson(new File(itemDir, jsonFile));
        List<TailElement> elements = TailModelParser.parse(root);
        int texW = textureSize(root, 0, 64);
        int texH = textureSize(root, 1, 32);
        HatAnimation animation = parseHatAnimation(animationFile == null ? null : Files.readAllBytes(new File(itemDir, animationFile).toPath()));
        final BufferedImage image = ImageIO.read(new File(itemDir, pngFile));
        if (image == null) throw new IOException("Invalid JSON hat texture for '" + id + "'");
        final ResourceLocation location = textureLocation("hat", id);
        final Hat hat = new Hat(id, item.displayName(), item.author(), item.mod(), item.locked(), item.howToUnlock(),
                location, texW, texH, HatModelType.JSON, Collections.<net.creeperhost.minetogethercommunity.cosmetic.hat.HatCuboid>emptyList(),
                elements, new TailModel(elements, texW, texH), animation);
        net.creeperhost.minetogethercommunity.util.ClientTaskRunner.run(new Runnable() {
            @Override
            public void run() {
                Minecraft.getMinecraft().getTextureManager().loadTexture(location, new DynamicTexture(image));
                loadedHats.put(id, hat);
                loadingAssetIds.remove("hat:" + id);
                LOGGER.info("JSON hat asset ready: '{}'", id);
            }
        });
    }

    private void downloadAndRegisterCape(final String id) throws Exception {
        CosmeticItem item = catalogItemOrFallback(capeCatalogById, id);
        File itemDir = new File(new File(cacheBase, "capes"), id);
        List<String> files = fetchAndCacheFiles(CDN_BASE_URL + "/cape/" + id, itemDir);
        String pngFile = findByExtension(files, ".png");
        if (pngFile == null) throw new IOException("No .png file in metadata for cape '" + id + "'");

        final BufferedImage image = ImageIO.read(new File(itemDir, pngFile));
        if (image == null) throw new IOException("Invalid cape texture for '" + id + "'");
        final ResourceLocation location = textureLocation("cape", id);
        final Cape cape = new Cape(id, item.displayName(), item.author(), item.mod(), item.locked(), item.howToUnlock(),
                location, image.getWidth(), image.getHeight());
        net.creeperhost.minetogethercommunity.util.ClientTaskRunner.run(new Runnable() {
            @Override
            public void run() {
                Minecraft.getMinecraft().getTextureManager().loadTexture(location, new DynamicTexture(image));
                loadedCapes.put(id, cape);
                loadingAssetIds.remove("cape:" + id);
                LOGGER.info("Cape asset ready: '{}' ({}x{})", id, image.getWidth(), image.getHeight());
            }
        });
    }

    private void downloadAndRegisterTail(final String id) throws Exception {
        CosmeticItem item = catalogItemOrFallback(tailCatalogById, id);
        File itemDir = new File(new File(cacheBase, "tails"), id);
        List<String> files = fetchAndCacheFiles(CDN_BASE_URL + "/tail/" + id, itemDir);
        String jsonFile = findByExtension(files, ".json");
        String pngFile = findByExtension(files, ".png");
        String animationFile = findExact(files, "animation.json");
        if ("metadata.json".equalsIgnoreCase(jsonFile) || "animation.json".equalsIgnoreCase(jsonFile)) {
            jsonFile = findModelJson(files);
        }
        if (jsonFile == null) throw new IOException("No model .json file in metadata for tail '" + id + "'");
        if (pngFile == null) throw new IOException("No .png file in metadata for tail '" + id + "'");

        JsonObject root = parseJson(new File(itemDir, jsonFile));
        List<TailElement> elements = TailModelParser.parse(root);
        int texW = textureSize(root, 0, 64);
        int texH = textureSize(root, 1, 32);
        TailAnimation animation = parseTailAnimation(animationFile == null ? null : Files.readAllBytes(new File(itemDir, animationFile).toPath()));
        final BufferedImage image = ImageIO.read(new File(itemDir, pngFile));
        if (image == null) throw new IOException("Invalid tail texture for '" + id + "'");
        final ResourceLocation location = textureLocation("tail", id);
        final Tail tail = new Tail(id, item.displayName(), item.author(), item.mod(), item.locked(), item.howToUnlock(),
                location, texW, texH, elements, new TailModel(elements, texW, texH), animation);
        net.creeperhost.minetogethercommunity.util.ClientTaskRunner.run(new Runnable() {
            @Override
            public void run() {
                Minecraft.getMinecraft().getTextureManager().loadTexture(location, new DynamicTexture(image));
                loadedTails.put(id, tail);
                loadingAssetIds.remove("tail:" + id);
                LOGGER.info("Tail asset ready: '{}'", id);
            }
        });
    }

    private void downloadAndRegisterWing(final String id) throws Exception {
        CosmeticItem item = catalogItemOrFallback(wingCatalogById, id);
        File itemDir = new File(new File(cacheBase, "wings"), id);
        List<String> files = fetchAndCacheFiles(CDN_BASE_URL + "/wing/" + id, itemDir);
        String jsonFile = findModelJson(files);
        String pngFile = findByExtension(files, ".png");
        String animationFile = findExact(files, "animation.json");
        if (jsonFile == null) throw new IOException("No model .json file in metadata for wing '" + id + "'");
        if (pngFile == null) throw new IOException("No .png file in metadata for wing '" + id + "'");

        JsonObject root = parseJson(new File(itemDir, jsonFile));
        List<TailElement> elements = TailModelParser.parse(root);
        int texW = textureSize(root, 0, 64);
        int texH = textureSize(root, 1, 32);
        WingAnimation animation = animationFile == null ? WingAnimation.NONE : WingAnimation.fromJson(parseJson(new File(itemDir, animationFile)));
        final BufferedImage image = ImageIO.read(new File(itemDir, pngFile));
        if (image == null) throw new IOException("Invalid wing texture for '" + id + "'");
        final ResourceLocation location = textureLocation("wing", id);
        final Wing wing = new Wing(id, item.displayName(), item.author(), item.mod(), item.locked(), item.howToUnlock(),
                location, texW, texH, elements, new TailModel(elements, texW, texH), animation);
        net.creeperhost.minetogethercommunity.util.ClientTaskRunner.run(new Runnable() {
            @Override
            public void run() {
                Minecraft.getMinecraft().getTextureManager().loadTexture(location, new DynamicTexture(image));
                loadedWings.put(id, wing);
                loadingAssetIds.remove("wing:" + id);
                LOGGER.info("Wing asset ready: '{}'", id);
            }
        });
    }

    private void downloadAndRegisterEmote(String id) throws Exception {
        CosmeticItem item = catalogItemOrFallback(emoteCatalogById, id);
        File itemDir = new File(new File(cacheBase, "emotes"), id);
        List<String> files = fetchAndCacheFiles(CDN_BASE_URL + "/emote/" + id, itemDir);
        JsonObject metadata = readMetadata(itemDir);
        EmoteType type = EmoteType.fromMetadata(stringValue(metadata, "type", EmoteType.SIMPLE.metadataValue()));
        if (!type.isAvailable()) {
            loadingAssetIds.remove(assetKey("emote", id));
            LOGGER.info("Skipping emote '{}' because runtime '{}' is not available", id, type.metadataValue());
            return;
        }

        String animationFile = findExact(files, "animation.json");
        EmoteAnimation animation = parseEmoteAnimation(animationFile == null ? null : Files.readAllBytes(new File(itemDir, animationFile).toPath()));
        boolean toggle = booleanValue(metadata, "toggle", false);
        boolean allowMovement = booleanValue(metadata, "allowMovement", false);
        boolean requiresMovement = booleanValue(metadata, "requiresMovement", false);
        float previewFrame = floatValue(metadata, "previewFrame", 0.0F);
        float previewHeight = floatValue(metadata, "previewHeight", 0.28F);

        Emote emote = new Emote(id, item.displayName(), item.author(), item.mod(), item.locked(), item.howToUnlock(),
                type, toggle, allowMovement, requiresMovement, previewFrame, previewHeight, animation);
        loadedEmotes.put(id, emote);
        loadingAssetIds.remove(assetKey("emote", id));
        LOGGER.info("Emote asset ready: '{}'", id);
    }

    private HatAnimation parseHatAnimation(byte[] animationData) {
        if (animationData == null || animationData.length == 0) return HatAnimation.NONE;
        try {
            JsonObject root = new JsonParser().parse(new InputStreamReader(
                    new ByteArrayInputStream(animationData), StandardCharsets.UTF_8)).getAsJsonObject();
            return HatAnimation.fromJson(root);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse hat animation config; hat will render without animation", e);
            return HatAnimation.NONE;
        }
    }

    private TailAnimation parseTailAnimation(byte[] animationData) {
        if (animationData == null || animationData.length == 0) return TailAnimation.NONE;
        try {
            JsonObject root = new JsonParser().parse(new InputStreamReader(
                    new ByteArrayInputStream(animationData), StandardCharsets.UTF_8)).getAsJsonObject();
            return TailAnimation.fromJson(root);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse tail animation config; tail will render without animation", e);
            return TailAnimation.NONE;
        }
    }

    private EmoteAnimation parseEmoteAnimation(byte[] animationData) {
        if (animationData == null || animationData.length == 0) return EmoteAnimation.WAVE;
        try {
            JsonObject root = new JsonParser().parse(new InputStreamReader(
                    new ByteArrayInputStream(animationData), StandardCharsets.UTF_8)).getAsJsonObject();
            return EmoteAnimation.fromJson(root);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse emote animation config; using wave fallback", e);
            return EmoteAnimation.WAVE;
        }
    }

    private List<String> fetchAndCacheFiles(String cdnBase, File itemDir) throws IOException {
        mkdirs(itemDir);
        File metadataFile = new File(itemDir, "metadata.json");
        byte[] metadataBytes;
        if (metadataFile.isFile()) {
            metadataBytes = Files.readAllBytes(metadataFile.toPath());
        } else {
            metadataBytes = fetchBytes(cdnBase + "/metadata.json");
            writeBytes(metadataFile, metadataBytes);
        }

        JsonObject metadata = new JsonParser().parse(new InputStreamReader(
                new ByteArrayInputStream(metadataBytes), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray files = metadata.has("files") && metadata.get("files").isJsonArray() ? metadata.getAsJsonArray("files") : null;
        if (files == null || files.size() == 0) throw new IOException("No files array in metadata for " + cdnBase);

        List<String> names = new ArrayList<String>();
        for (JsonElement element : files) {
            String name = element.getAsString();
            names.add(name);
            File dest = new File(itemDir, name);
            if (!dest.isFile()) {
                writeBytes(dest, fetchBytes(cdnBase + "/" + name));
            }
        }
        return names;
    }

    private JsonObject readMetadata(File itemDir) throws IOException {
        File metadata = new File(itemDir, "metadata.json");
        if (!metadata.isFile()) return new JsonObject();
        InputStream inputStream = Files.newInputStream(metadata.toPath());
        try {
            return new JsonParser().parse(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).getAsJsonObject();
        } finally {
            inputStream.close();
        }
    }

    private static byte[] fetchBytes(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Accept", "application/json, image/png, */*");
        int status = connection.getResponseCode();
        InputStream inputStream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        try {
            byte[] body = inputStream == null ? new byte[0] : ByteStreams.toByteArray(inputStream);
            if (status != 200) throw new IOException("HTTP " + status + " for " + url + ": " + new String(body, StandardCharsets.UTF_8));
            return body;
        } finally {
            if (inputStream != null) inputStream.close();
            connection.disconnect();
        }
    }

    private static void writeBytes(File file, byte[] data) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) mkdirs(parent);
        FileOutputStream outputStream = new FileOutputStream(file);
        try {
            outputStream.write(data);
        } finally {
            outputStream.close();
        }
    }

    private static void mkdirs(File dir) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Could not create directory " + dir);
        }
    }

    private static void deleteChildren(File dir) throws IOException {
        if (dir == null || !dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            deleteRecursively(child);
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) return;
        if (file.isDirectory() && !Files.isSymbolicLink(file.toPath())) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete() && file.exists()) {
            throw new IOException("Could not delete " + file);
        }
    }

    private void cleanupEmptyAssetDirectory(String slot, String id) {
        File itemDir = new File(new File(cacheBase, slot + "s"), id);
        try {
            if (!itemDir.isDirectory()) return;
            File[] files = itemDir.listFiles();
            if (files != null && files.length > 0) return;
            if (itemDir.delete()) {
                LOGGER.debug("Removed empty failed {} asset directory '{}'", slot, itemDir);
            }
        } catch (Exception cleanupError) {
            LOGGER.debug("Failed to remove empty failed {} asset directory '{}'", slot, itemDir, cleanupError);
        }
    }

    private static String findByExtension(List<String> files, String extension) {
        for (String file : files) {
            if (file.toLowerCase(Locale.ROOT).endsWith(extension)) return file;
        }
        return null;
    }

    private static String findModelJson(List<String> files) {
        for (String file : files) {
            String lower = file.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".json") && !"metadata.json".equals(lower) && !"animation.json".equals(lower)) return file;
        }
        return null;
    }

    private static String findExact(List<String> files, String name) {
        for (String file : files) {
            if (name.equalsIgnoreCase(file)) return file;
        }
        return null;
    }

    private static JsonObject parseJson(File file) throws IOException {
        InputStream inputStream = Files.newInputStream(file.toPath());
        try {
            return new JsonParser().parse(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).getAsJsonObject();
        } finally {
            inputStream.close();
        }
    }

    private static int textureSize(JsonObject root, int index, int fallback) {
        if (!root.has("texture_size") || !root.get("texture_size").isJsonArray()) return fallback;
        if (root.getAsJsonArray("texture_size").size() <= index) return fallback;
        return root.getAsJsonArray("texture_size").get(index).getAsInt();
    }

    private static ResourceLocation textureLocation(String slot, String id) {
        String safeId = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        return new ResourceLocation(MineTogether.MOD_ID, "dynamic/" + slot + "/" + safeId);
    }

    private static CosmeticItem catalogItemOrFallback(ConcurrentHashMap<String, CosmeticItem> catalog, String id) {
        CosmeticItem item = catalog.get(id);
        return item == null ? new CosmeticItem(id, id, "", "", false, null) : item;
    }

    private static String assetKey(String slot, String id) {
        return slot + ":" + id;
    }

    private static boolean isPermanentAssetFailure(Exception e) {
        String message = e.getMessage();
        return message != null && (message.contains("HTTP 404") || message.contains("No files array"));
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        String value = nullableString(object, key);
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static String nullableString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsBoolean() : fallback;
    }

    private static float floatValue(JsonObject object, String key, float fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) return fallback;
        try {
            return object.get(key).getAsFloat();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
