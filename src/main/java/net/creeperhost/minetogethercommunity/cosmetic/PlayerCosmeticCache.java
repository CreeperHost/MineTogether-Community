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
    private static final Set<UUID> FETCHING = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    private PlayerCosmeticCache() {
    }

    public static CosmeticSelections get(UUID uuid) {
        CosmeticSelections selections = CACHE.get(uuid);
        return selections != null ? selections : HASH_CACHE.get(fullHashFromUuid(uuid));
    }

    public static void put(UUID uuid, CosmeticSelections selections) {
        CACHE.put(uuid, selections);
        HASH_CACHE.put(fullHashFromUuid(uuid), selections);
        FETCHING.remove(uuid);
    }

    public static void putHash(String fullHash, CosmeticSelections selections) {
        String normalized = normalizeHash(fullHash);
        HASH_CACHE.put(normalized, selections);
        for (UUID uuid : CACHE.keySet()) {
            if (normalized.equals(fullHashFromUuid(uuid))) {
                CACHE.put(uuid, selections);
                FETCHING.remove(uuid);
            }
        }
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
        FETCHING.clear();
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
