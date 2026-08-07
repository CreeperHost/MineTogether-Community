package net.creeperhost.minetogethercommunity.ci.connectservice;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityTest {

    @Test
    void parsesDeterministicSessionClaims() {
        UUID uuid = UUID.fromString("10000000-0000-4000-8000-000000000001");
        Identity identity = Identity.parse(token(uuid, "CI Host"));

        assertEquals(uuid, identity.uuid());
        assertEquals("CI Host", identity.username());
        assertEquals(Identity.hash(uuid), identity.uuidHash());
    }

    @Test
    void rejectsAHashThatDoesNotBelongToTheSubject() {
        UUID uuid = UUID.fromString("10000000-0000-4000-8000-000000000001");
        String payload = "{\"sub\":\"" + uuid + "\",\"usn\":\"CI Host\",\"sha\":\"00\"}";
        String token = "header." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".signature";

        assertThrows(IllegalArgumentException.class, () -> Identity.parse(token));
    }

    static String token(UUID uuid, String username) {
        String payload = "{\"sub\":\"" + uuid + "\",\"usn\":" + Json.string(username)
                + ",\"sha\":\"" + Identity.hash(uuid) + "\"}";
        return "header." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".signature";
    }
}
