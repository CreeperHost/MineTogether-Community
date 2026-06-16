package net.creeperhost.minetogethercommunity.cosmetic;

import org.jetbrains.annotations.Nullable;

/**
 * In-memory record of the player's active cosmetic selections.
 * Populated from the server via {@link CosmeticApiClient#fetchProfileAsync()};
 * updated locally on each selection and synced to the server via
 * {@link CosmeticApiClient#selectAsync(String, String)}.
 */
public class CosmeticSelections {

    private static @Nullable CosmeticSelections INSTANCE;

    // ── Selected cosmetic IDs — empty/null means "none" ───────────────────────
    public volatile String selectedHatId = "";
    public volatile String selectedCapeId = "";

    // ── Singleton access ───────────────────────────────────────────────────────

    public static CosmeticSelections instance() {
        if (INSTANCE == null) {
            synchronized (CosmeticSelections.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CosmeticSelections();
                }
            }
        }
        return INSTANCE;
    }
}
