package net.creeperhost.minetogethercommunity.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class ModPackInfoTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void detectsInstalledLauncherMetadataWithoutNetworkLookups() throws Exception {
        ModPackInfo.VersionInfo curse = detectGameFixture("curseforge", "minecraftinstance.json");
        ModPackInfo.VersionInfo ftb = detectGameFixture("ftb-app-official", "instance.json");
        ModPackInfo.VersionInfo modrinth = detectPrismFixture("prism-modrinth");
        assertEquals("925200", curse.curseID);
        assertEquals("m119", ftb.ftbPackID);
        assertEquals("MTE5MTIwMzQ=", ftb.base64FTBID);
        assertEquals("5FFgwNNP", modrinth.modrinthProjectID);
        assertEquals("JQpSjXOD", modrinth.modrinthVersionID);
        assertEquals("{\"p\":\"mr:5FFgwNNP\",\"v\":\"JQpSjXOD\"}", modrinth.realName);
    }

    @Test public void detectsFtbAppCurseAndAllPrismCatalogues() throws Exception {
        assertEquals("925200", detectGameFixture("ftb-app-curse", "instance.json").curseID);
        assertEquals("925200", detectPrismFixture("prism-curse").curseID);
        assertEquals("m119", detectPrismFixture("prism-ftb").ftbPackID);
    }

    @Test public void detectsCurrentModrinthAppInstanceLink() throws Exception {
        File root = temporary.newFolder("modrinth-app");
        File game = new File(new File(root, "profiles"), "Cobblemon 1.21");
        assertTrue(game.mkdirs());
        copyFixture("modrinth-app/app.db", new File(root, "app.db"));
        ModPackInfo.VersionInfo info = ModPackInfo.detectLauncherInfo(game, new File(game, "config"));
        assertEquals("5FFgwNNP", info.modrinthProjectID);
        assertEquals("JQpSjXOD", info.modrinthVersionID);
    }

    @Test public void launcherDetectionOrderKeepsFtbAppAheadOfCurseForgeAndPrism() throws Exception {
        File instance = temporary.newFolder("ordered");
        File game = new File(instance, "game");
        assertTrue(game.mkdirs());
        copyFixture("ftb-app-official/instance.json", new File(game, "instance.json"));
        copyFixture("curseforge/minecraftinstance.json", new File(game, "minecraftinstance.json"));
        copyFixture("prism-curse/instance.cfg", new File(instance, "instance.cfg"));
        ModPackInfo.VersionInfo info = ModPackInfo.detectLauncherInfo(game, new File(game, "config"));
        assertEquals("m119", info.ftbPackID);
        assertTrue(info.curseID.isEmpty());
        assertTrue(info.modrinthProjectID.isEmpty());
    }

    @Test public void modrinthMappingUsesStatusAndEncodedIdentifier() throws Exception {
        ModrinthPackLookup.Result missing = new ModrinthPackLookup.Result(404, null, null);
        ModrinthPackLookup.Result failed = new ModrinthPackLookup.Result(500, null, null);
        assertTrue(missing.isNotFound());
        assertFalse(missing.isSuccessful());
        assertFalse(failed.isNotFound());
        assertEquals("https://www.creeperhost.net/json/modpacks/modrinth/version%20id", ModrinthPackLookup.urlFor("version id"));
    }

    private ModPackInfo.VersionInfo detectGameFixture(String fixture, String file) throws Exception {
        File game = temporary.newFolder(fixture, "game");
        copyFixture(fixture + "/" + file, new File(game, file));
        return ModPackInfo.detectLauncherInfo(game, new File(game, "config"));
    }

    private ModPackInfo.VersionInfo detectPrismFixture(String fixture) throws Exception {
        File instance = temporary.newFolder(fixture);
        File game = new File(instance, ".minecraft");
        assertTrue(game.mkdirs());
        copyFixture(fixture + "/instance.cfg", new File(instance, "instance.cfg"));
        return ModPackInfo.detectLauncherInfo(game, new File(game, "config"));
    }

    private void copyFixture(String fixture, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        InputStream input = getClass().getResourceAsStream("/modpacks/" + fixture);
        assertNotNull("Missing fixture " + fixture, input);
        try (InputStream stream = input; FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) >= 0) output.write(buffer, 0, read);
        }
    }
}
