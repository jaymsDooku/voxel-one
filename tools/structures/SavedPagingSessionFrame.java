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

/** Real CPU/GPU paging through the newly returned private World/scene bundle. */
final class SavedPagingSessionFrame {
    static void capture(Path root,Path image)throws Exception {
        long baseline=Mesh.estimatedResidentBytes();var tests=new ReadbackSaveSnapshotTest();tests.folder=Files.createDirectories(root.resolve("contexts"));
        try(var c=tests.new Context("frame")) {
            var editor=new RegionReplayEditor(c.world,new RegionReplayJournal());var saved=new Protocol.Edit(2,160,2,Blocks.STONE);editor.apply(saved,()->false);
            var snapshot=ReadbackSaveSnapshot.captureJournal(c.local,c.world,editor.capture(()->false),()->false);var old=c.fixture.worker(c.path);ReadbackMetadataTest.ready(old);RegionReplayWorkerTest.closed(old);
            try(var prepared=c.stage(snapshot)) {
                var publication=prepared.authorize(()->false);var later=PendingReplayOverlayTest.fraction();editor.apply(later,()->false);editor.apply(new Protocol.Edit(6,160,2,Blocks.LED).withColor(0xed593c),()->false);
                c.worker.submit(()->{publication.publish();publication.close();return null;}).get(10,TimeUnit.SECONDS);
                var pending=publication.pendingPagingOverlay(()->false);var spool=Files.createDirectories(c.fixture.folder.resolve("paging"));
                var worker=new RegionReplayWorker(spool,publication.pagingFactory(old,spool,SavedPagingFactoryTest.NEXT,pending),1,1,56*1024,2000);
                try {
                    ReadbackMetadataTest.ready(worker);byte[] disk=Files.readAllBytes(c.path);
                    try(var session=publication.adoptPagingScene(worker,SavedPagingFactoryTest.NEXT,pending,1,8192,()->false);
                            var shader=new ShaderProgram("shaders/voxel.vert","shaders/planar.frag");var models=new VoxelModelRenderer(session.local().models)) {
                        assertTrue(session.world().hasReplayReadView());assertFalse(session.world().isLoaded(0,160,0));
                        var scene=session.scene();scene.desired(List.of(new ChunkPos(0,10,0)));long end=System.nanoTime()+10_000_000_000L;
                        while(scene.status().data().ready()!=1&&System.nanoTime()<end){scene.tick();Thread.sleep(5);}
                        assertEquals(1,scene.status().data().ready());assertFalse(session.world().isLoaded(0,160,0));
                        for(int step=0;scene.status().gpuEntries()!=1&&step<10000;step++)scene.pumpGpu(64,System.nanoTime()+100_000_000L,()->false);
                        assertEquals(1,scene.status().gpuEntries());assertTrue(session.world().isLoaded(0,160,0));assertFalse(session.local().replayComplete());assertThrows(java.io.IOException.class,session.local()::save);
                        glViewport(0,0,640,480);glEnable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);glClearColor(.04f,.07f,.12f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
                        shader.bind();shader.setMatrix4("uProjection",ReplaySkyFrameCheck.projection());shader.setMatrix4("uView",new Matrix4f().translation(0,-160,0));shader.setMatrix4("uModel",new Matrix4f());
                        shader.setInt("uVertexColor",1);shader.setInt("uInstanced",0);shader.setFloat("uAmbient",2);shader.setFloat("uDaylight",1);shader.setVector3("uSun",0,1,0);
                        assertEquals(1,session.world().renderReplayGeometry(ReplaySkyFrameCheck.visible(),shader));models.render(session.world(),ReplaySkyFrameCheck.visible(),shader);glFinish();assertEquals(GL_NO_ERROR,glGetError());
                        var pixels=MemoryUtil.memAlloc(640*480*4);
                        try {
                            glReadPixels(0,0,640,480,GL_RGBA,GL_UNSIGNED_BYTE,pixels);var output=new BufferedImage(640,480,BufferedImage.TYPE_INT_RGB);int red=0,blue=0;
                            for(int y=0;y<480;y++)for(int x=0;x<640;x++) {int i=(y*640+x)*4,r=pixels.get(i)&255,g=pixels.get(i+1)&255,b=pixels.get(i+2)&255;if(r>b*2&&r>50)red++;if(b>r*2&&b>50)blue++;output.setRGB(x,479-y,(r<<16)|(g<<8)|b);}
                            assertTrue(red>100&&blue>10,"Saved session renders pending coloured geometry: red="+red+", blue="+blue);assertTrue(javax.imageio.ImageIO.write(output,"png",image.toFile()));
                        }finally{MemoryUtil.memFree(pixels);}
                        assertEquals(pending.rows(),editor.capture(()->false).overlay().rows());assertArrayEquals(disk,Files.readAllBytes(c.path));assertFalse(c.world.hasReplayReadView());
                    }
                    assertEquals(baseline,Mesh.estimatedResidentBytes());
                }finally{RegionReplayWorkerTest.closed(worker);}
            }
        }
    }
}
