package net.creeperhost.minetogethercommunity.tests.fabric;

import net.creeperhost.minetogethercommunity.tests.TitleScreenScreenshot;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CI-only equivalent of NeoForge's RenderFrameEvent.Post, including title/loading screens. */
@Mixin(Minecraft.class)
public abstract class TitleScreenFrameMixin {
    @Unique
    private TitleScreenScreenshot minetogetherTests$screenshot;

    @Inject(method = "renderFrame", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;render()V", shift = At.Shift.AFTER))
    private void minetogetherTests$afterFrame(boolean advanceGameTime, CallbackInfo ci) {
        if (minetogetherTests$screenshot == null) minetogetherTests$screenshot = new TitleScreenScreenshot();
        minetogetherTests$screenshot.afterFrame();
    }
}
