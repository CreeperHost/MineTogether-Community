package net.creeperhost.minetogethercommunity.mixin.connect;

import net.creeperhost.minetogethercommunity.connect.ConnectHandler;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {

    @Inject(method = "getForcedGameType", at = @At("HEAD"), cancellable = true)
    private void minetogether$getForcedGameType(CallbackInfoReturnable<GameType> cir) {
        GameType gameType = ConnectHandler.getPublishedGameType();
        IntegratedServer server = (IntegratedServer) (Object) this;
        if (gameType != null && server.isPublished() && !server.isHardcore()) {
            cir.setReturnValue(gameType);
        }
    }

    @Inject(method = "getCustomPermissionLevel", at = @At("HEAD"), cancellable = true)
    private void minetogether$getCustomPermissionLevel(CallbackInfoReturnable<LevelBasedPermissionSet> cir) {
        Boolean commands = ConnectHandler.getPublishedCommands();
        if (commands != null) {
            cir.setReturnValue(commands ? LevelBasedPermissionSet.GAMEMASTER : LevelBasedPermissionSet.ALL);
        }
    }

    @Inject(method = "getMaxPlayers", at = @At("HEAD"), cancellable = true)
    private void minetogether$getMaxPlayers(CallbackInfoReturnable<Integer> cir) {
        int maxPlayers = ConnectHandler.getPublishedMaxPlayers();
        if (maxPlayers > 0) {
            cir.setReturnValue(maxPlayers);
        }
    }
}
