package net.creeperhost.minetogethercommunity.fabric;

import dev.architectury.platform.Platform;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.connect.DedicatedServerConnect;
import net.creeperhost.minetogethercommunity.gui.MTTextures;
import net.creeperhost.polylib.fabric.client.ResourceReloadListenerWrapper;
import net.creeperhost.minetogethercommunity.cosmetic.hat.HatLayer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.EntityType;

/**
 * Created by covers1624 on 20/6/22.
 */
public class MineTogetherFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        MineTogether.init();

        if (Platform.getEnv() == EnvType.CLIENT) {
            clientInit();
            ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new ResourceReloadListenerWrapper(MTTextures::getAtlasHolder, ResourceLocation.fromNamespaceAndPath(MineTogether.MOD_ID, "gui_atlas_reload")));
        } else {
            serverInit();
        }
    }

    private void clientInit() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> MineTogetherChat.onScreenPostInit(screen));
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, registrationHelper, context) -> {
            if (entityType == EntityType.PLAYER) {
                //noinspection unchecked
                registrationHelper.register(new HatLayer<>((LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) renderer));
            }
        });
    }

    private void serverInit() {
        ServerLifecycleEvents.SERVER_STARTED.register(DedicatedServerConnect::serverStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(DedicatedServerConnect::serverStopping);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> DedicatedServerConnect.playerJoined(handler.player));
    }
}
