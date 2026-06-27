package net.creeperhost.minetogethercommunity;

import net.covers1624.quack.net.httpapi.HttpEngine;
import net.creeperhost.minetogether.lib.MineTogetherLib;
import net.creeperhost.minetogether.lib.web.ApiClient;
import net.creeperhost.minetogether.lib.web.DynamicWebAuth;
import net.creeperhost.minetogethercommunity.config.Config;
import net.creeperhost.minetogethercommunity.proxy.CommonProxy;
import net.creeperhost.minetogethercommunity.util.Log4jUtils;
import net.creeperhost.minetogethercommunity.util.ModPackInfo;
import net.creeperhost.minetogethercommunity.util.SignatureVerifier;
import net.covers1624.quack.net.httpapi.apache.ApacheEngine;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(
        modid = MineTogether.MOD_ID,
        name = MineTogether.NAME,
        version = MineTogether.VERSION,
        acceptedMinecraftVersions = "[1.8.9]",
        acceptableRemoteVersions = "*"
)
public class MineTogether {

    public static final String MOD_ID = "minetogethercommunity";
    public static final String NAME = "MineTogether Community";
    public static final String VERSION = "6.3.4-1.8.9";

    private static final Logger LOGGER = LogManager.getLogger(NAME);

    @Mod.Instance(MOD_ID)
    public static MineTogether instance;

    @SidedProxy(
            clientSide = "net.creeperhost.minetogethercommunity.proxy.ClientProxy",
            serverSide = "net.creeperhost.minetogethercommunity.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    public static final DynamicWebAuth AUTH = new DynamicWebAuth();
    public static final HttpEngine WEB_ENGINE = ApacheEngine.create();
    public static String FINGERPRINT = System.getProperty("mt.develop.signature", isDeobfuscated() ? "Development" : "Unknown");
    public static final ApiClient API = ApiClient.builder()
            .httpEngine(WEB_ENGINE)
            .addUserAgentSegment("MineTogether-lib/" + MineTogetherLib.VERSION)
            .addUserAgentSegment("MineTogether-Community-mod/" + VERSION)
            .addUserAgentSegment("Minecraft/1.8.9")
            .addUserAgentSegment("Modloader/forge")
            .webAuth(AUTH)
            .build();

    private static File gameDir;
    private static File configDir;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        gameDir = event.getModConfigurationDirectory().getParentFile();
        configDir = event.getModConfigurationDirectory();
        Log4jUtils.attachMTLogs(new File(gameDir, "logs").toPath());
        Config.load(new File(configDir, MOD_ID + ".json"));

        FINGERPRINT = SignatureVerifier.generateSignature(event.getSourceFile(), isDeobfuscated());
        AUTH.setHeader("Fingerprint", FINGERPRINT);
        ModPackInfo.init();
        ModPackInfo.VersionInfo packInfo = ModPackInfo.getInfo();
        setPackIdentifier(packInfo);
        ModPackInfo.waitForInfo(this::setPackIdentifier);
        LOGGER.info("Initialized MineTogether Community config for Forge 1.8.9 with fingerprint {} and identifier {}.",
                maskedFingerprint(), packInfo.realName);
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    public static File getGameDir() {
        return gameDir;
    }

    public static File getConfigDir() {
        return configDir;
    }

    private static boolean isDeobfuscated() {
        Object deobfuscated = Launch.blackboard.get("fml.deobfuscatedEnvironment");
        return Boolean.TRUE.equals(deobfuscated);
    }

    private static String maskedFingerprint() {
        if (FINGERPRINT == null || FINGERPRINT.length() < 12) return String.valueOf(FINGERPRINT);
        return FINGERPRINT.substring(0, 8) + "..." + FINGERPRINT.substring(FINGERPRINT.length() - 6);
    }

    private void setPackIdentifier(ModPackInfo.VersionInfo info) {
        AUTH.setHeader("Identifier", info.realName);
    }
}
