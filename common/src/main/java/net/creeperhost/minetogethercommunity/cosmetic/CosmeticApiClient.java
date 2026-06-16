package net.creeperhost.minetogethercommunity.cosmetic;

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

/**
 * Thin client for the MineTogether cosmetics profile API.
 */
public class CosmeticApiClient {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String API_BASE = "https://api.creeper.host";

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
        Thread t = new Thread(() -> {
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
                    LOGGER.warn("Could not retrieve MT profile hash after {}s — cosmetic selections not loaded from server",
                            PROFILE_POLL_ATTEMPTS);
                    return;
                }

                String token = MineTogetherSession.getDefault().getTokenAsync().get().toString();
                String body = "{\"target\":\"" + fullHash + "\"}";

                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();

                HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + "/minetogether/cosmetics/profile"))
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .build();

                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

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
                    LOGGER.info("No selections in profile response — no cosmetics equipped");
                    return;
                }

                CosmeticSelections cs = CosmeticSelections.instance();
                // Reset before applying
                cs.selectedHatId = "";
                cs.selectedCapeId = "";

                for (JsonElement el : selections) {
                    JsonObject sel = el.getAsJsonObject();
                    String slot = sel.has("slot") ? sel.get("slot").getAsString() : null;
                    String cosmeticId = sel.has("cosmeticId") && !sel.get("cosmeticId").isJsonNull()
                            ? sel.get("cosmeticId").getAsString() : null;

                    if (slot == null || cosmeticId == null || cosmeticId.isEmpty()) continue;

                    switch (slot) {
                        case "hat"  -> cs.selectedHatId  = cosmeticId;
                        case "cape" -> cs.selectedCapeId = cosmeticId;
                        default     -> LOGGER.debug("Ignoring unhandled slot '{}' in profile response", slot);
                    }
                }

                LOGGER.info("Loaded cosmetic profile: hat='{}', cape='{}'",
                        cs.selectedHatId, cs.selectedCapeId);

            } catch (Exception e) {
                LOGGER.error("Failed to fetch cosmetic profile", e);
            }
        }, "CosmeticProfileFetch");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Asynchronously updates the server-side cosmetic selection for a single slot.
     * Fires and forgets on a daemon thread — failures are logged but not surfaced to the UI.
     *
     * @param slot       The cosmetic slot name (e.g. {@code "hat"}, {@code "cape"}).
     * @param cosmeticId The cosmetic ID to select, or {@code null} / empty to clear the slot.
     */
    public static void selectAsync(String slot, @Nullable String cosmeticId) {
        Thread t = new Thread(() -> {
            try {
                String token = MineTogetherSession.getDefault().getTokenAsync().get().toString();

                String idValue = (cosmeticId == null || cosmeticId.isEmpty()) ? "null" : "\"" + cosmeticId + "\"";
                String body = "{\"selections\":[{\"slot\":\"" + slot + "\",\"cosmeticId\":" + idValue + "}]}";

                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();

                HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + "/minetogether/cosmetics/select"))
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    LOGGER.info("Cosmetic selection updated: slot={}, id={}", slot, cosmeticId);
                } else {
                    LOGGER.warn("Cosmetic select returned HTTP {}: {}", response.statusCode(), response.body());
                }
            } catch (Exception e) {
                LOGGER.error("Failed to update cosmetic selection (slot={}, id={})", slot, cosmeticId, e);
            }
        }, "CosmeticSelect-" + slot);
        t.setDaemon(true);
        t.start();
    }
}
