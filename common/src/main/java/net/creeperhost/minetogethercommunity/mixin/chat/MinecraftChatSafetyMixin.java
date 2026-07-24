package net.creeperhost.minetogethercommunity.mixin.chat;

import net.creeperhost.minetogethercommunity.util.SignatureVerifier;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftChatSafetyMixin {

    @Inject(method = "getChatStatus", at = @At("RETURN"), cancellable = true)
    private void minetogether$bypassOnlineSafety(CallbackInfoReturnable<Minecraft.ChatStatus> cir) {
        if (!SignatureVerifier.isDebugSignatureSet()) {
            return;
        }

        Minecraft.ChatStatus status = cir.getReturnValue();
        if (status == Minecraft.ChatStatus.DISABLED_BY_PROFILE
                || status == Minecraft.ChatStatus.DISABLED_BY_LAUNCHER) {
            cir.setReturnValue(Minecraft.ChatStatus.ENABLED);
        }
    }
}
