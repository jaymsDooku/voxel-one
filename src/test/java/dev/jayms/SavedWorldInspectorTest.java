package dev.jayms;

import dev.jayms.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SavedWorldInspectorTest {
    @TempDir Path folder;
    SavedWorldInspector app(float x,float y,float z){return new SavedWorldInspector(new SavedWorldInspector.Options(folder.resolve("synthetic.dat"),x,y,z));}
    @Test void parsesOnlyExplicitInspectionArgumentsWithoutTouchingTheFile()throws Exception {
        Path file=folder.resolve("long synthetic file name "+"part ".repeat(20)+".dat");
        var result=SavedWorldInspector.parse(new String[]{"--inspect-position","-1.5","160.25","4","--inspect-world",file.toString()});
        assertEquals(file,result.file());assertEquals(-1.5f,result.x());assertEquals(160.25f,result.y());assertEquals(4,result.z());assertFalse(Files.exists(file));
        var defaults=SavedWorldInspector.parse(new String[]{"--inspect-world",file.toString()});assertEquals(8,defaults.x());assertEquals(104,defaults.y());assertEquals(24,defaults.z());
        assertFalse(SavedWorldInspector.launchIfRequested(new String[]{"--game","sandbox","--offline"}));
    }
    @Test void malformedOrMixedModesRejectBeforeNormalMainDispatch() {
        for(String[] args:List.of(new String[]{"--inspect-world"},new String[]{"--inspect-world"," "},new String[]{"--inspect-world","a","--inspect-world","b"},
                new String[]{"--inspect-world","a","--game","hcf"},new String[]{"--game","practice","--inspect-world","a"},new String[]{"--inspect-world","a","--offline"},
                new String[]{"--inspect-world","a","--inspect-position","1","2"},new String[]{"--inspect-world","a","--inspect-position","NaN","160","0"},
                new String[]{"--inspect-world","a","--inspect-position","0","1024","0"},new String[]{"--inspect-world","a","--inspect-position","1000000","160","0"}))
            assertThrows(IllegalArgumentException.class,()->Main.main(args));
        try(var files=assertDoesNotThrow(()->Files.list(folder))){assertEquals(0,files.count());}catch(Exception failure){throw new AssertionError(failure);}
    }
    @Test void boundsCoverOnlyCompleteProtocolChunks() {
        assertTrue(SavedWorldInspector.validPosition(-1_000_000,-1024,999999));assertFalse(SavedWorldInspector.validPosition(1_000_000,0,0));assertFalse(SavedWorldInspector.validPosition(0,Float.POSITIVE_INFINITY,0));
        assertTrue(SavedWorldInspector.validChunk(new ChunkPos(-62500,-64,62499)));assertTrue(SavedWorldInspector.validChunk(new ChunkPos(0,63,0)));
        assertFalse(SavedWorldInspector.validChunk(new ChunkPos(62500,0,0)));assertFalse(SavedWorldInspector.validChunk(new ChunkPos(0,64,0)));assertFalse(SavedWorldInspector.validChunk(new ChunkPos(Integer.MAX_VALUE,0,0)));
    }
    @Test void controlsUseExistingFreeCameraMovementAndClampStepAndWorldBounds() {
        var a=app(8,160,24);a.control(1,0,0,0,0,.05f);assertEquals(23.6f,a.position().z,1e-4);assertEquals(8,a.position().x,1e-4);
        var b=app(8,160,24);b.control(1,0,0,0,0,1000);assertEquals(a.position(),b.position());
        var edge=app(-1_000_000,-1024,999999);for(int i=0;i<10;i++)edge.control(-1,-1,-1,0,0,.05f);assertTrue(SavedWorldInspector.validPosition(edge.position().x,edge.position().y,edge.position().z));
        var held=a.position();held.set(0,0,0);assertNotEquals(held,a.position());
    }
    @Test void badControlsLeaveTheCameraUnchanged() {
        var a=app(8,160,24);var before=a.position();
        assertThrows(IllegalArgumentException.class,()->a.control(Integer.MIN_VALUE,0,0,0,0,.05f));assertThrows(IllegalArgumentException.class,()->a.control(0,2,0,0,0,.05f));
        for(float dt:new float[]{-1,Float.NaN,Float.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->a.control(0,0,0,0,0,dt));
        assertEquals(before,a.position());
    }
    @Test void unknownCpuOnlyAndKnownMissHaveDifferentLabels() {
        var unknown=new BlockRaycaster.Trace(null,new BlockRaycaster.UnavailableCell(0,160,0,false));var cpu=new BlockRaycaster.Trace(null,new BlockRaycaster.UnavailableCell(0,160,0,true));
        assertTrue(SavedWorldInspector.queryLabel(unknown).startsWith("Loading"));assertEquals(SavedWorldInspector.queryLabel(unknown),SavedWorldInspector.queryLabel(cpu));
        assertTrue(SavedWorldInspector.queryLabel(new BlockRaycaster.Trace(null,null)).startsWith("No surface"));assertEquals("Saved cell 1 / 160 / 2",SavedWorldInspector.queryLabel(new BlockRaycaster.Trace(new BlockHit(1,160,2,0,1,0),null)));
    }
    @Test void observerScopeAndControlsRejectForeignThreads()throws Exception {
        var a=app(8,160,24);var executor=Executors.newSingleThreadExecutor();var observer=SavedWorldInspector.observe(new SavedWorldInspector.Observer(){});
        try {
            assertThrows(IllegalStateException.class,()->SavedWorldInspector.observe(new SavedWorldInspector.Observer(){}));
            executor.submit(()->{assertThrows(IllegalStateException.class,()->a.control(1,0,0,0,0,.05f));assertThrows(IllegalStateException.class,a::position);assertThrows(IllegalStateException.class,observer::close);return null;}).get(10,TimeUnit.SECONDS);
        }finally{observer.close();executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
        try(var next=SavedWorldInspector.observe(new SavedWorldInspector.Observer(){})){observer.close();assertThrows(IllegalStateException.class,()->SavedWorldInspector.observe(new SavedWorldInspector.Observer(){}));assertNotNull(next);}
    }
}
