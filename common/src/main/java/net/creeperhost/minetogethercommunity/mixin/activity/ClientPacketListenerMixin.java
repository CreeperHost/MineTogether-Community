package net.creeperhost.minetogethercommunity.mixin.activity;

import net.creeperhost.minetogethercommunity.activity.ActivityTelemetry;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleUpdateAdvancementsPacket", at = @At("TAIL"))
    private void mtActivity$handleUpdateAdvancementsPacket(ClientboundUpdateAdvancementsPacket packet, CallbackInfo ci) {
        ActivityTelemetry.handleAdvancementPacket(packet);
    }
}
