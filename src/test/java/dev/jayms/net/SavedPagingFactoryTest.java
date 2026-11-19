package dev.jayms.net;

import dev.jayms.ChunkPos;
import dev.jayms.World;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class SavedPagingFactoryTest {
    @TempDir Path folder;
    static final RegionReplayWorker.Revision NEXT=new RegionReplayWorker.Revision(ReadbackMetadataTest.REV.world()+1,ReadbackMetadataTest.REV.models(),ReadbackMetadataTest.REV.terrain());
    ReadbackSaveSnapshotTest contexts()throws IOException {var t=new ReadbackSaveSnapshotTest();t.folder=Files.createDirectories(folder.resolve("contexts"));return t;}
    RegionReplayWorker prior(ReadbackSaveSnapshotTest.Context c)throws Exception {var worker=c.fixture.worker(c.path);ReadbackMetadataTest.ready(worker);return worker;}
    @Test public void retiredSourceCreatesFreshOwnedWorkerAndRejectsOldReceipts()throws Exception {
        try(var c=contexts().new Context("replacement");var prepared=c.stage(c.snapshot())) {
            var old=prior(c);var local=old.metadataResult(ReadbackMetadataTest.REV);local.health--;int liveHealth=local.health;
            try(var world=new World(local.seed,local.models,local.generatorVersion)) {
                var oldPage=old.submitFenced(new ChunkPos(0,10,0),new RegionReplayPage.CellRange(0,64),64,ReadbackMetadataTest.REV,world);
                RegionReplayWorkerTest.await(()->oldPage.outcome().phase()==RegionReplayWorker.Phase.READY);assertNotNull(oldPage.result(ReadbackMetadataTest.REV,world));
                var publication=prepared.authorize(()->false);c.worker.submit(()->{publication.publish();publication.close();return null;}).get(10,TimeUnit.SECONDS);
                var spool=Files.createDirectories(c.fixture.folder.resolve("saved-paging"));assertThrows(IOException.class,()->publication.pagingFactory(old,spool,NEXT));
                RegionReplayWorkerTest.closed(old);assertThrows(IOException.class,()->oldPage.result(ReadbackMetadataTest.REV,world));
                assertThrows(IOException.class,()->publication.pagingFactory(old,spool,ReadbackMetadataTest.REV));
                c.worker.submit(()->{assertThrows(IOException.class,()->publication.pagingFactory(old,spool,NEXT));return null;}).get(10,TimeUnit.SECONDS);
                var factory=publication.pagingFactory(old,spool,NEXT);
                assertThrows(IOException.class,()->factory.open(()->false,n->{}));
                var next=new RegionReplayWorker(spool,factory,1,1,56*1024,2000);
                try {
                    ReadbackMetadataTest.ready(next);var saved=next.metadataResult(NEXT);assertNotEquals(local.readbackFingerprint(),saved.readbackFingerprint());assertFalse(saved.replayComplete());assertThrows(IOException.class,saved::save);assertEquals(liveHealth,local.health);assertEquals(c.local.health,saved.health);
                    var page=next.submit(new ChunkPos(0,10,0),new RegionReplayPage.CellRange(0,64),64,NEXT);RegionReplayWorkerTest.await(()->page.outcome().phase()==RegionReplayWorker.Phase.READY);
                    var image=page.result(NEXT).cells().get(new WorldVoxels.CellPosition(0,160,0));assertNotNull(image);assertEquals(0,image.tree().get(0,0,0));assertThrows(IOException.class,()->page.result(ReadbackMetadataTest.REV));
                    var duplicate=new RegionReplayWorker(spool,factory,1,1,56*1024,2000);
                    try{RegionReplayWorkerTest.await(()->duplicate.status().state()!=RegionReplayWorker.State.OPENING);assertEquals(RegionReplayWorker.State.FAILED,duplicate.status().state());}finally{RegionReplayWorkerTest.closed(duplicate);}
                }finally{RegionReplayWorkerTest.closed(next);}
                try(var files=Files.list(spool)){assertEquals(0,files.count());}
            }finally{RegionReplayWorkerTest.closed(old);}
        }
    }
    @Test public void differentValidDestinationFailsFactoryAndCleansItsOwnedSpool()throws Exception {
        try(var c=contexts().new Context("changed");var prepared=c.stage(c.snapshot())) {
            var old=prior(c);RegionReplayWorkerTest.closed(old);var publication=prepared.authorize(()->false);
            c.worker.submit(()->{publication.publish();publication.close();return null;}).get(10,TimeUnit.SECONDS);
            var spool=Files.createDirectories(c.fixture.folder.resolve("saved-paging"));
            var foreignPath=Files.write(c.fixture.folder.resolve("foreign.dat"),c.original);var foreign=c.fixture.worker(foreignPath);
            try{ReadbackMetadataTest.ready(foreign);RegionReplayWorkerTest.closed(foreign);assertThrows(IOException.class,()->publication.pagingFactory(foreign,spool,NEXT));}finally{RegionReplayWorkerTest.closed(foreign);}
            var factory=publication.pagingFactory(old,spool,NEXT);byte[] published=Files.readAllBytes(c.path);Files.write(c.path,c.original);
            var failed=new RegionReplayWorker(spool,factory,1,1,56*1024,2000);
            try{RegionReplayWorkerTest.await(()->failed.status().state()!=RegionReplayWorker.State.OPENING);assertEquals(RegionReplayWorker.State.FAILED,failed.status().state());assertArrayEquals(c.original,Files.readAllBytes(c.path));}finally{RegionReplayWorkerTest.closed(failed);}
            try(var files=Files.list(spool)){assertEquals(0,files.count());}
            Files.write(c.path,published);var retry=new RegionReplayWorker(spool,publication.pagingFactory(old,spool,NEXT),1,1,56*1024,2000);
            try{ReadbackMetadataTest.ready(retry);}finally{RegionReplayWorkerTest.closed(retry);}
            try(var files=Files.list(spool)){assertEquals(0,files.count());}
        }
    }
}
