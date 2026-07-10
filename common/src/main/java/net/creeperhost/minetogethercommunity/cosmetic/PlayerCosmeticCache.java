package net.creeperhost.minetogethercommunity.cosmetic;

import com.google.common.hash.Hashing;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache of cosmetic selections for remote players (everyone except the local player).
 * <p>
 * Populated when a player enters entity tracking range via
 * {@link CosmeticApiClient#fetchProfileForPlayerAsync(UUID)}.
 * The entire cache is cleared when the local player disconnects so that stale data
 * never lingers across world changes or account switches.
 */
public class PlayerCosmeticCache {

    /** UUID → resolved cosmetic selections. */
    private static final ConcurrentHashMap<UUID, CosmeticSelections> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CosmeticSelections> HASH_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> PROFILE_REVISIONS = new ConcurrentHashMap<>();

    /**
     * UUIDs whose profiles are currently being fetched.
     * Guards against concurrent duplicate requests for the same player.
     * Removed when the fetch completes (success or failure).
     */
    private static final Set<UUID> FETCHING = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Returns the resolved {@link CosmeticSelections} for the given player,
     * or {@code null} if the profile has not been fetched yet.
     */
    public static @Nullable CosmeticSelections get(UUID uuid) {
        CosmeticSelections selections = CACHE.get(uuid);
        return selections != null ? selections : HASH_CACHE.get(fullHashFromUuid(uuid));
    }

    /**
     * Stores resolved selections for the given player and removes the "currently fetching" mark.
     * Called by {@link CosmeticApiClient#fetchProfileForPlayerAsync(UUID)} when the fetch completes.
     */
    public static void put(UUID uuid, String fullHash, CosmeticSelections cs, long revision) {
        String normalizedHash = normalizeHash(fullHash);
        if (!isCurrentRevision(normalizedHash, revision)) {
            FETCHING.remove(uuid);
            return;
        }
        CACHE.put(uuid, cs);
        HASH_CACHE.put(normalizedHash, cs);
        FETCHING.remove(uuid);
    }

    /** Stores resolved selections by MineTogether full hash when a profile event has no Minecraft UUID. */
    public static void putHash(String fullHash, CosmeticSelections cs, long revision) {
        String normalizedHash = normalizeHash(fullHash);
        if (!isCurrentRevision(normalizedHash, revision)) return;
        HASH_CACHE.put(normalizedHash, cs);

        // PROFILE_EXPIRE events identify a remote player by MineTogether hash. Keep the UUID
        // index in sync too, otherwise get(UUID) would continue returning its stale entry.
        CACHE.replaceAll((uuid, ignored) -> fullHashFromUuid(uuid).equals(normalizedHash) ? cs : ignored);
    }

    /** Starts a profile-expiry refresh and invalidates any older in-flight response for that hash. */
    public static long beginHashRefresh(String fullHash) {
        String normalizedHash = normalizeHash(fullHash);
        return PROFILE_REVISIONS.merge(normalizedHash, 1L, Long::sum);
    }

    /** Captures the current refresh revision for a regular UUID-driven profile request. */
    public static long currentRevision(String fullHash) {
        return PROFILE_REVISIONS.getOrDefault(normalizeHash(fullHash), 0L);
    }

    /**
     * Attempts to mark a UUID as "currently fetching".
     *
     * @return {@code true} if this call should start the fetch (the UUID was not already tracked);
     *         {@code false} if a fetch for this UUID is already in progress.
     */
    public static boolean markFetching(UUID uuid) {
        return FETCHING.add(uuid);
    }

    /**
     * Removes a player from the fetching guard without adding a cache entry,
     * allowing a retry on next entry into tracking range.
     * Called when a fetch fails with an exception.
     */
    public static void cancelFetching(UUID uuid) {
        FETCHING.remove(uuid);
    }

    /** Clears all cached data and in-progress fetch marks — called when the local player disconnects. */
    public static void clearAll() {
        CACHE.clear();
        HASH_CACHE.clear();
        PROFILE_REVISIONS.clear();
        FETCHING.clear();
    }

    private static boolean isCurrentRevision(String normalizedHash, long revision) {
        return PROFILE_REVISIONS.getOrDefault(normalizedHash, 0L) == revision;
    }

    private static String fullHashFromUuid(UUID uuid) {
        return Hashing.sha256()
                .hashString(uuid.toString(), StandardCharsets.UTF_8)
                .toString()
                .toUpperCase(Locale.ROOT);
    }

    private static String normalizeHash(String fullHash) {
        return fullHash.toUpperCase(Locale.ROOT);
    }
}
