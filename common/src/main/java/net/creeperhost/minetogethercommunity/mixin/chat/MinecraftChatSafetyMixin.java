package net.creeperhost.minetogethercommunity.mixin.chat;

import net.creeperhost.minetogethercommunity.util.SignatureVerifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.ChatAbilities;
import net.minecraft.client.multiplayer.chat.ChatRestriction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftChatSafetyMixin {

    @Inject(method = "computeChatAbilities", at = @At("RETURN"), cancellable = true)
    private void minetogether$bypassOnlineSafety(CallbackInfoReturnable<ChatAbilities> cir) {
        if (!SignatureVerifier.isDebugSignatureSet()) {
            return;
        }

        ChatAbilities.Builder abilities = new ChatAbilities.Builder();
        cir.getReturnValue().restrictions()
                .filter(restriction -> restriction != ChatRestriction.DISABLED_BY_PROFILE
                        && restriction != ChatRestriction.DISABLED_BY_LAUNCHER)
                .forEach(abilities::addRestriction);
        cir.setReturnValue(abilities.build());
    }
}
