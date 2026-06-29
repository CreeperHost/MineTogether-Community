package net.creeperhost.minetogethercommunity.compat.companion;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

/**
 * Created by brandon3055 on 14/07/2024
 */
public class FTBPackCompanionCompat {

    private static final Logger LOGGER = LogManager.getLogger();

    public static void init() {
        try {
            PauseApi api = verifyPauseApi();
            Object apiInstance = api.getMethod.invoke(null);
            Object topRight = enumConstant(api.targetClass, "TOP_RIGHT");
            api.registerMethod.invoke(apiInstance, topRight, new PauseProvider());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOGGER.warn("FTB Pack Companion pause API is not compatible; skipping MineTogether pause menu buttons.", e);
        }
    }

    private static PauseApi verifyPauseApi() throws ReflectiveOperationException {
        Class<?> apiClass = Class.forName("dev.ftb.packcompanion.api.client.PackCompanionClientAPI");
        Class<?> targetClass = Class.forName("dev.ftb.packcompanion.api.client.pause.AdditionalPauseTarget");
        Class<?> providerClass = Class.forName("dev.ftb.packcompanion.api.client.pause.AdditionalPauseProvider");
        Class<?> screenHolderClass = Class.forName("dev.ftb.packcompanion.api.client.pause.ScreenHolder");
        Class<?> widgetCollectionClass = Class.forName("dev.ftb.packcompanion.api.client.pause.ScreenWidgetCollection");
        Class<?> guiEventListenerClass = Class.forName("net.minecraft.client.gui.components.events.GuiEventListener");

        Method getMethod = apiClass.getMethod("get");
        Method registerMethod = apiClass.getMethod("registerAdditionalPauseProvider", targetClass, providerClass);

        enumConstant(targetClass, "TOP_RIGHT");
        providerClass.getMethod("init", targetClass, screenHolderClass, int.class, int.class);
        screenHolderClass.getMethod("unsafeScreenAccess");
        widgetCollectionClass.getMethod("create");
        widgetCollectionClass.getMethod("addRenderableWidget", guiEventListenerClass);

        return new PauseApi(targetClass, getMethod, registerMethod);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(Class<?> enumClass, String name) {
        return Enum.valueOf((Class) enumClass.asSubclass(Enum.class), name);
    }

    private static class PauseApi {
        final Class<?> targetClass;
        final Method getMethod;
        final Method registerMethod;

        PauseApi(Class<?> targetClass, Method getMethod, Method registerMethod) {
            this.targetClass = targetClass;
            this.getMethod = getMethod;
            this.registerMethod = registerMethod;
        }
    }
}
