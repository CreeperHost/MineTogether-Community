package net.creeperhost.minetogethercommunity;

import net.covers1624.quack.net.httpapi.HttpEngine;
import net.covers1624.quack.net.httpapi.java11.Java11HttpEngine;
import net.creeperhost.minetogether.lib.MineTogetherLib;
import net.creeperhost.minetogether.lib.web.ApiClient;
import net.creeperhost.minetogether.lib.web.DynamicWebAuth;
import net.creeperhost.minetogethercommunity.config.Config;
import net.creeperhost.minetogethercommunity.util.Log4jUtils;
import net.creeperhost.minetogethercommunity.util.ModPackInfo;
import net.creeperhost.minetogethercommunity.util.SignatureVerifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
            .addUserAgentSegment("Minecraft/" + MineTogetherPlatform.getMinecraftVersion())
            .addUserAgentSegment("Modloader/" + MineTogetherPlatform.getPlatformName())
            .webAuth(AUTH)
            .build();

    public static void init() {
        Log4jUtils.attachMTLogs(MineTogetherPlatform.getGameFolder().resolve("logs"));
        LOGGER.info("Initializing MineTogether Community!");
        AUTH.setHeader("Fingerprint", FINGERPRINT);

        if (Config.instance().debugMode) {
            LOGGER.warn("Debug mode enabled. Prepare for _VERY_ verbose logging!");
        }

        ModPackInfo.init();
        ModPackInfo.waitForInfo(info -> AUTH.setHeader("Identifier", info.realName));
        if (MineTogetherPlatform.isClient()) {
            MineTogetherClient.init();
        }
    }
}
