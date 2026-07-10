package net.creeperhost.minetogethercommunity.cosmetic.hat;

import net.creeperhost.minetogethercommunity.cosmetic.ModelPlacement;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailElement;
import net.creeperhost.minetogethercommunity.cosmetic.tail.TailModel;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record Hat(
        String id,
        String displayName,
        String author,
        String mod,
        boolean locked,
        String howToUnlock,
        Identifier texture,
        int texWidth,
        int texHeight,
        HatModelType type,
        List<HatCuboid> cuboids,
        List<TailElement> jsonElements,
        @Nullable TailModel jsonModel,
        HatAnimation animation,
        ModelPlacement placement
) {
    public boolean isJsonModel() {
        return type == HatModelType.JSON;
    }
}
