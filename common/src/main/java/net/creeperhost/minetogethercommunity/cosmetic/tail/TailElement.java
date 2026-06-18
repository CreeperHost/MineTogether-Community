package net.creeperhost.minetogethercommunity.cosmetic.tail;

import org.jetbrains.annotations.Nullable;

/**
 * One box element from a block-model JSON, with per-face UV data.
 * Coordinates are in block-model pixel units (same as Minecraft model-part units).
 */
public record TailElement(
        String name,
        float[] from,    // [x, y, z] — min corner
        float[] to,      // [x, y, z] — max corner
        @Nullable TailFace north,
        @Nullable TailFace south,
        @Nullable TailFace east,
        @Nullable TailFace west,
        @Nullable TailFace up,
        @Nullable TailFace down,
        @Nullable TailRotation rotation
) {}
