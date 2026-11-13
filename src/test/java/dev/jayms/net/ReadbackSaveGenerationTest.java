package dev.jayms.net;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReadbackSaveGenerationTest {
    @TempDir Path folder;
    ReadbackMetadataTest fixture(String name)throws Exception{var f=new ReadbackMetadataTest();f.folder=Files.createDirectories(folder.resolve(name));return f;}
    @Test void actualReadbackGenerationGuardsStreamedOverlayPublicationAndNormalReload()throws Exception {
        var f=fixture("roundtrip");var path=f.fixture();var original=new LocalGame(path,0);
        var source=LocalGame.openRegionalReadback(path,f.spool(),ReadbackMetadataTest.REV,()->false,n->{});
        try(var owned=source.spool()) {
            var partial=source.metadata().transfer();assertFalse(partial.replayComplete());assertNotNull(partial.readbackGeneration());assertThrows(IOException.class,partial::save);
            var edit=new Protocol.Edit(0,160,0,0);var overlay=RegionReplayOverlay.capture(List.of(edit),()->false);
            try(var stage=StagedOfflineSave.stageRegions(path,partial.seed,partial.generatorVersion,partial.models,source.spool(),overlay,e->partial.models.has(e.type()),()->false,n->{})) {
                stage.publish(partial.inventory,17,partial.drops.values(),partial.drops.size(),partial.readbackGeneration(),()->false);
            }
            var loaded=new LocalGame(path,0);var expected=new WorldVoxels.History();original.edits.values().forEach(e->WorldVoxels.remember(expected,e));WorldVoxels.remember(expected,edit);
            assertEquals(new ArrayList<>(expected.values()),new ArrayList<>(loaded.edits.values()));assertEquals(17,loaded.health);assertNull(loaded.readbackGeneration());assertTrue(loaded.replayComplete());
            assertFalse(partial.replayComplete());assertThrows(IOException.class,partial::save);
        }
        f.noSpools();
    }
    @Test void laterOrdinarySaveRejectsReadbackTokenAndRetainsNewSave()throws Exception {
        var f=fixture("stale");var path=f.fixture();
        var source=LocalGame.openRegionalReadback(path,f.spool(),ReadbackMetadataTest.REV,()->false,n->{});
        try(var owned=source.spool()) {
            var partial=source.metadata().transfer();
            try(var stage=StagedOfflineSave.stageRegions(path,partial.seed,partial.generatorVersion,partial.models,source.spool(),e->partial.models.has(e.type()),()->false,n->{})) {
                var current=new LocalGame(path,0);current.health=19;current.save();var newer=Files.readAllBytes(path);
                assertThrows(IOException.class,()->stage.publish(partial.inventory,17,List.of(),0,partial.readbackGeneration(),()->false));assertArrayEquals(newer,Files.readAllBytes(path));assertEquals(19,new LocalGame(path,0).health);
            }
            try(var paths=Files.list(f.folder)){assertFalse(paths.anyMatch(p->p.getFileName().toString().startsWith(".structure-offline-")));}
        }
        f.noSpools();
    }
    @Test void matchingCaptureRejectsWrongMissingAndMalformedSourceDigests()throws Exception {
        var path=folder.resolve("bytes");Files.writeString(path,"source");var digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        assertNotNull(WorldSavePublication.captureMatching(path,digest,1024,()->false));
        assertThrows(IOException.class,()->WorldSavePublication.captureMatching(path,"0".repeat(64),1024,()->false));
        assertThrows(IOException.class,()->WorldSavePublication.captureMatching(folder.resolve("absent"),digest,1024,()->false));
        assertThrows(IllegalArgumentException.class,()->WorldSavePublication.captureMatching(path,"invalid",1024,()->false));
        assertThrows(IllegalArgumentException.class,()->WorldSavePublication.captureMatching(path,"X".repeat(64),1024,()->false));
        assertEquals("source",Files.readString(path));
    }
    @Test void bufferedSameSizeRewriteWithRestoredAttributesCannotReturnSourceMetadata()throws Exception {
        var f=fixture("rewrite");var path=f.fixture();var before=Files.readAllBytes(path);assertTrue(before.length<64*1024);var changed=before.clone();changed[changed.length-1]^=1;
        var modified=Files.getLastModifiedTime(path);var rewritten=new AtomicBoolean();
        var failure=assertThrows(IOException.class,()->LocalGame.openRegionalReadback(path,f.spool(),ReadbackMetadataTest.REV,()->false,n->{
            if(n>=2&&rewritten.compareAndSet(false,true))try{Files.write(path,changed);Files.setLastModifiedTime(path,modified);}catch(IOException e){throw new UncheckedIOException(e);}
        }));
        assertTrue(rewritten.get());assertEquals("Save source digest changed",failure.getMessage());assertArrayEquals(changed,Files.readAllBytes(path));f.noSpools();
        Files.write(path,before);var retry=LocalGame.openRegionalReadback(path,f.spool(),ReadbackMetadataTest.REV,()->false,n->{});try(var owned=retry.spool()){assertNotNull(retry.metadata().transfer().readbackGeneration());}f.noSpools();
    }
}
