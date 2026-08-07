package net.creeperhost.minetogethercommunity.connect;

import net.creeperhost.minetogether.session.JWebToken;
import net.creeperhost.minetogether.session.MineTogetherSession;

import java.security.KeyPairGenerator;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/** Supplies Connect credentials while keeping the normal session service out of isolated CI runs. */
public final class ConnectCredentials {
    private static volatile JWebToken testToken;

    public static JWebToken get() throws ExecutionException, InterruptedException {
        String role = System.getenv("MINETOGETHER_CI_ROLE");
        String uuid = System.getenv("MINETOGETHER_CI_CONNECT_UUID");
        String username = System.getenv("MINETOGETHER_CI_CONNECT_USERNAME");
        if (role != null && role.startsWith("connect-") && uuid != null && username != null) {
            JWebToken current = testToken;
            if (current == null) {
                synchronized (ConnectCredentials.class) {
                    current = testToken;
                    if (current == null) testToken = current = createTestToken(UUID.fromString(uuid), username);
                }
            }
            return current;
        }
        return MineTogetherSession.getDefault().getTokenAsync().get();
    }

    static JWebToken createTestToken(UUID uuid, String username) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(256);
            return new JWebToken(uuid, username, generator.generateKeyPair().getPrivate());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create isolated Connect test identity", exception);
        }
    }

    private ConnectCredentials() {
    }
}
