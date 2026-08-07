package net.creeperhost.minetogethercommunity.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModPackInfoTest {

    @TempDir
    Path temporaryFolder;

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

    @Test
    void detectsFtbAppOfficialPackFromStaticInstanceFixture() throws IOException {
        Path game = fixtureGame("ftb-app-official", "instance.json");

        ModPackInfo.PackIdentity identity = detect(game);

        assertNotNull(identity);
        assertEquals(ModPackInfo.PackSource.FTB, identity.source());
        assertEquals("119", identity.projectId());
        assertEquals("12034", identity.versionId());
        assertEquals("MTE5MTIwMzQ=", identity.connectKey());
    }

    @Test
    void detectsFtbAppCursePackFromStaticInstanceFixture() throws IOException {
        Path game = fixtureGame("ftb-app-curse", "instance.json");

        ModPackInfo.PackIdentity identity = detect(game);

        assertNotNull(identity);
        assertEquals(ModPackInfo.PackSource.CURSEFORGE, identity.source());
        assertEquals("925200", identity.projectId());
    }

    @Test
    void detectsCurseForgeLauncherFromStaticFixture() throws IOException {
        Path game = fixtureGame("curseforge", "minecraftinstance.json");

        ModPackInfo.PackIdentity identity = detect(game);

        assertNotNull(identity);
        assertEquals(ModPackInfo.PackSource.CURSEFORGE, identity.source());
        assertEquals("925200", identity.projectId());
    }

    @Test
    void detectsAllPrismManagedPackCataloguesFromStaticFixtures() throws IOException {
        ModPackInfo.PackIdentity curse = detect(fixturePrism("prism-curse"));
        ModPackInfo.PackIdentity ftb = detect(fixturePrism("prism-ftb"));
        ModPackInfo.PackIdentity modrinth = detect(fixturePrism("prism-modrinth"));

        assertNotNull(curse);
        assertEquals(ModPackInfo.PackSource.CURSEFORGE, curse.source());
        assertEquals("925200", curse.projectId());
        assertNotNull(ftb);
        assertEquals(ModPackInfo.PackSource.FTB, ftb.source());
        assertEquals("119", ftb.projectId());
        assertEquals("12034", ftb.versionId());
        assertNotNull(modrinth);
        assertEquals(ModPackInfo.PackSource.MODRINTH, modrinth.source());
        assertEquals("5FFgwNNP", modrinth.projectId());
        assertEquals("JQpSjXOD", modrinth.versionId());
    }

    @Test
    void detectsNamedModrinthAppProfileFromStaticDatabaseFixture() throws IOException {
        Path root = temporaryFolder.resolve("modrinth-app");
        Path game = root.resolve("profiles").resolve("Cobblemon 1.21");
        Files.createDirectories(game);
        copyFixture("modrinth-app/app.db", root.resolve("app.db"));

        ModPackInfo.PackIdentity identity = detect(game);

        assertNotNull(identity);
        assertEquals(ModPackInfo.PackSource.MODRINTH, identity.source());
        assertEquals("5FFgwNNP", identity.projectId());
        assertEquals("JQpSjXOD", identity.versionId());
    }

    private ModPackInfo.PackIdentity detect(Path game) throws IOException {
        Path config = game.resolve("config");
        Files.createDirectories(config);
        return new ModPackInfo.VersionInfo(false, false).detectLauncherIdentity(game, config);
    }

    private Path fixtureGame(String fixture, String file) throws IOException {
        Path game = temporaryFolder.resolve(fixture).resolve("game");
        Files.createDirectories(game);
        copyFixture(fixture + "/" + file, game.resolve(file));
        return game;
    }

    private Path fixturePrism(String fixture) throws IOException {
        Path instance = temporaryFolder.resolve(fixture);
        Path game = instance.resolve(".minecraft");
        Files.createDirectories(game);
        copyFixture(fixture + "/instance.cfg", instance.resolve("instance.cfg"));
        return game;
    }

    private void copyFixture(String fixture, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        try (InputStream stream = getClass().getResourceAsStream("/modpacks/" + fixture)) {
            assertNotNull(stream, "Missing fixture " + fixture);
            Files.copy(stream, destination);
        }
    }
}
