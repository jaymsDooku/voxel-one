package dev.jayms;

import dev.jayms.launcher.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HubCatalogTest {
    private HubCatalog parse(String text) throws IOException { return HubCatalog.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1))); }
    private String bundled() throws IOException { try (var in = HubCatalog.class.getResourceAsStream("catalog.properties")) { return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1); } }
    @Test void syntheticEntryAppearsWithoutUiCodeAndUsesSharedArtifact() throws Exception {
        String text = bundled(); String extra = text.substring(text.indexOf("sandbox.title=") , text.indexOf("space-invaders.title=")).replace("sandbox.", "new-game.");
        HubCatalog c = parse(text.replace("entries=city,sandbox,space-invaders,engine-editor", "entries=city,sandbox,space-invaders,engine-editor,new-game") + extra);
        assertEquals(5, c.entries().size());
        var e = c.entries().get(4); assertTrue(e.supported(Platform.WINDOWS));
        assertEquals("voxel-client", e.artifact()); assertEquals(java.util.List.of("--game", "sandbox", "--offline"), e.arguments(true, "", 0));
        assertEquals(java.util.List.of("--game", "sandbox", "--server", "localhost", "--port", "25565"), e.arguments(false, "localhost", 25565));
    }
    @Test void unsafeMetadataCannotExecute() throws Exception {
        String text = bundled();
        assertThrows(IOException.class, () -> parse(text.replace("format=1", "format=2")));
        assertThrows(IOException.class, () -> parse(text.replace("entries=city,", "entries=../city,")));
        assertThrows(IOException.class, () -> parse(text.replace("https://voxel-one.jamesleaver1.chatgpt.site/games/space-invaders/", "https://evil.example/game")));
        var e = parse(text.replace("city.adapter=native", "city.adapter=shell")).entries().get(0);
        assertFalse(e.supported(Platform.LINUX)); assertThrows(IllegalArgumentException.class, () -> e.arguments(true, "", 0));
        assertFalse(parse(text.replace("city.target=city", "city.target=arbitrary")).entries().get(0).supported(Platform.LINUX));
        assertThrows(IOException.class, () -> parse("x".repeat(HubCatalog.MAX_BYTES+1)));
        assertTrue(parse(text).entries().get(2).supported(Platform.WINDOWS));
        assertFalse(parse(text.replace("space-invaders.source=1d2c3b3266c9e2d9c82c2217594690c0a62334ef", "space-invaders.source=unpublished")).entries().get(2).supported(Platform.WINDOWS));
        assertFalse(HubCatalog.safeWeb("https://user@github.com/jaymsDooku/voxel-one/"));
    }
    @Test void compatibilityRequiresExplicitPublishedProvenance() throws Exception {
        String text = bundled();
        var entry = parse(text).entries().get(0);
        assertEquals(java.util.Set.of(entry.source()), entry.clientSources());
        entry.requireClient(new Updater.Result(java.nio.file.Path.of("verified.jar"), entry.source(), false, ""));
        assertThrows(IOException.class, () -> entry.requireClient(new Updater.Result(java.nio.file.Path.of("other.jar"), "b".repeat(40), false, "")));
        assertThrows(IOException.class, () -> parse(text.replace("source-set-v1", "any")));
        assertThrows(IOException.class, () -> parse(text.replace("city.client-sources=" + entry.source(), "city.client-sources=" + "b".repeat(40))));
        assertThrows(IOException.class, () -> parse(text.replace("city.client-sources=" + entry.source(), "city.client-sources=" + entry.source() + ",../bad")));
        assertThrows(IOException.class, () -> parse(text.replace("city.client-compatibility=source-set-v1", "")));
    }
    @Test void cachedLibrarySurvivesMalformedSnapshot() throws Exception {
        var dir = java.nio.file.Files.createTempDirectory("hub-catalog-test");
        try {
            java.nio.file.Files.writeString(dir.resolve("catalog.properties"), "malformed");
            assertEquals(4, new CatalogStore(dir).cached().catalog().entries().size());
        } finally { java.nio.file.Files.deleteIfExists(dir.resolve("catalog.properties")); java.nio.file.Files.delete(dir); }
    }
}
