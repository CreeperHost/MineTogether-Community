package net.creeperhost.minetogethercommunity.cosmetic.tail;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record Tail(
        String id,
        String displayName,
        String author,
        String mod,
        boolean locked,
        @Nullable String howToUnlock,
        ResourceLocation texture,
        int texWidth,
        int texHeight,
        List<TailElement> elements,
        TailModel model,
        TailAnimation animation
) {}
