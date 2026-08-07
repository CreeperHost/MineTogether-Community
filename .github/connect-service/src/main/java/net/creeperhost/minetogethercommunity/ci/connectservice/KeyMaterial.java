package net.creeperhost.minetogethercommunity.ci.connectservice;

import net.creeperhost.minetogether.connect.lib.util.RSAUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

record KeyMaterial(PrivateKey privateKey, PublicKey publicKey) {

    private static final String PRIVATE_FILE = "connect-private.pem";
    private static final String PUBLIC_FILE = "connect-public.pem";

    static KeyMaterial loadOrCreate(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path privatePath = directory.resolve(PRIVATE_FILE);
        Path publicPath = directory.resolve(PUBLIC_FILE);
        if (Files.exists(privatePath) != Files.exists(publicPath)) {
            throw new IOException("Both local MTConnect key files must exist or both must be absent");
        }
        if (Files.exists(privatePath)) {
            return new KeyMaterial(
                    RSAUtils.loadRSAPrivateKeyPem(privatePath),
                    RSAUtils.loadRSAPublicKeyPem(publicPath)
            );
        }

        KeyPair pair = RSAUtils.generateRSAKeyPair();
        Files.write(privatePath, RSAUtils.encodeRSAKey(pair.getPrivate()), StandardCharsets.US_ASCII);
        Files.write(publicPath, RSAUtils.encodeRSAKey(pair.getPublic()), StandardCharsets.US_ASCII);
        return new KeyMaterial(pair.getPrivate(), pair.getPublic());
    }
}
