package net.creeperhost.minetogethercommunity.mixin.connect;

import net.creeperhost.minetogethercommunity.connect.ConnectHandler;
import net.creeperhost.minetogethercommunity.connect.gui.ServerListAppender;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin (JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin {

    @Shadow
    protected ServerSelectionList serverSelectionList;

    private JoinMultiplayerScreen getThis() {
        return (JoinMultiplayerScreen) (Object) this;
    }

    @Inject (at = @At ("TAIL"), method = "init()V")
    public void init(CallbackInfo ci) {
        ServerListAppender.INSTANCE.init(serverSelectionList, getThis());
    }

    @Inject (at = @At ("TAIL"), method = "removed()V") // closed
    public void removed(CallbackInfo ci) {
        ServerListAppender.INSTANCE.remove();
    }

    @Inject (at = @At ("TAIL"), method = "tick()V")
    public void tick(CallbackInfo ci) {
        ServerListAppender.INSTANCE.tick();
    }
}
