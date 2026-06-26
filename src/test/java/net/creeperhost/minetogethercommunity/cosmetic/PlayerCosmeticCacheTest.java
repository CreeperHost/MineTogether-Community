package net.creeperhost.minetogethercommunity.cosmetic;

import com.google.common.hash.Hashing;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class PlayerCosmeticCacheTest {

    private static final UUID PLAYER_UUID = UUID.fromString("1b671a64-40d5-491e-99b0-da01ff1f3341");
    private static final String PLAYER_HASH = Hashing.sha256()
            .hashString(PLAYER_UUID.toString(), StandardCharsets.UTF_8)
            .toString()
            .toUpperCase(Locale.ROOT);

    @Before
    public void setUp() {
        PlayerCosmeticCache.clearAll();
    }

    @After
    public void tearDown() {
        PlayerCosmeticCache.clearAll();
    }

    @Test
    public void invalidatesUuidAndHashEntriesForFullHash() {
        CosmeticSelections cached = new CosmeticSelections();
        cached.selectedHatId = "old_hat";
        PlayerCosmeticCache.put(PLAYER_UUID, cached);

        assertSame(cached, PlayerCosmeticCache.get(PLAYER_UUID));

        PlayerCosmeticCache.invalidateHash(PLAYER_HASH.toLowerCase(Locale.ROOT));

        assertNull(PlayerCosmeticCache.get(PLAYER_UUID));

        CosmeticSelections refreshed = new CosmeticSelections();
        refreshed.selectedHatId = "new_hat";
        PlayerCosmeticCache.putHash(PLAYER_HASH, refreshed);

        assertSame(refreshed, PlayerCosmeticCache.get(PLAYER_UUID));
    }

    @Test
    public void invalidationReleasesFetchingGuardForMatchingUuid() {
        assertTrue(PlayerCosmeticCache.markFetching(PLAYER_UUID));
        assertFalse(PlayerCosmeticCache.markFetching(PLAYER_UUID));

        PlayerCosmeticCache.invalidateHash(PLAYER_HASH);

        assertTrue(PlayerCosmeticCache.markFetching(PLAYER_UUID));
    }
}
