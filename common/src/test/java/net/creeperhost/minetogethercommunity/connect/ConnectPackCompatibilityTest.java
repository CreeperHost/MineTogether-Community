package net.creeperhost.minetogethercommunity.connect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectPackCompatibilityTest {

    @Test
    void samePackJoinsDirectlyWhileDifferentAndUnknownPacksWarn() {
        assertFalse(RemoteServer.shouldWarnBeforeJoin(RemoteServer.PackCompatibility.SAME));
        assertTrue(RemoteServer.shouldWarnBeforeJoin(RemoteServer.PackCompatibility.DIFFERENT));
        assertTrue(RemoteServer.shouldWarnBeforeJoin(RemoteServer.PackCompatibility.UNKNOWN));
    }

    @Test
    void mockedCatalogueMetadataProducesCorrectManualSelections() {
        ConnectPackResolver.ManualSelection curse = selection("curseforge", "925200", "", 101);
        ConnectPackResolver.ManualSelection ftb = selection("ftb", "119", "12034", 102);
        ConnectPackResolver.ManualSelection modrinth = selection("modrinth", "5FFgwNNP", "JQpSjXOD", 103);

        assertEquals("925200", curse.connectKey());
        assertEquals("curseforge", curse.projectType());
        assertEquals("MTE5MTIwMzQ=", ftb.connectKey());
        assertEquals("ftb", ftb.projectType());
        assertEquals("mr:5FFgwNNP", modrinth.connectKey());
        assertEquals("JQpSjXOD", modrinth.projectVersion());
    }

    private ConnectPackResolver.ManualSelection selection(String type, String project, String version, int id) {
        ConnectPackResolver.DetailResponse detail = new ConnectPackResolver.DetailResponse();
        detail.id = id;
        detail.name = "Fixture Pack";
        detail.meta = new ConnectPackResolver.Meta();
        detail.meta.projectType = type;
        detail.meta.projectId = project;
        detail.meta.projectVersion = version;
        detail.meta.versionFor = "1.20.1";
        ConnectPackResolver.ManualSelection selection = ConnectPackResolver.fromMeta(detail, "Fallback");
        assertNotNull(selection);
        return selection;
    }
}
