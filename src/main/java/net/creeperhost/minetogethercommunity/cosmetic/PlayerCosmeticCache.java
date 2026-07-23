package net.creeperhost.minetogethercommunity.cosmetic;

import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerCosmeticCache {

    private static final ConcurrentHashMap<UUID, CosmeticSelections> CACHE = new ConcurrentHashMap<UUID, CosmeticSelections>();
    private static final ConcurrentHashMap<String, CosmeticSelections> HASH_CACHE = new ConcurrentHashMap<String, CosmeticSelections>();
    private static final ConcurrentHashMap<String, Long> PROFILE_REVISIONS = new ConcurrentHashMap<String, Long>();
    private static final Set<UUID> FETCHING = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    private PlayerCosmeticCache() {
    }

    public static CosmeticSelections get(UUID uuid) {
        CosmeticSelections selections = CACHE.get(uuid);
        return selections != null ? selections : HASH_CACHE.get(fullHashFromUuid(uuid));
    }

    public static void put(UUID uuid, String fullHash, CosmeticSelections selections, long revision) {
        String normalized = normalizeHash(fullHash);
        if (!isCurrentRevision(normalized, revision)) {
            FETCHING.remove(uuid);
            return;
        }
        CACHE.put(uuid, selections);
        HASH_CACHE.put(normalized, selections);
        FETCHING.remove(uuid);
    }

    public static void put(UUID uuid, CosmeticSelections selections) {
        String fullHash = fullHashFromUuid(uuid);
        put(uuid, fullHash, selections, currentRevision(fullHash));
    }

    public static void putHash(String fullHash, CosmeticSelections selections, long revision) {
        String normalized = normalizeHash(fullHash);
        if (!isCurrentRevision(normalized, revision)) return;
        HASH_CACHE.put(normalized, selections);
        for (UUID uuid : CACHE.keySet()) {
            if (normalized.equals(fullHashFromUuid(uuid))) {
                CACHE.put(uuid, selections);
                FETCHING.remove(uuid);
            }
        }
    }

    public static void putHash(String fullHash, CosmeticSelections selections) {
        putHash(fullHash, selections, currentRevision(fullHash));
    }

    public static long beginHashRefresh(String fullHash) {
        String normalized = normalizeHash(fullHash);
        Long previous = PROFILE_REVISIONS.get(normalized);
        long revision = previous == null ? 1L : previous.longValue() + 1L;
        PROFILE_REVISIONS.put(normalized, Long.valueOf(revision));
        return revision;
    }

    public static long currentRevision(String fullHash) {
        Long revision = PROFILE_REVISIONS.get(normalizeHash(fullHash));
        return revision == null ? 0L : revision.longValue();
    }

    public static void invalidateHash(String fullHash) {
        String normalized = normalizeHash(fullHash);
        HASH_CACHE.remove(normalized);
        for (UUID uuid : CACHE.keySet()) {
            if (normalized.equals(fullHashFromUuid(uuid))) {
                CACHE.remove(uuid);
            }
        }
        for (UUID uuid : FETCHING) {
            if (normalized.equals(fullHashFromUuid(uuid))) {
                FETCHING.remove(uuid);
            }
        }
    }

    public static boolean markFetching(UUID uuid) {
        return FETCHING.add(uuid);
    }

    public static void cancelFetching(UUID uuid) {
        FETCHING.remove(uuid);
    }

    public static void clearAll() {
        CACHE.clear();
        HASH_CACHE.clear();
        PROFILE_REVISIONS.clear();
        FETCHING.clear();
    }

    private static boolean isCurrentRevision(String normalizedHash, long revision) {
        Long current = PROFILE_REVISIONS.get(normalizedHash);
        return (current == null ? 0L : current.longValue()) == revision;
    }

    private static String fullHashFromUuid(UUID uuid) {
        return uuid == null ? "" : Hashing.sha256()
                .hashString(uuid.toString(), StandardCharsets.UTF_8)
                .toString()
                .toUpperCase(Locale.ROOT);
    }

    private static String normalizeHash(String fullHash) {
        return fullHash == null ? "" : fullHash.toUpperCase(Locale.ROOT);
    }
}
