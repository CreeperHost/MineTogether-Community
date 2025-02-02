package net.creeperhost.minetogethercommunity;

import dev.architectury.injectables.targets.ArchitecturyTarget;
import dev.architectury.platform.Platform;
import net.covers1624.quack.net.httpapi.HttpEngine;
import net.covers1624.quack.net.httpapi.java11.Java11HttpEngine;
import net.creeperhost.minetogether.lib.MineTogetherLib;
import net.creeperhost.minetogether.lib.web.ApiClient;
import net.creeperhost.minetogether.lib.web.DynamicWebAuth;
import net.creeperhost.minetogethercommunity.config.Config;
import net.creeperhost.minetogethercommunity.util.Log4jUtils;
import net.creeperhost.minetogethercommunity.util.ModPackInfo;
import net.creeperhost.minetogethercommunity.util.SignatureVerifier;
import net.fabricmc.api.EnvType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

/**
 * Main common mod entrypoint.
 * <p>
 * Created by covers1624 on 20/6/22.
 */
public class MineTogether {

    private static final Logger LOGGER = LogManager.getLogger();

    public static final String MOD_ID = "minetogethercommunity";

    public static final String FINGERPRINT = SignatureVerifier.generateSignature();
    public static final DynamicWebAuth AUTH = new DynamicWebAuth();
    public static final HttpEngine WEB_ENGINE = Java11HttpEngine.create();
    public static final ApiClient API = ApiClient.builder()
            .httpEngine(WEB_ENGINE)
            .addUserAgentSegment("MineTogether-lib/" + MineTogetherLib.VERSION)
            .addUserAgentSegment("MineTogether-Community-mod/" + MineTogetherPlatform.getVersion())
            .addUserAgentSegment("Minecraft/" + Platform.getMinecraftVersion())
            .addUserAgentSegment("Modloader/" + ArchitecturyTarget.getCurrentTarget())
            .webAuth(AUTH)
            .build();

    public static void init() {
        Log4jUtils.attachMTLogs(Platform.getGameFolder().resolve("logs"));
        LOGGER.info("Initializing MineTogether Community!");
        AUTH.setHeader("Fingerprint", FINGERPRINT);

        if (Config.instance().debugMode) {
            LOGGER.warn("Debug mode enabled. Prepare for _VERY_ verbose logging!");
        }

        ModPackInfo.init();
        ModPackInfo.waitForInfo(info -> AUTH.setHeader("Identifier", info.realName));
        if (Objects.requireNonNull(Platform.getEnv()) == EnvType.CLIENT) {
            MineTogetherClient.init();
        }
    }
}
