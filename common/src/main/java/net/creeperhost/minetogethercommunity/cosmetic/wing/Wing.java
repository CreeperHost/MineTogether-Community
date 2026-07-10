package net.creeperhost.minetogethercommunity.cosmetic.wing;

import net.creeperhost.minetogethercommunity.cosmetic.tail.TailElement;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailModel;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record Wing(
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
        WingAnimation animation,
        WingPlacement placement
) {}
