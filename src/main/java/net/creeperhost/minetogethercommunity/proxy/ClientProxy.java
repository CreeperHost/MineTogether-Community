package net.creeperhost.minetogethercommunity.proxy;

import net.creeperhost.minetogether.session.MineTogetherSession;
import net.creeperhost.minetogethercommunity.MineTogether;
import net.creeperhost.minetogethercommunity.activity.ActivityTelemetry;
import net.creeperhost.minetogethercommunity.chat.InGameChatBridge;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.creeperhost.minetogethercommunity.client.ClientEvents;
import net.creeperhost.minetogethercommunity.client.Keybindings;
import net.creeperhost.minetogethercommunity.client.MineTogetherSettingsCommand;
import net.creeperhost.minetogethercommunity.client.OpenFriendChatCommand;
import net.creeperhost.minetogethercommunity.compat.Integration;
import net.creeperhost.minetogethercommunity.compat.ftbquests.FTBQuestsCompat;
import net.creeperhost.minetogethercommunity.compat.legacyquests.BetterQuestingCompat;
import net.creeperhost.minetogethercommunity.compat.legacyquests.BountifulCompat;
import net.creeperhost.minetogethercommunity.compat.legacyquests.HQMCompat;
import net.creeperhost.minetogethercommunity.connect.ConnectHandler;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticApiClient;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.creeperhost.minetogethercommunity.cosmetic.PlayerCosmeticCache;
import net.creeperhost.minetogethercommunity.cosmetic.render.CosmeticLayer;
import net.creeperhost.minetogethercommunity.cosmetic.render.MineTogetherCapeLayer;
import net.creeperhost.minetogethercommunity.cosmetic.render.MineTogetherElytraLayer;
import net.creeperhost.minetogethercommunity.util.MTSessionProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerCape;
import net.minecraft.client.renderer.entity.layers.LayerElytra;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;

public class ClientProxy extends CommonProxy {

    private static final Field LAYER_RENDERERS = findField(RenderLivingBase.class, "layerRenderers", "field_177097_h", "h");

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        Keybindings.init();
        ClientCommandHandler.instance.registerCommand(new MineTogetherSettingsCommand());
        ClientCommandHandler.instance.registerCommand(new OpenFriendChatCommand());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        MineTogetherSession.getDefault().setProvider(new MTSessionProvider());
        ActivityTelemetry.init();
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                ActivityTelemetry.onWorldExit();
            }
        }, "MineTogether telemetry shutdown"));
        MineTogetherSession.getDefault().onTokenRefreshed(token -> {
            if (token == null) {
                return;
            }
            MineTogether.AUTH.setHeader("Authorization", "Bearer " + token.toString());
            ActivityTelemetry.authChanged(token);
        });
        MineTogetherSession.getDefault().getTokenAsync();

        MineTogetherChat.init();
        ConnectHandler.init();
        MinecraftForge.EVENT_BUS.register(new ClientEvents());
        Integration.runOptional("ftbquests", () -> () -> MinecraftForge.EVENT_BUS.register(new FTBQuestsCompat()));
        Integration.runOptional("betterquesting", () -> BetterQuestingCompat::register);
        Integration.runOptional("bountiful", () -> BountifulCompat::register);
        Integration.runOptional("hardcorequesting", () -> HQMCompat::register);
        Integration.runOptional("hqm", () -> HQMCompat::register);
        registerCosmeticLayers();
    }

    private void registerCosmeticLayers() {
        for (RenderPlayer renderer : Minecraft.getMinecraft().getRenderManager().getSkinMap().values()) {
            removeConflictingLayers(renderer);
            renderer.addLayer(new MineTogetherCapeLayer(renderer));
            renderer.addLayer(new MineTogetherElytraLayer(renderer));
            renderer.addLayer(new CosmeticLayer<AbstractClientPlayer>(renderer));
        }
    }

    @SuppressWarnings("unchecked")
    private void removeConflictingLayers(RenderPlayer renderer) {
        try {
            List<LayerRenderer<AbstractClientPlayer>> layers = (List<LayerRenderer<AbstractClientPlayer>>) LAYER_RENDERERS.get(renderer);
            Iterator<LayerRenderer<AbstractClientPlayer>> iterator = layers.iterator();
            while (iterator.hasNext()) {
                LayerRenderer<?> layer = iterator.next();
                if (layer instanceof LayerCape
                        || layer instanceof LayerElytra
                        || layer instanceof MineTogetherCapeLayer
                        || layer instanceof MineTogetherElytraLayer
                        || layer instanceof CosmeticLayer) {
                    iterator.remove();
                }
            }
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Unable to update player renderer layers", ex);
        }
    }

    public static void onClientWorldJoin() {
        ActivityTelemetry.onWorldEnter();
        CosmeticDownloader.instance().startCatalogFetch();
        CosmeticApiClient.fetchProfileAsync();
    }

    public static void onClientWorldLeave() {
        ConnectHandler.unPublish();
        ConnectHandler.clearAndReset();
        InGameChatBridge.clearHistories();
        ActivityTelemetry.onWorldExit();
        CosmeticSelections.instance().clear();
        PlayerCosmeticCache.clearAll();
    }

    private static Field findField(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new IllegalStateException("Could not find field on " + owner.getName());
    }
}
