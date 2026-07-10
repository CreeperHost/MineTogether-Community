package net.creeperhost.minetogethercommunity.cosmetic;

import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogether.session.MineTogetherSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thin client for the MineTogether cosmetics profile API.
 */
public class CosmeticApiClient {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String API_BASE = "https://api.creeper.host";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final AtomicLong REQUEST_WORKER_ID = new AtomicLong();
    private static final ExecutorService REQUEST_EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "CosmeticApiWorker-" + REQUEST_WORKER_ID.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Max attempts to wait for the MT profile to have a full hash before giving up. */
    private static final int PROFILE_POLL_ATTEMPTS = 20;
    private static final long PROFILE_POLL_DELAY_MS = 1000;

    /**
     * Asynchronously fetches the player's cosmetic selections from the server and
     * applies them to the in-memory {@link CosmeticSelections} singleton.
     * <p>
     * Polls until the MT profile hash is available (up to ~20 s), then calls
     * {@code POST /minetogether/cosmetics/profile}.
     */
    public static void fetchProfileAsync() {
        REQUEST_EXECUTOR.execute(() -> {
            try {
                // Wait for the own profile to have a full hash (chat system may still be connecting)
                String fullHash = null;
                for (int i = 0; i < PROFILE_POLL_ATTEMPTS; i++) {
                    try {
                        var profile = MineTogetherChat.getOurProfile();
                        if (profile.hasFullHash() && profile.hasAccount()) {
                            fullHash = profile.getFullHash();
                            break;
                        }
                    } catch (Exception ignored) {
                        // profile system not ready yet
                    }
                    Thread.sleep(PROFILE_POLL_DELAY_MS);
                }

                if (fullHash == null) {
                    LOGGER.warn("Could not retrieve MT profile hash after {}s - cosmetic selections not loaded from server",
                            PROFILE_POLL_ATTEMPTS);
                    return;
                }

                String token = MineTogetherSession.getDefault().getTokenAsync()
                        .get(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS).toString();
                JsonObject bodyObj = new JsonObject();
                bodyObj.addProperty("target", fullHash);
                String body = bodyObj.toString();

                HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + "/minetogether/cosmetics/profile"))
                        .timeout(REQUEST_TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .build();

                HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() != 200) {
                    LOGGER.warn("Cosmetic profile returned HTTP {}: {}", response.statusCode(),
                            new String(response.body(), StandardCharsets.UTF_8));
                    return;
                }

                JsonObject root = JsonParser.parseReader(new InputStreamReader(
                        new java.io.ByteArrayInputStream(response.body()), StandardCharsets.UTF_8
                )).getAsJsonObject();

                JsonObject cosmetics = root.has("cosmetics") ? root.getAsJsonObject("cosmetics") : null;
                if (cosmetics == null) {
                    LOGGER.warn("No 'cosmetics' object in profile response");
                    return;
                }

                JsonArray selections = cosmetics.has("selections") ? cosmetics.getAsJsonArray("selections") : null;
                if (selections == null) {
                    LOGGER.info("No selections in profile response - no cosmetics equipped");
                    return;
                }

                CosmeticSelections cs = CosmeticSelections.instance();
                cs.selectedHatId  = "";
                cs.selectedCapeId = "";
                cs.selectedTailId = "";
                cs.selectedWingId = "";

                for (JsonElement el : selections) {
                    JsonObject sel = el.getAsJsonObject();
                    String slot = sel.has("slot") ? sel.get("slot").getAsString() : null;
                    String cosmeticId = sel.has("cosmeticId") && !sel.get("cosmeticId").isJsonNull()
                            ? sel.get("cosmeticId").getAsString() : null;

                    if (!isValidSelection(slot, cosmeticId)) continue;

                    switch (slot) {
                        case "hat"  -> cs.selectedHatId  = cosmeticId;
                        case "cape" -> cs.selectedCapeId = cosmeticId;
                        case "tail" -> cs.selectedTailId = cosmeticId;
                        case "wing" -> cs.selectedWingId = cosmeticId;
                        default     -> LOGGER.debug("Ignoring unhandled slot '{}' in profile response", slot);
                    }
                }

                LOGGER.info("Loaded cosmetic profile: hat='{}', cape='{}', tail='{}', wing='{}'",
                        cs.selectedHatId, cs.selectedCapeId, cs.selectedTailId, cs.selectedWingId);

                if (!cs.selectedHatId.isEmpty())
                    CosmeticDownloader.instance().ensureAssetLoaded("hat",  cs.selectedHatId);
                if (!cs.selectedCapeId.isEmpty())
                    CosmeticDownloader.instance().ensureAssetLoaded("cape", cs.selectedCapeId);
                if (!cs.selectedTailId.isEmpty())
                    CosmeticDownloader.instance().ensureAssetLoaded("tail", cs.selectedTailId);
                if (!cs.selectedWingId.isEmpty())
                    CosmeticDownloader.instance().ensureAssetLoaded("wing", cs.selectedWingId);

            } catch (Exception e) {
                LOGGER.error("Failed to fetch cosmetic profile", e);
            }
        });
    }

    /**
     * Asynchronously fetches the cosmetic selections for a <em>remote</em> player (not the local player)
     * and stores the result in {@link PlayerCosmeticCache}.
     * <p>
     * The MT full-hash is derived from the player's Minecraft UUID using the same SHA-256 formula
     * as {@link net.creeperhost.minetogethercommunity.chat.ChatAuthImpl#getHash()} - no extra API
     * call is needed to convert UUID - hash.
     *
     * @param uuid The Minecraft UUID of the other player.
     */
    public static void fetchProfileForPlayerAsync(UUID uuid) {
        // MT full hash = SHA-256( uuid.toString() ).toUpperCase() - matches ChatAuthImpl.getHash()
        String fullHash = Hashing.sha256()
                .hashString(uuid.toString(), StandardCharsets.UTF_8)
                .toString()
                .toUpperCase(Locale.ROOT);

        fetchProfileForTargetAsync(fullHash, uuid, PlayerCosmeticCache.currentRevision(fullHash));
    }

    /**
     * Asynchronously fetches cosmetic selections by MineTogether full hash and stores them in
     * {@link PlayerCosmeticCache}'s hash index. Used when profile events do not expose a Minecraft UUID.
     */
    public static void fetchProfileForHashAsync(String fullHash) {
        String normalizedHash = fullHash.toUpperCase(Locale.ROOT);
        fetchProfileForTargetAsync(normalizedHash, null, PlayerCosmeticCache.beginHashRefresh(normalizedHash));
    }

    private static void fetchProfileForTargetAsync(String fullHash, @Nullable UUID uuid, long revision) {
        REQUEST_EXECUTOR.execute(() -> {
            try {
                String token = MineTogetherSession.getDefault().getTokenAsync()
                        .get(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS).toString();
                JsonObject bodyObj = new JsonObject();
                bodyObj.addProperty("target", fullHash);
                String body = bodyObj.toString();

                HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + "/minetogether/cosmetics/profile"))
                        .timeout(REQUEST_TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .build();

                HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

                // Always produce an entry even if the player has no cosmetics, so render layers
                // know the fetch is done and won't try again until the player re-enters range.
                CosmeticSelections cs = new CosmeticSelections();

                if (response.statusCode() == 200) {
                    JsonObject root = JsonParser.parseReader(new InputStreamReader(
                            new java.io.ByteArrayInputStream(response.body()), StandardCharsets.UTF_8
                    )).getAsJsonObject();

                    JsonObject cosmetics = root.has("cosmetics") ? root.getAsJsonObject("cosmetics") : null;
                    if (cosmetics != null) {
                        JsonArray selections = cosmetics.has("selections")
                                ? cosmetics.getAsJsonArray("selections") : null;
                        if (selections != null) {
                            for (JsonElement el : selections) {
                                JsonObject sel = el.getAsJsonObject();
                                String slot = sel.has("slot") ? sel.get("slot").getAsString() : null;
                                String cosmeticId = sel.has("cosmeticId") && !sel.get("cosmeticId").isJsonNull()
                                        ? sel.get("cosmeticId").getAsString() : null;
                                if (!isValidSelection(slot, cosmeticId)) continue;
                                switch (slot) {
                                    case "hat"  -> cs.selectedHatId  = cosmeticId;
                                    case "cape" -> cs.selectedCapeId = cosmeticId;
                                    case "tail" -> cs.selectedTailId = cosmeticId;
                                    case "wing" -> cs.selectedWingId = cosmeticId;
                                    default     -> LOGGER.debug("Ignoring unhandled slot '{}' for profile {}", slot, profileLogName(uuid, fullHash));
                                }
                            }
                        }
                    }
                    LOGGER.debug("Loaded cosmetic profile for {}: hat='{}', cape='{}'",
                            profileLogName(uuid, fullHash), cs.selectedHatId, cs.selectedCapeId);
                } else {
                    // 404 = no MT account / no cosmetics - treat as "no cosmetics" silently
                    LOGGER.debug("Cosmetic profile for {} returned HTTP {} - no cosmetics equipped",
                            profileLogName(uuid, fullHash), response.statusCode());
                }

                // Trigger lazy asset downloads for whatever this remote player is wearing
                if (!cs.selectedHatId.isEmpty())
                    CosmeticDownloader.instance().ensureAssetLoaded("hat",  cs.selectedHatId);
                if (!cs.selectedCapeId.isEmpty())
                    CosmeticDownloader.instance().ensureAssetLoaded("cape", cs.selectedCapeId);
                if (!cs.selectedTailId.isEmpty())
                    CosmeticDownloader.instance().ensureAssetLoaded("tail", cs.selectedTailId);
                if (!cs.selectedWingId.isEmpty())
                    CosmeticDownloader.instance().ensureAssetLoaded("wing", cs.selectedWingId);

                if (uuid != null) {
                    PlayerCosmeticCache.put(uuid, fullHash, cs, revision);
                } else {
                    PlayerCosmeticCache.putHash(fullHash, cs, revision);
                }

            } catch (Exception e) {
                LOGGER.error("Failed to fetch cosmetic profile for {}", profileLogName(uuid, fullHash), e);
                // Cancel the fetching mark so the next entry into range triggers a retry
                if (uuid != null) {
                    PlayerCosmeticCache.cancelFetching(uuid);
                }
            }
        });
    }

    private static String profileLogName(@Nullable UUID uuid, String fullHash) {
        return uuid != null ? uuid.toString() : fullHash;
    }

    private static boolean isValidSelection(@Nullable String slot, @Nullable String cosmeticId) {
        return isSelectionSlot(slot) && cosmeticId != null && CosmeticDownloader.isValidAssetId(cosmeticId);
    }

    private static boolean isSelectionSlot(@Nullable String slot) {
        return "hat".equals(slot) || "cape".equals(slot) || "tail".equals(slot) || "wing".equals(slot);
    }

    /**
     * Asynchronously updates the server-side cosmetic selection for a single slot.
     * Fires and forgets on a daemon thread - failures are logged but not surfaced to the UI.
     *
     * @param slot       The cosmetic slot name (e.g. {@code "hat"}, {@code "cape"}).
     * @param cosmeticId The cosmetic ID to select, or {@code null} / empty to clear the slot.
     */
    public static void selectAsync(String slot, @Nullable String cosmeticId) {
        if (!isSelectionSlot(slot)) {
            LOGGER.warn("Refusing cosmetic selection for unsupported slot '{}'", slot);
            return;
        }
        if (cosmeticId != null && !cosmeticId.isEmpty() && !cosmeticId.equals("none")
                && !CosmeticDownloader.isValidAssetId(cosmeticId)) {
            LOGGER.warn("Refusing cosmetic selection with invalid id '{}'", cosmeticId);
            return;
        }
        REQUEST_EXECUTOR.execute(() -> {
            try {
                String token = MineTogetherSession.getDefault().getTokenAsync()
                        .get(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS).toString();

                boolean clear = cosmeticId == null || cosmeticId.isEmpty() || cosmeticId.equals("none");

                JsonArray selectionsArr = new JsonArray();
                JsonObject selection = new JsonObject();
                selection.addProperty("slot", slot);
                if (!clear) {
                    selection.addProperty("cosmeticId", cosmeticId);
                }
                selectionsArr.add(selection);
                JsonObject bodyObj = new JsonObject();
                bodyObj.add("selections", selectionsArr);
                String body = bodyObj.toString();

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(API_BASE + "/minetogether/cosmetics/select"))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token);

                if (clear) {
                    requestBuilder.method("DELETE", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                } else {
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                }

                HttpResponse<String> response = HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    LOGGER.info("Cosmetic selection updated: slot={}, id={}", slot, cosmeticId);
                } else {
                    LOGGER.warn("Cosmetic select returned HTTP {}: {}", response.statusCode(), response.body());
                }
            } catch (Exception e) {
                LOGGER.error("Failed to update cosmetic selection (slot={}, id={})", slot, cosmeticId, e);
            }
        });
    }
}
