package net.creeperhost.minetogethercommunity.cosmetic.emote;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EmoteTypeTest {
    @Test
    public void missingTypeRemainsBackwardCompatible() {
        assertEquals(EmoteType.SIMPLE, EmoteType.fromMetadata(null));
        assertEquals(EmoteType.SIMPLE, EmoteType.fromMetadata("  "));
    }

    @Test
    public void knownTypesAreCaseInsensitive() {
        assertEquals(EmoteType.SIMPLE, EmoteType.fromMetadata(" SIMPLE "));
        assertEquals(EmoteType.JSON, EmoteType.fromMetadata(" JSON "));
        assertEquals(EmoteType.GECKOLIB, EmoteType.fromMetadata("GeckoLib"));
    }

    @Test
    public void unknownTypesFailClosed() {
        assertEquals(EmoteType.UNKNOWN, EmoteType.fromMetadata("future-runtime"));
        assertFalse(EmoteType.UNKNOWN.isKnown());
        assertFalse(EmoteType.UNKNOWN.isAvailable());
        assertTrue(EmoteType.SIMPLE.isKnown());
    }
}
