package net.creeperhost.minetogethercommunity.mixin.chat.ingame;

import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.ChatComponent;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Created by covers1624 on 27/7/22.
 */
@Mixin(Hud.class)
abstract class HudMixin {

    @Final
    @Shadow
    private ChatComponent chat;

    @Inject(
            method = "<init>",
            at = @At(
                    value = "TAIL"
            )
    )
    private void onInit(Minecraft minecraft, CallbackInfo ci) {
        MineTogetherChat.initChat(chat);
    }

    @Redirect(
            method = "getChat",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/Hud;chat:Lnet/minecraft/client/gui/components/ChatComponent;",
                    opcode = Opcodes.GETFIELD
            )
    )
    private ChatComponent onGetChat(Hud instance) {
        return minetogethercommunity$getSelectedChat();
    }

    @Redirect(
            method = "extractChat",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/Hud;chat:Lnet/minecraft/client/gui/components/ChatComponent;",
                    opcode = Opcodes.GETFIELD
            )
    )
    private ChatComponent onExtractChat(Hud instance) {
        return minetogethercommunity$getSelectedChat();
    }

    @Redirect(
            method = "onDisconnected",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/Hud;chat:Lnet/minecraft/client/gui/components/ChatComponent;",
                    opcode = Opcodes.GETFIELD
            )
    )
    private ChatComponent onDisconnect(Hud instance) {
        return minetogethercommunity$getSelectedChat();
    }

    @Inject(
            method = "tick()V",
            at = @At("TAIL")
    )
    private void onTick(CallbackInfo ci) {
        MineTogetherChat.publicChat.tick();
    }

    @Unique
    private ChatComponent minetogethercommunity$getSelectedChat() {
        return switch (MineTogetherChat.getTarget()) {
            case VANILLA -> chat;
            case PUBLIC -> MineTogetherChat.publicChat;
            case GROUP -> MineTogetherChat.groupChat;
        };
    }
}
