import dev.jayms.ChunkPos;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.StructurePackage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Synthetic CLI integration: disk cache -> detached patch -> actual world queries. */
public final class DiskRegionCacheCheck {
    interface Action{void run()throws Exception;}
    static int rejects;
    static void require(boolean ok){if(!ok)throw new AssertionError();}
    static void reject(Class<? extends Throwable> type,Action action)throws Exception {
        try{action.run();}catch(Throwable e){if(type.isInstance(e)){rejects++;return;}throw new AssertionError(e);}
        throw new AssertionError("Expected "+type.getSimpleName());
    }
    static SparseVoxelOctree solid(int type){var t=new SparseVoxelOctree(16);t.fill(0,0,0,16,16,16,WorldVoxels.encode(type));return t;}
    static StructurePackage fixture()throws Exception {
        var fractional=new SparseVoxelOctree(16);fractional.fill(1,2,3,7,8,9,WorldVoxels.encode(new Protocol.Edit(0,0,0,Blocks.LED,0,0,0,0,0x123456)));
        var voxels=new SparseVoxelOctree(8);voxels.set(1,2,3,0xff123456);
        return new StructurePackage("Cache check",new StructurePackage.Bounds(64,2,2,1,1,1),
                List.of(new ModelDefinition("Cache model",voxels)),List.of(
                new StructurePackage.Cell(new StructurePackage.Position(0,0,0),fractional,-1),
                new StructurePackage.Cell(new StructurePackage.Position(16,0,0),solid(Blocks.GLASS),-1),
                new StructurePackage.Cell(new StructurePackage.Position(32,0,0),solid(Blocks.BRICKS),-1),
                new StructurePackage.Cell(new StructurePackage.Position(33,0,0),null,0)));
    }
    static void check(Path root)throws Exception {
        var pkg=fixture();var source=root.resolve("cache.vstr");pkg.save(source);
        try(var index=DiskPackageIndexPrototype.open(source,root,()->false);
            var cache=new DiskRegionCachePrototype(index,2,1024*1024)) {
            var retained=cache.cell(0,0,0,()->false).orElseThrow();
            require(retained.equals(pkg.cells.get(0))&&cache.successfulLoads()==1);
            reject(IllegalStateException.class,()->retained.geometry().set(0,0,0,1));
            cache.cell(16,0,0,()->false);cache.cell(0,0,0,()->false);cache.cell(32,0,0,()->false);
            require(cache.residentPages()==2&&cache.successfulLoads()==3);
            cache.cell(0,0,0,()->false);require(cache.successfulLoads()==3);
            cache.cell(16,0,0,()->false);require(cache.successfulLoads()==4);
            require(retained.geometry().sameValues(pkg.cells.get(0).geometry()));
            require(cache.cell(48,0,0,()->false).isEmpty()&&cache.successfulLoads()==4);
            require(cache.cell(17,0,0,()->false).isEmpty());
            reject(IllegalArgumentException.class,()->cache.cell(-1,0,0,()->false));
            reject(IllegalArgumentException.class,()->cache.cell(64,0,0,()->false));
            reject(CancellationException.class,()->cache.cell(16,0,0,()->true));
            var calls=new AtomicInteger();long before=cache.successfulLoads();
            reject(CancellationException.class,()->cache.cell(32,0,0,()->calls.incrementAndGet()>4));
            require(cache.successfulLoads()==before&&cache.residentPages()==2);
            var executor=Executors.newSingleThreadExecutor();
            try{require(executor.submit(()->{try{cache.cell(0,0,0,()->false);return false;}catch(IllegalStateException expected){return true;}catch(IOException e){throw new UncheckedIOException(e);}}).get());rejects++;}
            finally{executor.shutdownNow();}

            // Actual world root replacement is separate and atomic at the caller's merge.
            var world=new WorldVoxels(new Terrain(42));
            world.apply(new Protocol.Edit(-16,180,-16,Blocks.STONE)); // source hole
            world.apply(new Protocol.Edit(500,180,500,Blocks.STONE));
            var chunks=new ArrayList<ChunkPos>();for(int x=0;x<64;x+=16)chunks.add(ChunkPos.fromBlock(x-17,180,-17));
            // Offset -17 straddles package sections; include every affected destination chunk.
            for(var c:pkg.cells){var p=c.position();chunks.add(ChunkPos.fromBlock(p.x()-17,180+p.y(),p.z()-17));}
            var cancelled=world.captureCellPatch(chunks);
            cancelled.replaceCell(-17,180,-17,retained.geometry());
            reject(CancellationException.class,()->cache.cell(32,0,0,()->true));
            require(world.type(-17,180,-17)==0); // abandoned patch never published
            var patch=world.captureCellPatch(chunks);var library=new ModelLibrary();
            int model=library.stageStructureModel(index.model(0,()->false)).id();
            for(var c:pkg.cells){var p=c.position();var loaded=cache.cell(p.x(),p.y(),p.z(),()->false).orElseThrow();
                patch.replaceCell(p.x()-17,180+p.y(),p.z()-17,loaded.model()<0?loaded.geometry():solid(model));}
            world.apply(new Protocol.Edit(501,180,500,Blocks.GLASS));
            var candidate=patch.merge(world);require(candidate!=null);
            for(var c:pkg.cells){var p=c.position();require(candidate.cell(p.x()-17,180+p.y(),p.z()-17).sameValues(c.model()<0?c.geometry():solid(model)));}
            require(candidate.type(-16,180,-16)==Blocks.STONE&&candidate.type(501,180,500)==Blocks.GLASS);
            require(world.type(15,180,-17)==0&&candidate.type(16,180,-17)==model);
            require(library.get(model).definition().fingerprint().equals(pkg.models.get(0).fingerprint()));
            world.apply(new Protocol.Edit(-18,180,-17,Blocks.BRICKS));require(patch.merge(world)==null);
            cache.close();require(cache.residentPages()==0&&cache.residentEncodedBytes()==0);
            reject(IOException.class,()->cache.cell(0,0,0,()->false));
            require(retained.geometry().sameValues(pkg.cells.get(0).geometry()));
        }
        try(var index=DiskPackageIndexPrototype.open(source,root,()->false)) {
            reject(IllegalArgumentException.class,()->new DiskRegionCachePrototype(index,0,1024));
            reject(IllegalArgumentException.class,()->new DiskRegionCachePrototype(index,129,1024));
            reject(IllegalArgumentException.class,()->new DiskRegionCachePrototype(index,2,0));
            reject(IllegalArgumentException.class,()->new DiskRegionCachePrototype(index,2,8L*1024*1024+1));
            long allowance=index.regions.stream().mapToLong(DiskPackageIndexPrototype.Region::bytes).max().orElseThrow();
            try(var cache=new DiskRegionCachePrototype(index,2,allowance)) {
                cache.cell(0,0,0,()->false);cache.cell(16,0,0,()->false);
                require(cache.residentPages()==1&&cache.residentEncodedBytes()<=allowance);
                cache.cell(0,0,0,()->false);require(cache.successfulLoads()==3&&cache.residentEncodedBytes()==allowance);
            }
        }
        try(var index=DiskPackageIndexPrototype.open(source,root,()->false);
            var cache=new DiskRegionCachePrototype(index,2,1)) {
            reject(IOException.class,()->cache.cell(0,0,0,()->false));require(cache.residentPages()==0);
        }
        try(var index=DiskPackageIndexPrototype.open(source,root,()->false);
            var cache=new DiskRegionCachePrototype(index,2,1024*1024)) {
            cache.cell(0,0,0,()->false);var region=index.regions.get(1);
            try(var file=new RandomAccessFile(index.decoded.toFile(),"rw")){file.seek(region.offset());int b=file.read();file.seek(region.offset());file.write(b^1);}
            reject(IOException.class,()->cache.cell(16,0,0,()->false));require(cache.successfulLoads()==1&&cache.residentPages()==1);
            require(cache.cell(0,0,0,()->false).orElseThrow().equals(pkg.cells.get(0)));
        }
        try(var paths=Files.list(root)){require(paths.map(p->p.getFileName().toString()).toList().equals(List.of("cache.vstr")));}
    }
    static void corpus(Path source,Path root)throws Exception {
        long start=System.nanoTime();long[] cells={0},leaves={0},instances={0};long peakBytes=0;int peakPages=0;
        try(var index=DiskPackageIndexPrototype.open(source,root,()->false);
            var cache=new DiskRegionCachePrototype(index,2,1024*1024)) {
            double indexed=(System.nanoTime()-start)/1e9;long visit=System.nanoTime();
            for(int region=0;region<index.regions.size();region++) {
                index.visitRegion(region,expected->{
                    try {
                        var p=expected.position();var actual=cache.cell(p.x(),p.y(),p.z(),()->false).orElseThrow();
                        require(actual.equals(expected));cells[0]++;
                        if(actual.model()<0)leaves[0]+=actual.geometry().leafCount();else instances[0]++;
                    }catch(IOException failure){throw new UncheckedIOException(failure);}
                },()->false);
                peakPages=Math.max(peakPages,cache.residentPages());peakBytes=Math.max(peakBytes,cache.residentEncodedBytes());
            }
            require(cells[0]==index.stats.cells()&&leaves[0]==index.stats.leaves()&&instances[0]==index.stats.instances());
            require(peakPages<=2&&peakBytes<=1024*1024&&cache.successfulLoads()==index.regions.size());
            System.out.printf(Locale.ROOT,"{\"status\":\"passed\",\"cells\":%d,\"leaves\":%d,\"instances\":%d,\"regions\":%d,\"loads\":%d,\"peak_pages\":%d,\"peak_encoded_bytes\":%d,\"index_seconds\":%.6f,\"visit_seconds\":%.6f,\"production_admission\":false,\"qualification\":false}%n",cells[0],leaves[0],instances[0],index.regions.size(),cache.successfulLoads(),peakPages,peakBytes,indexed,(System.nanoTime()-visit)/1e9);
        }
    }
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory(Path.of("target"),"region-cache-check-");
        boolean corpus=args.length==1&&!args[0].equals("--evidence");
        try{if(corpus)corpus(Path.of(args[0]),root);else check(root);}finally{try(var files=Files.list(root)){for(var p:files.toList())Files.delete(p);}Files.delete(root);}
        if(corpus)return;
        String result="{\"status\":\"passed\",\"cells\":4,\"regions\":3,\"cache_pages\":2,\"rejection_cases\":"+rejects+",\"world_patch_queries\":true,\"qualification\":false}";
        System.out.println(result);
        if(args.length==1&&args[0].equals("--evidence"))javax.swing.SwingUtilities.invokeAndWait(()->{
            var frame=new javax.swing.JFrame("Voxel One bounded region cache CLI evidence");
            var text=new javax.swing.JTextArea("Voxel One: bounded disk-region cache integration\n\nActual CLI check completed with exit 0 checks before this window.\n"+result+"\n\nSynthetic four-cell / three-region production package.\nTwo-page LRU eviction, frozen results, holes and custom model restored.\nDisk reads stage a detached WorldVoxels patch; actual world queries match.\nCancelled staging leaves world unchanged; concurrent affected edits reject.\nCorrupt and over-budget pages never enter the cache.\nCross-thread access and closed handles reject; owned cache files cleaned.\n\nEncoded-byte budget is not total heap or retained-object memory.\nPrivate diagnostic only: game importer, admission and saves unchanged.\nCity/max, physical GPU and shaped 32-client qualification remain pending.");
            text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,0,15));text.setMargin(new java.awt.Insets(20,20,20,20));
            frame.add(text);frame.setSize(1000,540);frame.setLocation(60,60);frame.setVisible(true);
            var timer=new javax.swing.Timer(15000,e->frame.dispose());timer.setRepeats(false);timer.start();
        });
    }
}
