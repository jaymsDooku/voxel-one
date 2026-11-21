package dev.jayms.net;

import dev.jayms.ChunkPos;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Exercises the actual private cache API; no live-client or city qualification. */
public final class RegionReplayCacheCheck {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Fresh directory, result JSON, evidence PNG");
        var folder=Path.of(args[0]);var result=Path.of(args[1]);var png=Path.of(args[2]);
        require(!Files.exists(folder)&&!Files.exists(result)&&!Files.exists(png),"Fresh outputs required");Files.createDirectory(folder);
        var rows=new ArrayList<Protocol.Edit>();
        for(int r=0;r<6;r++)rows.add(new Protocol.Edit(r*16,0,0,new int[]{0,Blocks.STONE,Blocks.GLASS,Blocks.LED,1031,Blocks.STONE}[r]));
        var encoded=new ByteArrayOutputStream();var history=new LinkedHashMap<String,Protocol.Edit>();
        for(var edit:rows)history.put(edit.key(),edit);
        RegionReplay.write(new DataOutputStream(encoded),history,()->false,n->{});
        var expected=new WorldVoxels(new Terrain(42));rows.forEach(expected::apply);
        var allowed=new AtomicBoolean(true);var calls=new AtomicInteger();var peakFiles=new AtomicInteger();var peakBytes=new AtomicLong();
        var retained=new ArrayList<RegionReplayPage>();int requests=0;
        try(var spool=RegionReplaySpool.stage(folder,new DataInputStream(new ByteArrayInputStream(encoded.toByteArray())),e->true,()->false);
            var cache=new RegionReplayCache(folder,spool,2,56,1,e->{
                calls.incrementAndGet();
                try(var files=Files.list(folder)) {
                    var copies=files.filter(p->p.getFileName().toString().startsWith(".replay-cells-")).toList();long bytes=0;
                    for(var path:copies)bytes+=Files.size(path);
                    require(copies.size()<=2&&bytes<=56,"In-flight cache bounds");
                    peakFiles.accumulateAndGet(copies.size(),Math::max);peakBytes.accumulateAndGet(bytes,Math::max);
                }catch(IOException failure){throw new UncheckedIOException(failure);}
                return allowed.get();
            })) {
            int[] sequence={0,1,0,2,0,1,3,4,5,4};
            for(int r:sequence) {
                var page=cache.load(new ChunkPos(r,0,0),new RegionReplayPage.CellRange(0,1),new Terrain(42),1,()->false);requests++;
                require(new ArrayList<>(page.history().values()).equals(List.of(rows.get(r))),"History order/value oracle");
                var image=page.cells().values().iterator().next();require(image.tree().sameValues(expected.cell(r*16,0,0)),"Geometry oracle");retained.add(page);
                require(cache.retainedRegions()<=2&&cache.retainedBytes()<=56,"Retained bounds");
            }
            require(calls.get()==24,"Seven builds plus ten selected reads"); // 7 builds *2 +10 reads
            allowed.set(false);
            try{cache.load(new ChunkPos(0,0,0),new RegionReplayPage.CellRange(0,1),new Terrain(42),1,()->false);throw new AssertionError("Policy rejection");}
            catch(IOException expectedFailure){}
            allowed.set(true);cache.load(new ChunkPos(0,0,0),new RegionReplayPage.CellRange(0,1),new Terrain(42),1,()->false);
            try{cache.load(new ChunkPos(0,0,0),new RegionReplayPage.CellRange(0,1),new Terrain(42),0,()->false);throw new AssertionError("Row rejection");}
            catch(IOException expectedFailure){}
            spool.close(); // cached reads survive, uncached reads need the caller-owned spool
            var page=cache.load(new ChunkPos(0,0,0),new RegionReplayPage.CellRange(0,1),new Terrain(42),1,()->false);
            require(page.history().size()==1,"Hit after source close");
            try{cache.load(new ChunkPos(3,0,0),new RegionReplayPage.CellRange(0,1),new Terrain(42),1,()->false);throw new AssertionError("Closed source miss");}
            catch(IOException expectedFailure){}
        }
        for(var page:retained)require(page.cells().size()==1,"Detached result lifetime");
        try(var files=Files.list(folder)){require(files.findAny().isEmpty(),"Owned cleanup");}Files.delete(folder);
        String json="{\"status\":\"passed\",\"qualification\":false,\"sequence_requests\":"+requests+",\"regions\":6,\"peak_observed_copy_files\":"+peakFiles.get()+",\"peak_observed_logical_copy_bytes\":"+peakBytes.get()+",\"max_retained_regions\":2,\"copy_budget_bytes\":56}";
        Files.writeString(result,json+"\n");System.out.println(json);
        String text="Voxel One: bounded replay cache CLI\n\nActual cache workflow passed before this window.\n\n6 synthetic regions; 10 load requests; LRU hit/eviction order checked.\nIndependent history and voxel geometry oracles match every page.\nAt most 2 copied files / 56 logical data bytes, including builds.\nPolicy rejection, selected-row rejection and retry passed.\nCached read after caller-owned source close passed.\nUncached read after source close rejected; owned files cleaned up.\nDetached immutable pages survived cache close.\n\nPrivate worker cache only. Source spool remains caller-owned.\nNo live authority/rendering integration, async requests or durable archive.\nModel/terrain/authority revision fences remain caller-owned.\nNo timing, peak-memory, GPU, network or city qualification claim.\nPublic limits and stable rollout stay gated.";
        javax.swing.SwingUtilities.invokeAndWait(()->{
            var frame=new javax.swing.JFrame("Voxel One replay cache CLI");var area=new javax.swing.JTextArea(text);
            area.setEditable(false);area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,0,15));area.setMargin(new java.awt.Insets(20,20,20,20));
            frame.add(area);frame.setSize(1120,530);frame.setLocation(30,30);frame.setVisible(true);
            var timer=new javax.swing.Timer(1200,e->{try{javax.imageio.ImageIO.write(new java.awt.Robot().createScreenCapture(frame.getBounds()),"png",png.toFile());}
                catch(Exception failure){throw new RuntimeException(failure);}finally{frame.dispose();}});timer.setRepeats(false);timer.start();
        });
    }
}
