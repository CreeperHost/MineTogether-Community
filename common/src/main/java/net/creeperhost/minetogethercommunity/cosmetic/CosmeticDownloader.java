package net.creeperhost.minetogethercommunity.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.architectury.platform.Platform;
import net.creeperhost.minetogethercommunity.cosmetic.cape.Cape;
import net.creeperhost.minetogethercommunity.cosmetic.hat.Hat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CosmeticDownloader {

    private static final Logger LOGGER = LogManager.getLogger();

    /** Base URL of the MineTogether profile/cosmetics API. */
    private static final String CATALOG_BASE_URL = "https://api.creeper.host";

    /**
     * CDN base URL used to download actual asset files.
     * Hat assets are fetched from: {CDN_BASE}/hat/{id}/{id}.tc2
     * Cape assets are fetched from: {CDN_BASE}/cape/{id}/{id}.png
     */
    private static final String CDN_BASE_URL = "https://localhost:61713";

    private static final int PAGE_LIMIT = 100;

    private static volatile CosmeticDownloader INSTANCE;

    private final Path cacheBase;
    private final List<Hat> hats = new CopyOnWriteArrayList<>();
    private final List<Cape> capes = new CopyOnWriteArrayList<>();
    private volatile boolean loading = false;
    private volatile boolean loaded = false;

    private CosmeticDownloader(Path cacheBase) {
        this.cacheBase = cacheBase;
    }

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

    public List<Hat> getHats() {
        if (!loading && !loaded) startDownload();
        return Collections.unmodifiableList(hats);
    }

    public List<Cape> getCapes() {
        if (!loading && !loaded) startDownload();
        return Collections.unmodifiableList(capes);
    }

    public boolean isLoading() { return loading; }
    public boolean isLoaded() { return loaded; }

    public void startDownload() {
        synchronized (this) {
            if (loading || loaded) return;
            loading = true;
        }
        Thread t = new Thread(this::downloadAll, "CosmeticsDownloader");
        t.setDaemon(true);
        t.start();
    }

    private void downloadAll() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            Path hatsCache = cacheBase.resolve("hats");
            Path capesCache = cacheBase.resolve("capes");
            Files.createDirectories(hatsCache);
            Files.createDirectories(capesCache);

            fetchAllCatalogForSlot(client, "hat", hatsCache);
            fetchAllCatalogForSlot(client, "cape", capesCache);

            LOGGER.info("Loaded {} hats, {} capes", hats.size(), capes.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load cosmetics", e);
        } finally {
            loading = false;
            loaded = true;
        }
    }

    /**
     * Pages through the catalog API for a given slot and loads each item.
     */
    private void fetchAllCatalogForSlot(HttpClient client, String slot, Path cacheDir) throws IOException, InterruptedException {
        String cursor = null;
        int totalFetched = 0;

        do {
            StringBuilder url = new StringBuilder(CATALOG_BASE_URL)
                    .append("/minetogether/cosmetics/catalog")
                    .append("?slot=").append(slot)
                    .append("&limit=").append(PAGE_LIMIT);
            if (cursor != null) url.append("&cursor=").append(cursor);

            LOGGER.info("Fetching {} catalog (cursor={})", slot, cursor == null ? "start" : cursor);
            byte[] body = fetchBytes(client, url.toString());
            var root = JsonParser.parseReader(new InputStreamReader(
                    new java.io.ByteArrayInputStream(body), StandardCharsets.UTF_8
            )).getAsJsonObject();

            JsonArray items = root.getAsJsonArray("cosmetics");
            for (JsonElement el : items) {
                var obj = el.getAsJsonObject();
                String id = obj.get("id").getAsString();
                String name = obj.get("name").getAsString();
                String author = obj.has("author") && !obj.get("author").isJsonNull()
                        ? obj.get("author").getAsString() : "";
                boolean locked = obj.get("locked").getAsBoolean();
                String howToUnlock = obj.has("howToUnlock") && !obj.get("howToUnlock").isJsonNull()
                        ? obj.get("howToUnlock").getAsString() : null;

                try {
                    if (slot.equals("hat")) {
                        loadHat(client, cacheDir, id, name, author, locked, howToUnlock);
                    } else {
                        loadCape(client, cacheDir, id, name, author, locked, howToUnlock);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Skipping {} '{}': {}", slot, id, e.getMessage());
                }
                totalFetched++;
            }

            cursor = root.has("nextCursor") && !root.get("nextCursor").isJsonNull()
                    ? root.get("nextCursor").getAsString()
                    : null;

        } while (cursor != null);

        LOGGER.info("Fetched {} {} catalog items", totalFetched, slot);
    }

    private void loadHat(HttpClient client, Path cacheDir, String id, String name, String author,
                         boolean locked, String howToUnlock) throws Exception {
        String filename = id + ".tc2";
        Path cached = cacheDir.resolve(filename);
        byte[] data;
        if (Files.exists(cached)) {
            LOGGER.info("Loading hat '{}' from cache", id);
            data = Files.readAllBytes(cached);
        } else {
            // CDN convention: /{slot}/{id}/{id}.tc2
            String assetUrl = CDN_BASE_URL + "/hat/" + id + "/" + id + ".tc2";
            LOGGER.info("Downloading hat '{}' from {}", id, assetUrl);
            data = fetchBytes(client, assetUrl);
            Files.write(cached, data);
        }
        Hat hat = TechneLoader.load(id, name, author, "", locked, howToUnlock, data);
        hats.add(hat);
    }

    private void loadCape(HttpClient client, Path cacheDir, String id, String name, String author,
                          boolean locked, String howToUnlock) throws Exception {
        String filename = id + ".png";
        Path cached = cacheDir.resolve(filename);
        byte[] data;
        if (Files.exists(cached)) {
            LOGGER.info("Loading cape '{}' from cache", id);
            data = Files.readAllBytes(cached);
        } else {
            // CDN convention: /{slot}/{id}/{id}.png
            String assetUrl = CDN_BASE_URL + "/cape/" + id + "/" + id + ".png";
            LOGGER.info("Downloading cape '{}' from {}", id, assetUrl);
            data = fetchBytes(client, assetUrl);
            Files.write(cached, data);
        }

        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                "minetogethercommunity", "cape/" + id.toLowerCase().replace(' ', '_'));
        byte[] finalData = data;
        Minecraft.getInstance().execute(() -> {
            try {
                NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(finalData));
                DynamicTexture tex = new DynamicTexture(img);
                Minecraft.getInstance().getTextureManager().register(location, tex);
                capes.add(new Cape(id, name, author, "", locked, howToUnlock, location, img.getWidth(), img.getHeight()));
                LOGGER.info("Registered cape texture '{}' ({}x{})", id, img.getWidth(), img.getHeight());
            } catch (Exception e) {
                LOGGER.warn("Failed to register cape texture '{}': {}", id, e.getMessage());
            }
        });
    }

    private byte[] fetchBytes(HttpClient client, String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        return resp.body();
    }
}
