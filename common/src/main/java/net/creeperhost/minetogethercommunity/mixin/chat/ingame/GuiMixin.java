package net.creeperhost.minetogethercommunity.mixin.chat.ingame;

import net.covers1624.quack.util.SneakyUtils;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Created by covers1624 on 27/7/22.
 */
@Mixin (Gui.class)
abstract class GuiMixin {

    @Final
    @Shadow
    public ChatComponent chat;

    @Inject (
            method = "<init>",
            at = @At (
                    value = "TAIL"
            )
    )
    private void onInit(Minecraft minecraft, ItemRenderer itemRenderer, CallbackInfo ci) {
        MineTogetherChat.initChat(SneakyUtils.unsafeCast(this));
    }

    @Inject (
            method = "getChat",
            at = @At("RETURN"),
            cancellable = true
    )
    private void onGetChat(CallbackInfoReturnable<ChatComponent> cir) {
        cir.setReturnValue(selectedChat());
    }
    @Redirect (
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V",
            at = @At (
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;render(Lnet/minecraft/client/gui/GuiGraphics;III)V"
            )
    )
    private void onRenderChat(ChatComponent instance, GuiGraphics graphics, int tickCount, int mouseX, int mouseY) {
        selectedChat().render(graphics, tickCount, mouseX, mouseY);
    }

    private ChatComponent selectedChat() {
        return switch (MineTogetherChat.getTarget()) {
            case VANILLA -> chat;
            case PUBLIC -> MineTogetherChat.publicChat;
            case GROUP -> MineTogetherChat.groupChat;
        };
    }

    @Redirect (
            method = "onDisconnected",
            at = @At (
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/Gui;chat:Lnet/minecraft/client/gui/components/ChatComponent;",
                    opcode = Opcodes.GETFIELD
            )
    )
    private ChatComponent onDisconnect(Gui instance) {
        return selectedChat();
    }

    @Inject(
            method = "tick()V",
            at = @At("TAIL")
    )
    private void onTick(CallbackInfo ci) {
        MineTogetherChat.publicChat.tick();
        MineTogetherChat.groupChat.tick();
    }
}
