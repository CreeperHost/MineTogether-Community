package net.creeperhost.minetogethercommunity.ci.connectservice;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessPolicyTest {

    @Test
    void friendshipsAreSymmetricAndLimitsAreValidated() {
        UUID first = UUID.fromString("10000000-0000-4000-8000-000000000001");
        UUID second = UUID.fromString("20000000-0000-4000-8000-000000000002");
        AccessPolicy policy = new AccessPolicy();

        assertFalse(policy.areFriends(first, second));
        policy.setFriend(first, second, true);
        assertTrue(policy.areFriends(first, second));
        assertTrue(policy.areFriends(second, first));
        policy.setFriend(second, first, false);
        assertFalse(policy.areFriends(first, second));

        assertThrows(IllegalArgumentException.class, () -> policy.setLimit(first, 0));
        assertThrows(IllegalArgumentException.class, () -> policy.setLimit(first, -2));
    }
}
