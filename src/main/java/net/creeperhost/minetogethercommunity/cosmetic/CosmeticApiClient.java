package net.creeperhost.minetogethercommunity.cosmetic;

import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogether.session.MineTogetherSession;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class CosmeticApiClient {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String API_BASE = "https://api.creeper.host";
    private static final int PROFILE_POLL_ATTEMPTS = 20;
    private static final long PROFILE_POLL_DELAY_MS = 1000L;

    private CosmeticApiClient() {
    }

    public static void fetchProfileAsync() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String fullHash = waitForOwnProfileHash();
                    if (fullHash == null) {
                        LOGGER.warn("Could not retrieve MineTogether profile hash; cosmetic selections not loaded");
                        return;
                    }
                    CosmeticSelections selections = fetchSelections(fullHash, true);
                    copySelections(selections, CosmeticSelections.instance());
                    loadSelectedAssets(CosmeticSelections.instance());
                    LOGGER.info("Loaded cosmetic profile: hat='{}', cape='{}', tail='{}', wing='{}'",
                            CosmeticSelections.instance().selectedHatId,
                            CosmeticSelections.instance().selectedCapeId,
                            CosmeticSelections.instance().selectedTailId,
                            CosmeticSelections.instance().selectedWingId);
                } catch (Exception e) {
                    LOGGER.error("Failed to fetch cosmetic profile", e);
                }
            }
        }, "CosmeticProfileFetch");
        thread.setDaemon(true);
        thread.start();
    }

    public static void fetchProfileForPlayerAsync(final UUID uuid) {
        if (uuid == null || !PlayerCosmeticCache.markFetching(uuid)) return;
        final String fullHash = Hashing.sha256()
                .hashString(uuid.toString(), StandardCharsets.UTF_8)
                .toString()
                .toUpperCase(Locale.ROOT);
        fetchProfileForTargetAsync(fullHash, uuid, PlayerCosmeticCache.currentRevision(fullHash));
    }

    public static void fetchProfileForHashAsync(final String fullHash) {
        if (fullHash == null || fullHash.trim().isEmpty()) return;
        String normalized = fullHash.trim().toUpperCase(Locale.ROOT);
        fetchProfileForTargetAsync(normalized, null, PlayerCosmeticCache.beginHashRefresh(normalized));
    }

    private static void fetchProfileForTargetAsync(final String fullHash, final UUID uuid, final long revision) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    CosmeticSelections selections = fetchSelections(fullHash, true);
                    loadSelectedAssets(selections);
                    if (uuid != null) {
                        PlayerCosmeticCache.put(uuid, fullHash, selections, revision);
                    } else {
                        PlayerCosmeticCache.putHash(fullHash, selections, revision);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to fetch cosmetic profile for player {}", profileLogName(uuid, fullHash), e);
                    if (uuid != null) {
                        PlayerCosmeticCache.cancelFetching(uuid);
                    }
                }
            }
        }, "CosmeticProfileFetch-" + profileLogName(uuid, fullHash));
        thread.setDaemon(true);
        thread.start();
    }

    private static String profileLogName(UUID uuid, String fullHash) {
        return uuid != null ? uuid.toString() : fullHash;
    }

    public static void selectAsync(final String slot, final String cosmeticId) {
        if (!isSelectionSlot(slot)) {
            LOGGER.warn("Refusing cosmetic selection for unsupported slot '{}'", slot);
            return;
        }
        if (cosmeticId != null && !cosmeticId.isEmpty() && !"none".equals(cosmeticId)
                && !CosmeticDownloader.isValidAssetId(cosmeticId)) {
            LOGGER.warn("Refusing cosmetic selection with invalid id '{}'", cosmeticId);
            return;
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean clear = cosmeticId == null || cosmeticId.isEmpty() || "none".equals(cosmeticId);
                    JsonArray selections = new JsonArray();
                    JsonObject selection = new JsonObject();
                    selection.addProperty("slot", slot);
                    if (!clear) {
                        selection.addProperty("cosmeticId", cosmeticId);
                    }
                    selections.add(selection);
                    JsonObject body = new JsonObject();
                    body.add("selections", selections);

                    HttpResponse response = sendJson(API_BASE + "/minetogether/cosmetics/select",
                            clear ? "DELETE" : "POST", body.toString(), true);
                    if (response.statusCode == 200) {
                        LOGGER.info("Cosmetic selection updated: slot={}, id={}", slot, cosmeticId);
                    } else {
                        LOGGER.warn("Cosmetic select returned HTTP {}: {}", response.statusCode, response.bodyText());
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to update cosmetic selection (slot={}, id={})", slot, cosmeticId, e);
                }
            }
        }, "CosmeticSelect-" + slot);
        thread.setDaemon(true);
        thread.start();
    }

    private static String waitForOwnProfileHash() throws InterruptedException {
        for (int i = 0; i < PROFILE_POLL_ATTEMPTS; i++) {
            try {
                Profile profile = MineTogetherChat.getOurProfile();
                if (profile != null && profile.hasFullHash() && profile.hasAccount()) {
                    return profile.getFullHash();
                }
                if (MineTogetherChat.CHAT_AUTH != null) {
                    return MineTogetherChat.CHAT_AUTH.getHash();
                }
            } catch (Exception ignored) {
            }
            Thread.sleep(PROFILE_POLL_DELAY_MS);
        }
        return MineTogetherChat.CHAT_AUTH == null ? null : MineTogetherChat.CHAT_AUTH.getHash();
    }

    private static CosmeticSelections fetchSelections(String fullHash, boolean requireAuth) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("target", fullHash);
        HttpResponse response = sendJson(API_BASE + "/minetogether/cosmetics/profile", "POST", body.toString(), requireAuth);
        CosmeticSelections selections = new CosmeticSelections();

        if (response.statusCode != 200) {
            LOGGER.debug("Cosmetic profile returned HTTP {} for {}", response.statusCode, fullHash);
            return selections;
        }

        JsonObject root = new JsonParser().parse(new InputStreamReader(
                new ByteArrayInputStream(response.body), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject cosmetics = root.has("cosmetics") && root.get("cosmetics").isJsonObject()
                ? root.getAsJsonObject("cosmetics") : null;
        JsonArray selected = cosmetics != null && cosmetics.has("selections") && cosmetics.get("selections").isJsonArray()
                ? cosmetics.getAsJsonArray("selections") : null;
        if (selected == null) return selections;

        for (JsonElement element : selected) {
            if (!element.isJsonObject()) continue;
            JsonObject selectedObject = element.getAsJsonObject();
            String slot = selectedObject.has("slot") && !selectedObject.get("slot").isJsonNull()
                    ? selectedObject.get("slot").getAsString() : "";
            String id = selectedObject.has("cosmeticId") && !selectedObject.get("cosmeticId").isJsonNull()
                    ? selectedObject.get("cosmeticId").getAsString() : "";
            if (!isSelectionSlot(slot) || !CosmeticDownloader.isValidAssetId(id)) continue;
            if ("hat".equals(slot)) selections.selectedHatId = id;
            else if ("cape".equals(slot)) selections.selectedCapeId = id;
            else if ("tail".equals(slot)) selections.selectedTailId = id;
            else if ("wing".equals(slot)) selections.selectedWingId = id;
        }

        return selections;
    }

    private static boolean isSelectionSlot(String slot) {
        return "hat".equals(slot) || "cape".equals(slot) || "tail".equals(slot) || "wing".equals(slot);
    }

    private static void copySelections(CosmeticSelections source, CosmeticSelections target) {
        target.selectedHatId = source.selectedHatId;
        target.selectedCapeId = source.selectedCapeId;
        target.selectedTailId = source.selectedTailId;
        target.selectedWingId = source.selectedWingId;
    }

    private static void loadSelectedAssets(CosmeticSelections selections) {
        if (!selections.selectedHatId.isEmpty()) CosmeticDownloader.instance().ensureAssetLoaded("hat", selections.selectedHatId);
        if (!selections.selectedCapeId.isEmpty()) CosmeticDownloader.instance().ensureAssetLoaded("cape", selections.selectedCapeId);
        if (!selections.selectedTailId.isEmpty()) CosmeticDownloader.instance().ensureAssetLoaded("tail", selections.selectedTailId);
        if (!selections.selectedWingId.isEmpty()) CosmeticDownloader.instance().ensureAssetLoaded("wing", selections.selectedWingId);
    }

    private static HttpResponse sendJson(String url, String method, String body, boolean auth) throws Exception {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        if (auth) {
            Object token = MineTogetherSession.getDefault().getTokenAsync().get();
            if (token == null) {
                throw new IllegalStateException("MineTogether session token is unavailable");
            }
            connection.setRequestProperty("Authorization", "Bearer " + token.toString());
        }
        connection.setDoOutput(true);
        OutputStream outputStream = connection.getOutputStream();
        try {
            outputStream.write(payload);
        } finally {
            outputStream.close();
        }
        int status = connection.getResponseCode();
        java.io.InputStream inputStream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        byte[] responseBody = inputStream == null ? new byte[0] : ByteStreams.toByteArray(inputStream);
        return new HttpResponse(status, responseBody);
    }

    private static class HttpResponse {
        final int statusCode;
        final byte[] body;

        HttpResponse(int statusCode, byte[] body) {
            this.statusCode = statusCode;
            this.body = body == null ? new byte[0] : body;
        }

        String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
