import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;

/** Synthetic file-bundle capture, stale input fencing and private generation publication. */
public final class RegionOfflineBundleCheck {
    static int rejected;
    interface Action{void run()throws Exception;}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void reject(Class<? extends Throwable> type,Action action)throws Exception{try{action.run();}catch(Throwable error){if(type.isInstance(error)){rejected++;return;}throw new AssertionError(error);}throw new AssertionError("Unexpected acceptance");}
    static RegionOfflineBundlePrototype.Snapshot capture(Path base,Object monitor,AtomicLong revision)throws IOException{synchronized(monitor){return RegionOfflineBundlePrototype.capture(base,monitor,revision::get,()->false);}}
    static long temps(Path folder)throws IOException{try(var files=Files.list(folder)){return files.filter(p->p.getFileName().toString().startsWith(".region-stage-")).count();}}
    public static void main(String[] args)throws Exception {
        Path parent=Path.of("target/structure-offline-bundle-tests");Files.createDirectories(parent);Path root=Files.createTempDirectory(parent,"synthetic-");
        try{run(root);}finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
    }
    static void run(Path root)throws Exception {
        Path base=root.resolve("world");var game=new LocalGame(base,123);game.inventory.add(Blocks.STONE,5);game.health=7;game.save();byte[] original=Files.readAllBytes(base);
        Object monitor=new Object();var revision=new AtomicLong();var storeRoot=root.resolve("destination");var store=new RegionGenerationPrototype(storeRoot);
        reject(IOException.class,()->RegionOfflineBundlePrototype.capture(base,monitor,revision::get,()->false));
        var snapshot=capture(base,monitor,revision);try(var stage=snapshot.stage(store,()->false)){reject(IOException.class,()->stage.commit(()->false));synchronized(monitor){stage.commit(()->false);}}
        require(new RegionAuthorityPrototype(storeRoot,123,Terrain.CURRENT_VERSION).metadata().orElseThrow().health()==7,"Base metadata lost");byte[] saved=Files.readAllBytes(storeRoot.resolve("current.region"));
        // Unsaved source changes must fence publication even when on-disk bytes have not changed.
        try(var stage=capture(base,monitor,revision).stage(store,()->false)){revision.incrementAndGet();synchronized(monitor){reject(IOException.class,()->stage.commit(()->false));}}
        require(Arrays.equals(saved,Files.readAllBytes(storeRoot.resolve("current.region")))&&temps(storeRoot)==0,"Revision failure changed destination/leaked stage");
        for(String change:List.of("base","new-city","new-history","missing-base")){
            var captured=capture(base,monitor,revision);try(var stage=captured.stage(store,()->false)){
                switch(change){case "base" -> {var modified=original.clone();modified[modified.length-1]^=1;Files.write(base,modified);}case "new-city" -> Files.write(base.resolveSibling("world.city"),new byte[]{1});case "new-history" -> Files.write(base.resolveSibling("world.city.history"),new byte[]{1});case "missing-base" -> Files.delete(base);}
                synchronized(monitor){reject(IOException.class,()->stage.commit(()->false));}
            }finally{Files.write(base,original);Files.deleteIfExists(base.resolveSibling("world.city"));Files.deleteIfExists(base.resolveSibling("world.city.history"));}
            require(Arrays.equals(saved,Files.readAllBytes(storeRoot.resolve("current.region")))&&temps(storeRoot)==0,"Changed bundle published/leaked stage");
        }
        Path city=base.resolveSibling("world.city"),history=base.resolveSibling("world.city.history");
        var ground=new CitySimulation.Ground(){public int type(int x,int y,int z){return y<=0?Blocks.STONE:0;}public void apply(List<Protocol.Edit> edits){}public boolean occupied(int x,int y,int z,int w,int d){return false;}};
        new CitySimulation(GameConfig.cityGame(),ground,new Terrain(123),null).save(city);byte[] cityBytes=Files.readAllBytes(city),historyBytes=Files.readAllBytes(history);
        var full=capture(base,monitor,revision);try(var stage=full.stage(store,()->false)){synchronized(monitor){stage.commit(()->false);}}
        var restarted=new RegionAuthorityPrototype(storeRoot,123,Terrain.CURRENT_VERSION);require(Arrays.equals(cityBytes,restarted.sidecars().orElseThrow().city())&&Arrays.equals(historyBytes,restarted.sidecars().orElseThrow().history().orElseThrow()),"Full bundle lost sidecars");
        saved=Files.readAllBytes(storeRoot.resolve("current.region"));
        for(String changed:List.of("city","history","removed-city","removed-history")){
            try(var stage=capture(base,monitor,revision).stage(store,()->false)){
                switch(changed){case "city" -> {var bytes=cityBytes.clone();bytes[bytes.length-1]^=1;Files.write(city,bytes);}case "history" -> {var bytes=historyBytes.clone();bytes[bytes.length-1]^=1;Files.write(history,bytes);}case "removed-city" -> Files.delete(city);case "removed-history" -> Files.delete(history);}
                synchronized(monitor){reject(IOException.class,()->stage.commit(()->false));}
            }finally{Files.write(city,cityBytes);Files.write(history,historyBytes);}
            require(Arrays.equals(saved,Files.readAllBytes(storeRoot.resolve("current.region")))&&temps(storeRoot)==0,"Sidecar change published/leaked stage");
        }
        try(var stage=capture(base,monitor,revision).stage(store,()->false)){synchronized(monitor){reject(CancellationException.class,()->stage.commit(()->true));}}
        synchronized(monitor){reject(CancellationException.class,()->RegionOfflineBundlePrototype.capture(base,monitor,revision::get,()->true));reject(IOException.class,()->RegionOfflineBundlePrototype.capture(base,monitor,()->-1,()->false));}
        // Missing/non-regular/oversize source files reject before decoding or publication.
        Files.delete(city);reject(IOException.class,()->capture(base,monitor,revision));Files.write(city,cityBytes);
        Files.write(history,new byte[0]);reject(IOException.class,()->capture(base,monitor,revision));Files.write(history,historyBytes);
        Path bad=root.resolve("bad");Files.createDirectory(bad);reject(IOException.class,()->capture(bad,monitor,revision));Files.delete(bad);
        try(var file=FileChannel.open(bad,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){file.position(RegionOfflineImportPrototype.MAX_BYTES);file.write(java.nio.ByteBuffer.wrap(new byte[]{0}));}reject(IOException.class,()->capture(bad,monitor,revision));Files.delete(bad);
        boolean symlinkChecked=false;
        try{Files.createSymbolicLink(bad,base);symlinkChecked=true;reject(IOException.class,()->capture(bad,monitor,revision));}
        catch(UnsupportedOperationException|FileSystemException unavailable){/* Platform cannot create this synthetic symlink; report it explicitly. */}
        finally{Files.deleteIfExists(bad);}
        var cancelled=new AtomicInteger();synchronized(monitor){reject(CancellationException.class,()->RegionOfflineBundlePrototype.capture(base,monitor,revision::get,()->cancelled.incrementAndGet()>10));}
        var revisionCalls=new AtomicInteger();synchronized(monitor){reject(IOException.class,()->RegionOfflineBundlePrototype.capture(base,monitor,()->revisionCalls.incrementAndGet()>=3?1:0,()->false));}
        reject(CancellationException.class,()->snapshot.stage(store,()->true));
        Thread.currentThread().interrupt();try{synchronized(monitor){reject(CancellationException.class,()->RegionOfflineBundlePrototype.capture(base,monitor,revision::get,()->false));}}finally{Thread.interrupted();}
        var stable=capture(base,monitor,revision);try(var first=stable.stage(store,()->false);var second=stable.stage(store,()->false)){
            synchronized(monitor){first.commit(()->false);reject(IOException.class,()->second.commit(()->false));reject(IOException.class,()->first.commit(()->false));}
        }
        try(var stage=capture(base,monitor,revision).stage(store,()->false)){synchronized(monitor){stage.commit(()->false);}}
        require(Arrays.equals(original,Files.readAllBytes(base))&&Arrays.equals(cityBytes,Files.readAllBytes(city))&&Arrays.equals(historyBytes,Files.readAllBytes(history))&&temps(storeRoot)==0,"Source preservation or cleanup failed");
        System.out.printf("{\"status\":\"passed\",\"bundle_slots\":3,\"generation_restart\":true,\"rejection_cases\":%d,\"source_files_unchanged\":true,\"symlink_checked\":%s,\"production_integration\":false,\"qualification\":false}%n",rejected,symlinkChecked);
    }
}
