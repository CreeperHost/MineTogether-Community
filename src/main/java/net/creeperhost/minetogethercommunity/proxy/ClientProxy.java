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
import net.creeperhost.minetogethercommunity.connect.ConnectHandler;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticApiClient;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticDownloader;
import net.creeperhost.minetogethercommunity.cosmetic.CosmeticSelections;
import net.creeperhost.minetogethercommunity.cosmetic.PlayerCosmeticCache;
import net.creeperhost.minetogethercommunity.cosmetic.render.LegacyCosmeticRenderer;
import net.creeperhost.minetogethercommunity.util.MTSessionProvider;
import net.creeperhost.minetogethercommunity.util.DiagnosticLog;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraftforge.client.ClientCommandHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

public class ClientProxy extends CommonProxy {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether Client");

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        Keybindings.init();
        registerClientCommand(new MineTogetherSettingsCommand());
        registerClientCommand(new OpenFriendChatCommand());
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
        ClientEvents clientEvents = new ClientEvents();
        MinecraftForge.EVENT_BUS.register(clientEvents);
        FMLCommonHandler.instance().bus().register(clientEvents);
        DiagnosticLog.info(LOGGER, "[MT-1710-DIAG] registered MineTogether client events on Forge and FML buses");
        MinecraftForge.EVENT_BUS.register(new LegacyCosmeticRenderer());
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

    private static void registerClientCommand(net.minecraft.command.ICommand command) {
        try {
            for (Method method : ClientCommandHandler.class.getMethods()) {
                if (!isRegisterCommandMethod(method) || method.getParameterTypes().length != 1) {
                    continue;
                }
                if (method.getParameterTypes()[0].isAssignableFrom(command.getClass())) {
                    method.invoke(ClientCommandHandler.instance, command);
                    return;
                }
            }
            throw new NoSuchMethodException("ClientCommandHandler.registerCommand(ICommand)");
        } catch (Exception ex) {
            throw new RuntimeException("Unable to register MineTogether client command " + commandName(command), ex);
        }
    }

    private static boolean isRegisterCommandMethod(Method method) {
        String name = method.getName();
        return "registerCommand".equals(name) || "func_71560_a".equals(name) || "a".equals(name);
    }

    private static String commandName(net.minecraft.command.ICommand command) {
        for (String methodName : new String[]{"getCommandName", "func_71517_b"}) {
            try {
                Method method = command.getClass().getMethod(methodName);
                Object value = method.invoke(command);
                if (value != null) return String.valueOf(value);
            } catch (Exception ignored) {
            }
        }
        return command.getClass().getName();
    }
}
