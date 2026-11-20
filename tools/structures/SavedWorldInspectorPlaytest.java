package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.ui.SavedRegionCapturePanel;
import dev.jayms.net.structure.StructurePackage;
import dev.jayms.ui.Controls;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL33.*;
import static org.junit.jupiter.api.Assertions.*;

/** Same Main inspection launch and real framebuffer/recorder. Synthetic file,
 * owner-thread camera controls, no login, account, config or save workflow. */
public final class SavedWorldInspectorPlaytest {
    static void image(Path file,boolean geometry)throws Exception {
        int[] w={0},h={0};var viewport=new int[4];glGetIntegerv(GL_VIEWPORT,viewport);w[0]=viewport[2];h[0]=viewport[3];
        var pixels=MemoryUtil.memAlloc(w[0]*h[0]*4);
        try {
            glFinish();glReadPixels(0,0,w[0],h[0],GL_RGBA,GL_UNSIGNED_BYTE,pixels);assertEquals(GL_NO_ERROR,glGetError());var output=new BufferedImage(w[0],h[0],BufferedImage.TYPE_INT_RGB);int red=0,blue=0;
            for(int y=0;y<h[0];y++)for(int x=0;x<w[0];x++){int i=(y*w[0]+x)*4,r=pixels.get(i)&255,g=pixels.get(i+1)&255,b=pixels.get(i+2)&255;if(r>70&&r>b*2)red++;if(b>50&&b>r*2)blue++;output.setRGB(x,h[0]-1-y,(r<<16)|(g<<8)|b);}
            if(geometry){assertTrue(red>100,"Saved red geometry visible");assertTrue(blue>10,"Saved blue model or water visible");}assertTrue(javax.imageio.ImageIO.write(output,"png",file.toFile()));
        }finally{MemoryUtil.memFree(pixels);}
    }
    static void command(SavedWorldInspector app,String text) {
        for(char value:text.toCharArray())app.capturePanel().character(value);
        app.capturePanel().enter(new StructurePackage.Position(8,164,10));
    }
    static Path capturedFixture(Path root)throws Exception {
        var worker=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{return worker.submit(()->SavedRegionCaptureWorkflowFixture.create(root)).get(60,java.util.concurrent.TimeUnit.SECONDS);}
        finally{worker.shutdownNow();assertTrue(worker.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS));}
    }
    public static void main(String[] args)throws Exception {
        Path root=Files.createDirectories(Path.of(args[0])),evidence=Path.of(args[1]);String prefix=args[2];var file=args.length>3&&args[3].equals("--captured-region")?capturedFixture(root):SavedWorldInspectorFixture.create(root);var original=Files.readAllBytes(file);assertEquals(10,java.nio.ByteBuffer.wrap(original).getInt());long baseline=Mesh.estimatedResidentBytes();String[] hardware={null,null};boolean[] completed={false,false,false,false};
        var seen=new HashSet<String>();int[] stage={0},ticks={0},readyGpu={0};long[] recordStarted={0};
        var observer=new SavedWorldInspector.Observer() {
            public void started(SavedWorldInspector app)throws Exception {
                hardware[0]=glGetString(GL_RENDERER);hardware[1]=glGetString(GL_VERSION);
                assertThrows(IllegalStateException.class,()->SavedWorldInspector.launchIfRequested(new String[]{"--inspect-world",file.toString()}));
            }
            public void frame(SavedWorldInspector app,SavedWorldInspector.Frame frame)throws Exception {
                seen.add(frame.stage());assertEquals(0,frame.residentChunks());assertTrue(frame.cpuChunks()<=2&&frame.gpuChunks()<=2);readyGpu[0]=Math.max(readyGpu[0],frame.gpuChunks());assertNotEquals("unavailable",frame.stage());
                assertTrue(frame.number()<3000,"Bounded native workflow deadline");
                assertFalse(app.capturePanel().status().state()==SavedRegionCapturePanel.State.FAILED||app.capturePanel().status().state()==SavedRegionCapturePanel.State.CLEANUP_FAILED,"Native capture must succeed or cancel");
                if(stage[0]==0&&frame.stage().equals("hit")&&frame.draws()>0) {
                    assertThrows(IllegalStateException.class,()->app.world().apply(new Protocol.Edit(8,160,0,Blocks.AIR)));assertThrows(IllegalStateException.class,()->app.world().beginStructureSnapshot());
                    image(evidence.resolve(prefix+"-ready.png"),true);app.toggleRecording();recordStarted[0]=System.nanoTime();app.capturePanel().toggle();
                    command(app,"first -8 160 0");command(app,"second 15 167 4");command(app,"anchor 0 160 0");command(app,"capture Saved construction");stage[0]=10;
                }else if(stage[0]==10&&app.capturePanel().status().state()==SavedRegionCapturePanel.State.SAVED) {
                    var asset=StructurePackage.load(Controls.directory().resolve("structures/Saved construction.vstr"));assertEquals(195,asset.cells.size());assertEquals(1,asset.models.size());assertEquals(new StructurePackage.Bounds(24,8,5,8,0,0),asset.bounds);
                    image(evidence.resolve(prefix+"-capture.png"),false);command(app,"capture ../invalid");assertTrue(app.capturePanel().status().message().startsWith("Name:"));
                    command(app,"first 0 0 0");command(app,"second 1023 255 1023");command(app,"anchor 0 0 0");command(app,"capture Cancelled region");command(app,"cancel");stage[0]=11;
                }else if(stage[0]==11&&app.capturePanel().status().state()==SavedRegionCapturePanel.State.CANCELLED) {
                    assertFalse(Files.exists(Controls.directory().resolve("structures/Cancelled region.vstr")));app.capturePanel().toggle();stage[0]=1;
                }else if(stage[0]==1) {
                    app.control(0,-1,0,0,0,.05f);if(++ticks[0]>=28){stage[0]=2;ticks[0]=0;}
                }else if(stage[0]==2&&app.position().x<0&&frame.stage().equals("hit")&&frame.draws()>0) {
                    image(evidence.resolve(prefix+"-moved.png"),false);stage[0]=3;
                }else if(stage[0]==3&&System.nanoTime()-recordStarted[0]>3_000_000_000L) {
                    assertArrayEquals(original,Files.readAllBytes(file));app.toggleRecording();app.requestClose();
                }
            }
            public void closed(SavedWorldInspector app,boolean sourceClosed)throws Exception {assertTrue(sourceClosed);assertTrue(stage[0]>=3);assertEquals(baseline,Mesh.estimatedResidentBytes());completed[0]=true;}
        };
        try(var scope=SavedWorldInspector.observe(observer)){Main.main(new String[]{"--inspect-world",file.toString(),"--inspect-position","8.5","164.5","10.5"});}
        assertTrue(completed[0]);assertTrue(seen.contains("loading"));assertArrayEquals(original,Files.readAllBytes(file));
        Path invalid=root.resolve("synthetic-invalid.dat");Files.write(invalid,new byte[]{1,2,3});var bad=Files.readAllBytes(invalid);
        try(var scope=SavedWorldInspector.observe(new SavedWorldInspector.Observer(){
            public void frame(SavedWorldInspector app,SavedWorldInspector.Frame frame)throws Exception {assertTrue(frame.number()<3000);if(frame.stage().equals("unavailable")){image(evidence.resolve(prefix+"-invalid.png"),false);app.requestClose();}}
            public void closed(SavedWorldInspector app,boolean sourceClosed){assertTrue(sourceClosed);completed[1]=true;}
        })){Main.main(new String[]{"--inspect-world",invalid.toString()});}
        assertArrayEquals(bad,Files.readAllBytes(invalid));assertTrue(completed[1]);
        try(var scope=SavedWorldInspector.observe(new SavedWorldInspector.Observer(){
            public void started(SavedWorldInspector app){app.requestClose();}
            public void frame(SavedWorldInspector app,SavedWorldInspector.Frame frame){fail("Cancelled before first poll");}
            public void closed(SavedWorldInspector app,boolean sourceClosed){assertTrue(sourceClosed);completed[2]=true;}
        })){Main.main(new String[]{"--inspect-world",file.toString()});}
        assertTrue(completed[2]);assertArrayEquals(original,Files.readAllBytes(file));
        try(var scope=SavedWorldInspector.observe(new SavedWorldInspector.Observer(){
            public void frame(SavedWorldInspector app,SavedWorldInspector.Frame frame)throws Exception {throw new java.io.IOException("Synthetic observer failure");}
            public void closed(SavedWorldInspector app,boolean sourceClosed)throws Exception {assertTrue(sourceClosed);completed[3]=true;throw new java.io.IOException("Synthetic cleanup failure");}
        })){var failure=assertThrows(java.io.IOException.class,()->Main.main(new String[]{"--inspect-world",file.toString()}));assertEquals("Synthetic observer failure",failure.getMessage());assertTrue(Arrays.stream(failure.getSuppressed()).anyMatch(t->"Synthetic cleanup failure".equals(t.getMessage())));}
        assertTrue(completed[3]);assertEquals(baseline,Mesh.estimatedResidentBytes());assertArrayEquals(original,Files.readAllBytes(file));
        try(var files=Files.list(Path.of(System.getProperty("java.io.tmpdir")))){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith("voxel-inspection-")));}
        Files.writeString(root.resolve("result.json"),"{\"status\":\"passed\",\"renderer\":\""+hardware[0]+"\",\"gl_version\":\""+hardware[1]+"\",\"main_dispatch\":true,\"camera_crossed_chunk\":true,\"loading_then_hit\":true,\"maximum_observed_gpu_chunks\":"+readyGpu[0]+",\"resident_chunks\":0,\"source_bytes_unchanged\":true,\"invalid_source_isolated\":true,\"cancel_before_poll\":true,\"observer_failure_cleanup\":true,\"scratch_mesh_cleanup\":true,\"saved_capture_panel\":true,\"capture_cancel_no_file\":true,\"qualification\":false}\n");
    }
}
