package net.creeperhost.minetogethercommunity.cosmetic.renderstate;

import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import org.jetbrains.annotations.Nullable;

public interface MineTogetherCosmeticRenderState {
    String minetogether$hatId();

    String minetogether$capeId();

    String minetogether$tailId();

    String minetogether$wingId();

    boolean minetogether$suppressVanillaCape();

    boolean minetogether$fullBright();

    @Nullable
    EmotePlayer.Pose minetogether$emotePose();

    void minetogether$setCosmetics(String hatId, String capeId, String tailId, String wingId, boolean suppressVanillaCape, boolean fullBright);

    void minetogether$setEmotePose(@Nullable EmotePlayer.Pose pose);
}
