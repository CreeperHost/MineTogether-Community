package net.creeperhost.minetogethercommunity.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModPackInfoTest {

    @Test
    void modrinthIdentityUsesProjectForConnectAndVersionForLookup() {
        ModPackInfo.PackIdentity identity = ModPackInfo.PackIdentity.modrinth("mr:A1b2C3d4", "V5w6X7y8");

        assertEquals(ModPackInfo.PackSource.MODRINTH, identity.source());
        assertEquals("A1b2C3d4", identity.projectId());
        assertEquals("V5w6X7y8", identity.versionId());
        assertEquals("mr:A1b2C3d4", identity.connectKey());
        assertEquals("V5w6X7y8", identity.lookupIdentifier());
        assertEquals("{\"p\":\"mr:A1b2C3d4\",\"v\":\"V5w6X7y8\"}", identity.identifierJson());
        assertEquals("modrinth", identity.source().telemetryName());
        assertEquals("A1b2C3d4", identity.telemetryPackId());
        assertEquals("V5w6X7y8", identity.telemetryVersionId());
    }

    @Test
    void establishedCurseForgeAndFtbFormatsRemainCompatible() {
        ModPackInfo.PackIdentity curse = ModPackInfo.PackIdentity.curseForge("123456");
        ModPackInfo.PackIdentity ftb = ModPackInfo.PackIdentity.ftb("123", "456");

        assertEquals("{\"p\":\"123456\"}", curse.identifierJson());
        assertEquals("123456", curse.connectKey());
        assertEquals("curse", curse.source().telemetryName());

        assertEquals("MTIzNDU2", ftb.connectKey());
        assertEquals("{\"p\":\"m123\",\"b\":\"MTIzNDU2\"}", ftb.identifierJson());
        assertEquals("m123", ftb.telemetryPackId());
        assertEquals("MTIzNDU2", ftb.telemetryVersionId());
    }

    @Test
    void prismManagedPackFieldsSupportAllCatalogues() {
        ModPackInfo.PackIdentity modrinth = ModPackInfo.identityFromMultiMcValues(Map.of(
                "ManagedPackType", "modrinth",
                "ManagedPackID", "A1b2C3d4",
                "ManagedPackVersionID", "V5w6X7y8"
        ));
        ModPackInfo.PackIdentity curse = ModPackInfo.identityFromMultiMcValues(Map.of(
                "ManagedPackType", "flame",
                "ManagedPackID", "123456"
        ));
        ModPackInfo.PackIdentity ftb = ModPackInfo.identityFromMultiMcValues(Map.of(
                "ManagedPackType", "ftb",
                "ManagedPackID", "123",
                "ManagedPackVersionID", "456"
        ));

        assertNotNull(modrinth);
        assertEquals(ModPackInfo.PackSource.MODRINTH, modrinth.source());
        assertNotNull(curse);
        assertEquals(ModPackInfo.PackSource.CURSEFORGE, curse.source());
        assertNotNull(ftb);
        assertEquals(ModPackInfo.PackSource.FTB, ftb.source());
    }

    @Test
    void prismIconFallbackAndInvalidNumericIdsAreHandled() {
        ModPackInfo.PackIdentity fallback = ModPackInfo.identityFromMultiMcValues(Map.of("iconKey", "modrinth_A1b2C3d4"));
        ModPackInfo.PackIdentity invalidCurse = ModPackInfo.identityFromMultiMcValues(Map.of(
                "ManagedPackType", "curseforge",
                "ManagedPackID", "not-numeric"
        ));

        assertNotNull(fallback);
        assertEquals("mr:A1b2C3d4", fallback.connectKey());
        assertNull(invalidCurse);
    }

    @Test
    void nativeModrinthScanSupportsCurrentInstanceLinksAndLegacyRecords() {
        String instanceId = "local:69f4cdee-d173-4e99-8974-47a89b005715";
        byte[] current = ("instance-row" + instanceId + "-profile-name-padding-"
                + "link-row" + instanceId + "modrinth_modpackA1b2C3d4V5w6X7y8").getBytes(StandardCharsets.UTF_8);
        byte[] legacy = "profile-name-padding-modrinth_modpackA1b2C3d4V5w6X7y8".getBytes(StandardCharsets.UTF_8);
        byte[] invalid = "profile-name-padding-modrinth_modpackbad!id!!V5w6X7y8".getBytes(StandardCharsets.UTF_8);

        ModPackInfo.PackIdentity identity = ModPackInfo.findModrinthAppIdentity(current, "profile-name");

        assertNotNull(identity);
        assertEquals("A1b2C3d4", identity.projectId());
        assertEquals("V5w6X7y8", identity.versionId());
        assertNotNull(ModPackInfo.findModrinthAppIdentity(legacy, "profile-name"));
        assertNull(ModPackInfo.findModrinthAppIdentity(current, "different-profile"));
        assertNull(ModPackInfo.findModrinthAppIdentity(invalid, "profile-name"));
    }

    @Test
    void modrinthLookupTreatsOnly404AsNotFoundAndEncodesIdentifiers() {
        ModrinthPackLookup.Result missing = new ModrinthPackLookup.Result(404, null, null);
        ModrinthPackLookup.Result serverError = new ModrinthPackLookup.Result(500, null, null);

        assertTrue(missing.isNotFound());
        assertFalse(missing.isSuccessful());
        assertFalse(serverError.isNotFound());
        assertEquals("https://www.creeperhost.net/json/modpacks/modrinth/version%20id", ModrinthPackLookup.urlFor("version id"));
    }
}
