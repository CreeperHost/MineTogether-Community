package net.creeperhost.minetogethercommunity.mixin.cosmetic;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.model.PlayerModel")
public interface PlayerModelAccess {

    @Accessor("cloak")
    ModelPart minetogether$getCloak();
}
