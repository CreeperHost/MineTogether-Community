package net.creeperhost.minetogethercommunity.mixin.connect;

import net.creeperhost.minetogethercommunity.connect.ConnectHandler;
import net.creeperhost.minetogethercommunity.connect.gui.ConnectPackWarningScreen;
import net.creeperhost.minetogethercommunity.connect.gui.FriendConnectScreen;
import net.creeperhost.minetogethercommunity.connect.gui.FriendServerEntry;
import net.creeperhost.minetogethercommunity.connect.gui.ServerListAppender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin (JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin {

    @Shadow
    protected ServerSelectionList serverSelectionList;

    @Shadow
    protected abstract void join(ServerData serverData);

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

    @Inject (at = @At ("HEAD"), method = "joinSelectedServer", cancellable = true)
    public void joinSelectedServer(CallbackInfo ci) {
        ServerSelectionList.Entry entry = this.serverSelectionList.getSelected();
        if (entry instanceof FriendServerEntry friendServer) {
            if (friendServer.remoteServer.shouldWarnBeforeJoin()) {
                showPackWarning(friendServer);
            } else {
                startConnecting(friendServer);
            }
            ci.cancel();
        }
    }

    private void showPackWarning(FriendServerEntry friendServer) {
        Minecraft.getInstance().setScreen(new ConnectPackWarningScreen.Screen(getThis(), friendServer.remoteServer, friendServer.getServerData()));
    }

    private void startConnecting(FriendServerEntry friendServer) {
        FriendConnectScreen.startConnecting(getThis(), Minecraft.getInstance(), friendServer.remoteServer, friendServer.getServerData());
    }

    @Inject (at = @At ("TAIL"), method = "tick()V")
    public void tick(CallbackInfo ci) {
        ServerListAppender.INSTANCE.tick();
    }
}
