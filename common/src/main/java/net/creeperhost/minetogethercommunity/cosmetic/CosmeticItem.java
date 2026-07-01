package net.creeperhost.minetogethercommunity.cosmetic;

import org.jetbrains.annotations.Nullable;

/**
 * Lightweight metadata record for a cosmetic item, populated from the catalog API.
 * <p>
 * This is the "catalog-level" object - it contains only the information needed to display
 * the item in the cosmetics UI list. The heavy rendered assets (3-D geometry, textures) are
 * downloaded separately on demand via
 * {@link CosmeticDownloader#ensureAssetLoaded(String, String)}.
 */
public record CosmeticItem(
        String id,
        String displayName,
        String author,
        String mod,
        boolean locked,
        @Nullable String howToUnlock
) {}
