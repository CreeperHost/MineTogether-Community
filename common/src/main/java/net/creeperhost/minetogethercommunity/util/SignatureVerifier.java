package net.creeperhost.minetogethercommunity.util;

import com.google.common.hash.Hashing;
import net.covers1624.quack.util.HashUtils;
import net.creeperhost.minetogethercommunity.MineTogetherPlatform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

public class SignatureVerifier {

    private static final Logger LOGGER = LogManager.getLogger();

    public static boolean isDebugSignatureSet() {
        return System.getProperty("mt.develop.signature") != null;
    }

    public static String generateSignature() {
        if (!MineTogetherPlatform.isDevelopmentEnvironment() && !isDebugSignatureSet()) {
            Path modJar = MineTogetherPlatform.getModJar();
            if (modJar != null && modJar.toString().endsWith(".jar")) {
                try {
                    return HashUtils.hash(Hashing.sha256(), modJar).toString();
                } catch (IOException ex) {
                    LOGGER.error("Failed to hash mod jar.", ex);
                }
            }
        }
        return System.getProperty("mt.develop.signature", "Development");
    }
}
