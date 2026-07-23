package net.creeperhost.minetogethercommunity.mixin.cosmetic;

import net.creeperhost.minetogethercommunity.cosmetic.emote.EmotePlayer;
import net.creeperhost.minetogethercommunity.cosmetic.renderstate.MineTogetherCosmeticRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements MineTogetherCosmeticRenderState {
    private String minetogether$hatId = "";
    private String minetogether$capeId = "";
    private String minetogether$tailId = "";
    private String minetogether$wingId = "";
    private boolean minetogether$suppressVanillaCape;
    private boolean minetogether$fullBright;
    @Nullable
    private EmotePlayer.Pose minetogether$emotePose;

    @Override
    public String minetogether$hatId() {
        return minetogether$hatId;
    }

    @Override
    public String minetogether$capeId() {
        return minetogether$capeId;
    }

    @Override
    public String minetogether$tailId() {
        return minetogether$tailId;
    }

    @Override
    public String minetogether$wingId() {
        return minetogether$wingId;
    }

    @Override
    public boolean minetogether$suppressVanillaCape() {
        return minetogether$suppressVanillaCape;
    }

    @Override
    public boolean minetogether$fullBright() {
        return minetogether$fullBright;
    }

    @Override
    public @Nullable EmotePlayer.Pose minetogether$emotePose() {
        return minetogether$emotePose;
    }

    @Override
    public void minetogether$setCosmetics(String hatId, String capeId, String tailId, String wingId, boolean suppressVanillaCape, boolean fullBright) {
        this.minetogether$hatId = emptyIfNull(hatId);
        this.minetogether$capeId = emptyIfNull(capeId);
        this.minetogether$tailId = emptyIfNull(tailId);
        this.minetogether$wingId = emptyIfNull(wingId);
        this.minetogether$suppressVanillaCape = suppressVanillaCape;
        this.minetogether$fullBright = fullBright;
    }

    @Override
    public void minetogether$setEmotePose(@Nullable EmotePlayer.Pose pose) {
        this.minetogether$emotePose = pose;
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
