package net.creeperhost.minetogethercommunity.mixin.cosmetic;

import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.renderer.entity.LivingEntityRenderer")
public interface LivingEntityRendererAccess {
    @Invoker("addLayer")
    boolean minetogether$addLayer(RenderLayer<?, ?> layer);
}
