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
    //TODO Temp link
    private static final String COSMETICS_URL = "https://vristingtest.playat.ch/cosmetics.json";

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

            LOGGER.info("Fetching cosmetics list from {}", COSMETICS_URL);
            byte[] indexBytes = fetchBytes(client, COSMETICS_URL);
            var root = JsonParser.parseReader(new InputStreamReader(
                    new java.io.ByteArrayInputStream(indexBytes), StandardCharsets.UTF_8)
            ).getAsJsonObject();

            JsonArray hatsArray = root.getAsJsonArray("hats");
            LOGGER.info("Found {} hats in cosmetics.json", hatsArray.size());
            for (JsonElement el : hatsArray) {
                var obj = el.getAsJsonObject();
                String name = obj.get("name").getAsString();
                String url = obj.get("url").getAsString();
                String author = obj.has("author") ? obj.get("author").getAsString() : "";
                String mod = obj.has("mod") ? obj.get("mod").getAsString() : "";
                try {
                    loadHat(client, hatsCache, name, url, author, mod);
                } catch (Exception e) {
                    LOGGER.warn("Skipping hat '{}': {}", name, e.getMessage());
                }
            }

            if (root.has("capes")) {
                JsonArray capesArray = root.getAsJsonArray("capes");
                LOGGER.info("Found {} capes in cosmetics.json", capesArray.size());
                for (JsonElement el : capesArray) {
                    var obj = el.getAsJsonObject();
                    String name = obj.get("name").getAsString();
                    String url = obj.get("url").getAsString();
                    String author = obj.has("author") ? obj.get("author").getAsString() : "";
                    String mod = obj.has("mod") ? obj.get("mod").getAsString() : "";
                    try {
                        loadCape(client, capesCache, name, url, author, mod);
                    } catch (Exception e) {
                        LOGGER.warn("Skipping cape '{}': {}", name, e.getMessage());
                    }
                }
            }

            LOGGER.info("Loaded {} hats, {} capes", hats.size(), capes.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load cosmetics", e);
        } finally {
            loading = false;
            loaded = true;
        }
    }

    private void loadHat(HttpClient client, Path cacheDir, String name, String url, String author, String mod) throws Exception {
        String filename = name + ".tc2";
        Path cached = cacheDir.resolve(filename);
        byte[] data;
        if (Files.exists(cached)) {
            LOGGER.info("Loading hat '{}' from cache", name);
            data = Files.readAllBytes(cached);
        } else {
            LOGGER.info("Downloading hat '{}' from {}", name, url);
            data = fetchBytes(client, url);
            Files.write(cached, data);
        }
        Hat hat = TechneLoader.load(name, author, mod, data);
        hats.add(hat);
    }

    private void loadCape(HttpClient client, Path cacheDir, String name, String url, String author, String mod) throws Exception {
        String filename = name + ".png";
        Path cached = cacheDir.resolve(filename);
        byte[] data;
        if (Files.exists(cached)) {
            LOGGER.info("Loading cape '{}' from cache", name);
            data = Files.readAllBytes(cached);
        } else {
            LOGGER.info("Downloading cape '{}' from {}", name, url);
            data = fetchBytes(client, url);
            Files.write(cached, data);
        }

        ResourceLocation location = ResourceLocation.fromNamespaceAndPath("minetogethercommunity", "cape/" + name.toLowerCase().replace(' ', '_'));
        byte[] finalData = data;
        Minecraft.getInstance().execute(() -> {
            try {
                NativeImage img =
                        NativeImage.read(new java.io.ByteArrayInputStream(finalData));
                DynamicTexture tex = new DynamicTexture(img);
                Minecraft.getInstance().getTextureManager().register(location, tex);
                capes.add(new Cape(name, name, author, mod, location, img.getWidth(), img.getHeight()));
                LOGGER.info("Registered cape texture '{}' ({}x{})", name, img.getWidth(), img.getHeight());
            } catch (Exception e) {
                LOGGER.warn("Failed to register cape texture '{}': {}", name, e.getMessage());
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
