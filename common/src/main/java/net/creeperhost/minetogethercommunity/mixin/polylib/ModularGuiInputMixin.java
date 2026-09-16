package net.creeperhost.minetogethercommunity.mixin.polylib;

import com.mojang.blaze3d.platform.InputConstants;
import net.creeperhost.polylib.client.modulargui.ModularGui;
import net.creeperhost.polylib.client.modulargui.elements.GuiButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Bridges SDL input to PolyLib 2.0.11's legacy element button IDs. */
@Mixin(value = ModularGui.class, remap = false)
public class ModularGuiInputMixin {
    @Unique
    private static final boolean minetogether$legacyButtons = minetogether$usesLegacyButtons();

    @Unique
    private static boolean minetogether$usesLegacyButtons() {
        try {
            // Read at runtime: javac would inline the dependency's constant.
            return GuiButton.class.getField("LEFT_CLICK").getInt(null) == 0;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to determine PolyLib mouse button IDs", e);
        }
    }

    @ModifyArg(method = "mouseClicked", at = @At(value = "INVOKE",
            target = "Lnet/creeperhost/polylib/client/modulargui/elements/GuiElement;mouseClicked(DDIZ)Z"), index = 2)
    private int minetogether$clickButton(int button) {
        return minetogether$elementButton(button);
    }

    @ModifyArg(method = "mouseReleased", at = @At(value = "INVOKE",
            target = "Lnet/creeperhost/polylib/client/modulargui/elements/GuiElement;mouseReleased(DDIZ)Z"), index = 2)
    private int minetogether$releaseButton(int button) {
        return minetogether$elementButton(button);
    }

    @Unique
    private static int minetogether$elementButton(int button) {
        if (!minetogether$legacyButtons) return button;
        return switch (button) {
            case InputConstants.MOUSE_BUTTON_LEFT -> 0;
            case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
            case InputConstants.MOUSE_BUTTON_MIDDLE -> 2;
            default -> button - 1;
        };
    }
}
