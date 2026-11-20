package dev.jayms.net;

import dev.jayms.*;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.lwjgl.opengl.GL33.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual repeat-save/reopen, then CPU/GPU paging through the returned session. */
final class SavedPagingSessionFrame {
    static void warm(World world,RegionReplayScene scene)throws Exception {
        assertTrue(world.hasReplayReadView());scene.desired(List.of(new ChunkPos(0,10,0)));long end=System.nanoTime()+10_000_000_000L;
        while(scene.status().data().ready()!=1&&System.nanoTime()<end){scene.tick();Thread.sleep(5);}
        assertEquals(1,scene.status().data().ready());assertEquals(scene.status().gpuEntries()==1,world.isLoaded(0,160,0));
        for(int step=0;scene.status().gpuEntries()!=1&&step<10000;step++)scene.pumpGpu(64,System.nanoTime()+100_000_000L,()->false);
        assertEquals(1,scene.status().gpuEntries());assertTrue(world.isLoaded(0,160,0));
    }
    static void render(RegionReplayWorker.PagingSession session,Path image)throws Exception {
        try(var shader=new ShaderProgram("shaders/voxel.vert","shaders/planar.frag");var models=new VoxelModelRenderer(session.local().models)) {
            warm(session.world(),session.scene());assertFalse(session.local().replayComplete());assertThrows(java.io.IOException.class,session.local()::save);
            glViewport(0,0,640,480);glEnable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);glClearColor(.04f,.07f,.12f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
            shader.bind();shader.setMatrix4("uProjection",ReplaySkyFrameCheck.projection());shader.setMatrix4("uView",new Matrix4f().translation(0,-160,0));shader.setMatrix4("uModel",new Matrix4f());
            shader.setInt("uVertexColor",1);shader.setInt("uInstanced",0);shader.setFloat("uAmbient",2);shader.setFloat("uDaylight",1);shader.setVector3("uSun",0,1,0);
            assertEquals(1,session.world().renderReplayGeometry(ReplaySkyFrameCheck.visible(),shader));models.render(session.world(),ReplaySkyFrameCheck.visible(),shader);glFinish();assertEquals(GL_NO_ERROR,glGetError());
            var pixels=MemoryUtil.memAlloc(640*480*4);
            try {
                glReadPixels(0,0,640,480,GL_RGBA,GL_UNSIGNED_BYTE,pixels);var output=new BufferedImage(640,480,BufferedImage.TYPE_INT_RGB);int red=0,blue=0;
                for(int y=0;y<480;y++)for(int x=0;x<640;x++) {int i=(y*640+x)*4,r=pixels.get(i)&255,g=pixels.get(i+1)&255,b=pixels.get(i+2)&255;if(r>b*2&&r>50)red++;if(b>r*2&&b>50)blue++;output.setRGB(x,479-y,(r<<16)|(g<<8)|b);}
                assertTrue(red>100&&blue>10,"Reloaded session renders saved coloured geometry: red="+red+", blue="+blue);assertTrue(javax.imageio.ImageIO.write(output,"png",image.toFile()));
            }finally{MemoryUtil.memFree(pixels);}
        }
    }
    static void capture(Path root,Path image)throws Exception {
        long baseline=Mesh.estimatedResidentBytes();var tests=new ReadbackSaveSnapshotTest();tests.folder=Files.createDirectories(root.resolve("contexts"));
        try(var c=tests.new Context("frame")) {
            int[] added=SavedPagingModelBindingTest.addModelsInOneRevision(c.local.models);
            var editor=new RegionReplayEditor(c.world,new RegionReplayJournal());editor.apply(new Protocol.Edit(2,160,2,Blocks.STONE),()->false);editor.apply(new Protocol.Edit(8,160,0,added[0]),()->false);
            var snapshot=ReadbackSaveSnapshot.captureJournal(c.local,c.world,editor.capture(()->false),()->false);var old=c.fixture.worker(c.path);ReadbackMetadataTest.ready(old);RegionReplayWorkerTest.closed(old);
            try(var prepared=c.stage(snapshot)) {
                var publication=prepared.authorize(()->false);editor.apply(PendingReplayOverlayTest.fraction(),()->false);
                c.worker.submit(()->{publication.publish();publication.close();return null;}).get(10,TimeUnit.SECONDS);
                var spool=Files.createDirectories(c.fixture.folder.resolve("paging"));
                try(var replacement=new SavedPagingReplacement(publication,old,spool,SavedPagingFactoryTest.NEXT,1,8192,()->false)) {
                    SavedPagingReplacementTest.ready(replacement);replacement.prepare(()->false);
                    warm(replacement.warmingWorld(),replacement.warmingScene());var abandonedWorld=replacement.warmingWorld();
                    // Edit after the first candidate warmed: the red geometry
                    // must survive its retirement, fresh source and repeat save.
                    editor.apply(new Protocol.Edit(6,160,2,Blocks.LED).withColor(0xed593c),()->false);
                    assertEquals(SavedPagingReplacement.State.RETIRING,replacement.poll(()->false));SavedPagingReplacementTest.ready(replacement);
                    assertThrows(IllegalStateException.class,()->abandonedWorld.cell(0,160,0));
                    assertEquals(2,replacement.status().attempts());var pending=publication.pendingPagingOverlay(()->false);
                    replacement.prepare(()->false);var firstWorld=replacement.warmingWorld();var firstScene=replacement.warmingScene();var firstBinding=replacement.warmingBinding();warm(firstWorld,firstScene);
                    long firstWarmBytes=Mesh.estimatedResidentBytes();c.local.health--;c.local.inventory.add(Blocks.STONE,1);c.local.drops.put(91,new ItemDrop(91,Blocks.STONE,1,1,2,3));
                    assertFalse(editor.capture(()->false).overlay().rows().isEmpty());
                    var transfer=replacement.take(()->false);var worker=transfer.worker();var revision=transfer.revision();
                    long rows=worker.status().sourceRows();
                    try(transfer) {
                        var session=transfer.session();
                        assertSame(firstWorld,session.world());assertSame(firstScene,session.scene());assertSame(firstBinding,session.binding());assertEquals(1,session.scene().status().gpuEntries());assertEquals(firstWarmBytes,Mesh.estimatedResidentBytes());
                        assertEquals(c.local.health,session.local().health);assertEquals(c.local.drops,session.local().drops);assertNotSame(c.local.drops,session.local().drops);assertNotSame(c.local.inventory,session.local().inventory);
                        assertNotSame(c.local.models,session.local().models);
                        assertNotEquals(c.local.models.revision(),session.local().models.revision());assertTrue(session.local().models.sameBindings(c.local.models));
                        var sky=session.binding().submitSky(0,0,RegionReplaySkyWorkerTest.LIMITS,session.world());RegionReplayWorkerTest.done(sky);
                        assertEquals(session.local().models.revision(),session.binding().skyResult(sky,session.world()).modelRevision());
                        var retiredTree=new dev.jayms.net.model.SparseVoxelOctree(8);retiredTree.set(0,0,0,0xff73519b);
                        int retiredId=c.local.models.stageStructureModel(new dev.jayms.net.model.ModelDefinition("Retired World only",retiredTree)).id();
                        assertFalse(session.local().models.has(retiredId));
                        assertEquals(1,session.scene().status().data().ready());session.local().health--;
                        var second=SavedPagingJournalTest.stage(worker,session,revision);SavedPagingJournalTest.publish(worker,second,revision);
                        var nextPending=second.pendingPagingOverlay(()->false);assertTrue(nextPending.rows().isEmpty());RegionReplayWorkerTest.closed(worker);
                        var nextRevision=new RegionReplayWorker.Revision(revision.world()+1,revision.models(),revision.terrain());
                        var nextSpool=Files.createDirectories(c.fixture.folder.resolve("reloaded"));var nextWorker=new RegionReplayWorker(nextSpool,second.pagingFactory(worker,nextSpool,nextRevision,nextPending),1,1,56*1024,2000);
                        try {
                            ReadbackMetadataTest.ready(nextWorker);assertEquals(rows+pending.rows().size(),nextWorker.status().sourceRows());
                            try(var preparedScene=second.preparePagingScene(nextWorker,nextRevision,nextPending,1,8192,()->false)) {
                                assertFalse(session.journal().capture(()->false).overlay().rows().isEmpty());
                                var warmWorld=preparedScene.world();var warmScene=preparedScene.scene();var warmBinding=preparedScene.binding();warm(warmWorld,warmScene);
                                long warmBytes=Mesh.estimatedResidentBytes();session.local().health--;session.local().inventory.add(Blocks.STONE,1);session.local().drops.put(99,new ItemDrop(99,Blocks.STONE,1,1,2,3));
                                try(var next=preparedScene.commit(()->false)) {
                                preparedScene.close();
                                assertSame(warmWorld,next.world());assertSame(warmScene,next.scene());assertSame(warmBinding,next.binding());assertEquals(1,next.scene().status().gpuEntries());assertEquals(warmBytes,Mesh.estimatedResidentBytes());
                                assertEquals(session.local().drops,next.local().drops);assertNotSame(session.local().drops,next.local().drops);assertNotSame(session.local().inventory,next.local().inventory);
                                for(int i=0;i<Inventory.SIZE;i++){assertEquals(session.local().inventory.type(i),next.local().inventory.type(i));assertEquals(session.local().inventory.count(i),next.local().inventory.count(i));}
                                assertNotSame(session.local().models,next.local().models);assertFalse(next.local().models.has(retiredId));
                                assertTrue(session.journal().capture(()->false).overlay().rows().isEmpty());assertTrue(next.journal().capture(()->false).overlay().rows().isEmpty());assertEquals(session.local().health,next.local().health);
                                byte[] disk=Files.readAllBytes(c.path);render(next,image);assertArrayEquals(disk,Files.readAllBytes(c.path));
                                assertThrows(java.io.IOException.class,()->editor.capture(()->false));assertFalse(c.world.hasReplayReadView());
                                }
                            }
                        }finally{RegionReplayWorkerTest.closed(nextWorker);}
                    }finally{RegionReplayWorkerTest.closed(worker);}
                    assertEquals(baseline,Mesh.estimatedResidentBytes());
                }
            }
        }
    }
}
