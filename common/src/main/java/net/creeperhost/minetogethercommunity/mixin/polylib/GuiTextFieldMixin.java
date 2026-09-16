package net.creeperhost.minetogethercommunity.mixin.polylib;

import net.creeperhost.polylib.client.modulargui.elements.GuiTextField;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PolyLib's custom text fields must opt into SDL character input. */
@Mixin(value = GuiTextField.class, remap = false)
public class GuiTextFieldMixin {
    @Inject(method = "setFocus", at = @At("RETURN"))
    private void minetogether$updateTextInput(boolean focused, CallbackInfo ci) {
        Minecraft.getInstance().textInputManager().onTextInputFocusChange(this, focused);
    }
}
