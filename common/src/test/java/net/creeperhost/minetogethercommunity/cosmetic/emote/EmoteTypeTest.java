package net.creeperhost.minetogethercommunity.cosmetic.emote;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmoteTypeTest {

    @Test
    void missingTypeRemainsBackwardCompatibleWithSimpleEmotes() {
        assertEquals(EmoteType.SIMPLE, EmoteType.fromMetadata(null));
        assertEquals(EmoteType.SIMPLE, EmoteType.fromMetadata(""));
        assertEquals(EmoteType.SIMPLE, EmoteType.fromMetadata("  "));
    }

    @Test
    void knownTypesAreMatchedCaseInsensitively() {
        assertEquals(EmoteType.SIMPLE, EmoteType.fromMetadata("SIMPLE"));
        assertEquals(EmoteType.GECKOLIB, EmoteType.fromMetadata("GeckoLib"));
        assertEquals(EmoteType.SIMPLE, EmoteType.fromMetadata(" simple "));
    }

    @Test
    void unknownTypesFailClosed() {
        EmoteType type = EmoteType.fromMetadata("future-runtime");

        assertEquals(EmoteType.UNKNOWN, type);
        assertFalse(type.isKnown());
        assertFalse(type.isAvailable());
        assertTrue(EmoteType.SIMPLE.isKnown());
    }
}
