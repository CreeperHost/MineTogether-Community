package net.creeperhost.minetogethercommunity.connect;

import net.creeperhost.minetogether.session.JWebToken;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ConnectCredentialsTest {
    @Test
    public void createsARealWireTokenForAnOfflineIdentity() throws Exception {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000201");
        JWebToken token = ConnectCredentials.createTestToken(uuid, "CiConnectHost");

        assertEquals(uuid, token.getUuid());
        assertEquals("CiConnectHost", token.getUsername());
        assertNotNull(token.getUuidHash());
        assertEquals(token.getUuidHash(), JWebToken.parse(token.toString()).getUuidHash());
    }
}
