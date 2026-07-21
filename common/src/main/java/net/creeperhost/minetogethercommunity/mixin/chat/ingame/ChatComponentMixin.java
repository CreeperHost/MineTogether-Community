package net.creeperhost.minetogethercommunity.mixin.chat.ingame;

import net.creeperhost.minetogethercommunity.Constants;
import net.creeperhost.minetogethercommunity.chat.ChatTarget;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author covers1624
 */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {

    @Final
    @Shadow
    private Minecraft minecraft;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD")
    )
    private void onExtractRenderState(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY, ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions, CallbackInfo ci) {
        // Don't render our additional background blackout if chat is not enabled, or chat is not focused.
        if (!displayMode.foreground || !MineTogetherChat.isChatEnabled() || Minecraft.getInstance().options.hideGui || !((ChatComponent) (Object) this).isChatFocused()) return;

        //This does not *perfectly* match vanilla, but its very close, and a lot less dumb.
        //It also just happens to fix the vanilla scroll bar

        ChatComponent chatComponent = (ChatComponent) (Object) this;
        float scale = (float) chatComponent.getScale();
        int width = Mth.ceil((float) chatComponent.getWidth() + (12 * scale));
        int height = Mth.ceil(chatComponent.getHeight() * scale);
        int guiHeight = graphics.guiHeight();
        int maxYPos = guiHeight - 40;

        // Render new 'filled' background under all chat lines.
        graphics.fill(0, maxYPos - height, width, maxYPos, minecraft.options.getBackgroundColor(0x80000000));

        // If we are on a MineTogether tab, draw our logo.
        if (MineTogetherChat.getTarget() != ChatTarget.VANILLA){
            int logoSize = (int) (Math.min(width, height) * 0.9D);
            drawLogo(graphics, minecraft.font, -4 + (width / 2) - (logoSize / 2), maxYPos - (height / 2) - (logoSize / 2), logoSize, logoSize);
        }
    }

    @Inject(
            method = "rescaleChat",
            at = @At("HEAD")
    )
    private void onRescaleChat(CallbackInfo ci) {
        if (MineTogetherChat.getTarget() == ChatTarget.VANILLA) {
            MineTogetherChat.publicChat.rescaleChat();
        }
    }

    /**
     * Draws the Mine Together logo with the "Created By {CreeperHost Logo}" bellow it.
     * The entire thing will be scaled appropriately to fit within the specified bounds.
     */
    private static void drawLogo(GuiGraphicsExtractor g, Font font, int x, int y, int width, int height) {
        String created = "Created by";
        int strWidth = font.width(created);
        int creeperHeight = 19; //Value chosen so that Creeper Host logo text is roughly the same size as the "Created By" text
        int creeperWidth = (int) (creeperHeight * (960D / 266D)); //Computes width based on the image's aspect ratio.
        int createdWidth = strWidth + 2 + creeperWidth;
        float createdScale = width / (float) createdWidth;
        int creeperOffset = (int) ((font.lineHeight / 2D) - (creeperHeight / 2D));
        int creeperSHeight = (int) (creeperHeight * createdScale);

        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) (y + height - (creeperHeight * createdScale) - creeperOffset));
        g.pose().scale(createdScale, createdScale);

        g.blit(RenderPipelines.GUI_TEXTURED, Constants.CREEPERHOST_LOGO_25, createdWidth - creeperWidth, creeperOffset, 0.0F, 0.0F, creeperWidth, creeperHeight, creeperWidth, creeperHeight, 0x40FFFFFF);
        g.text(font, created, 0, 0, 0x40FFFFFF, true);

        g.pose().popMatrix();

        int mtHeight = height - creeperSHeight - 4;
        int mtWidth = (int) (mtHeight * (348D / 318D));

        g.blit(RenderPipelines.GUI_TEXTURED, Constants.MINETOGETHER_LOGO_25, x + (int) ((width / 2D) - (mtWidth / 2D)), y, 0.0F, 0.0F, mtWidth, mtHeight, mtWidth, mtHeight, 0x40FFFFFF);
    }
}
