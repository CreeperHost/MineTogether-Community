package net.creeperhost.minetogethercommunity.mixin.chat;

import net.creeperhost.minetogethercommunity.util.MessageFormatter;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Created by covers1624 on 2/11/22.
 */
@Mixin (ChatScreen.class)
public abstract class ScreenMixin {

    @Inject (
            method = "handleComponentClicked(Lnet/minecraft/network/chat/Style;Z)Z",
            at = @At ("HEAD"),
            cancellable = true
    )
    private void onComponentClicked(Style style, boolean allowInsertions, CallbackInfoReturnable<Boolean> cir) {
        if (style != null) {
            ClickEvent event = style.getClickEvent();
            // Don't let CLICK_NAME escape into the wild.
            if (MessageFormatter.isClickName(event)) {
                cir.setReturnValue(false);
            }
        }
    }
}
