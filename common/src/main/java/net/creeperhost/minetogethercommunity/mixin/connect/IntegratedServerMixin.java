package net.creeperhost.minetogethercommunity.mixin.connect;

import net.creeperhost.minetogethercommunity.connect.ConnectHandler;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {

    @Inject(method = "getMaxPlayers", at = @At("HEAD"), cancellable = true)
    private void minetogether$getMaxPlayers(CallbackInfoReturnable<Integer> cir) {
        int maxPlayers = ConnectHandler.getPublishedMaxPlayers();
        if (maxPlayers > 0) {
            cir.setReturnValue(maxPlayers);
        }
    }
}
