package net.creeperhost.minetogethercommunity.mixin.compat;

import net.creeperhost.minetogethercommunity.compat.ftbquests.FTBQuestsCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Date;
import java.util.UUID;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.client.FTBQuestsNetClient", remap = false)
public class FTBQuestsNetClientMixin {

    @Inject(method = "objectCompleted", at = @At("TAIL"), require = 0)
    private static void mt$onObjectCompleted(UUID teamId, long id, Date completedAt, CallbackInfo ci) {
        FTBQuestsCompat.onObjectCompleted(id);
    }
}
