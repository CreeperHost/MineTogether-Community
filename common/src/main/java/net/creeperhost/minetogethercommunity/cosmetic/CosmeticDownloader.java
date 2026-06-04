package net.creeperhost.minetogethercommunity.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.architectury.platform.Platform;
import net.creeperhost.minetogethercommunity.cosmetic.hat.Hat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
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
    private static final String COSMETICS_RESOURCE = "/assets/minetogethercommunity/cosmetics.json";

    private static volatile CosmeticDownloader INSTANCE;

    private final Path cacheBase;
    private final List<Hat> hats = new CopyOnWriteArrayList<>();
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
            Files.createDirectories(hatsCache);

            try (InputStream is = CosmeticDownloader.class.getResourceAsStream(COSMETICS_RESOURCE)) {
                if (is == null) {
                    LOGGER.error("cosmetics.json not found in resources");
                    return;
                }
                var root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonArray hatsArray = root.getAsJsonArray("hats");
                LOGGER.info("Found {} hats in cosmetics.json", hatsArray.size());

                for (JsonElement el : hatsArray) {
                    var obj = el.getAsJsonObject();
                    String name = obj.get("name").getAsString();
                    String url = obj.get("url").getAsString();
                    try {
                        loadHat(client, hatsCache, name, url);
                    } catch (Exception e) {
                        LOGGER.warn("Skipping hat '{}': {}", name, e.getMessage());
                    }
                }
            }

            LOGGER.info("Loaded {} hats", hats.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load cosmetics", e);
        } finally {
            loading = false;
            loaded = true;
        }
    }

    private void loadHat(HttpClient client, Path cacheDir, String name, String url) throws Exception {
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
        Hat hat = TechneLoader.load(name, data);
        hats.add(hat);
    }

    private byte[] fetchBytes(HttpClient client, String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        return resp.body();
    }
}
