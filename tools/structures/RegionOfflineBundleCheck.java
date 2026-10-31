import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/** Synthetic file-bundle capture, stale input fencing and private generation publication. */
public final class RegionOfflineBundleCheck {
    static int rejected;static boolean server;
    interface Action{void run()throws Exception;}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void reject(Class<? extends Throwable> type,Action action)throws Exception{try{action.run();}catch(Throwable error){if(type.isInstance(error)){rejected++;return;}throw new AssertionError(error);}throw new AssertionError("Unexpected acceptance");}
    static RegionOfflineBundlePrototype.Snapshot rawCapture(Path base,Object monitor,LongSupplier revision,BooleanSupplier cancel)throws IOException{return server?RegionOfflineBundlePrototype.captureServer(base,monitor,revision,cancel):RegionOfflineBundlePrototype.capture(base,monitor,revision,cancel);}
    static RegionOfflineBundlePrototype.Snapshot capture(Path base,Object monitor,AtomicLong revision)throws IOException{synchronized(monitor){return rawCapture(base,monitor,revision::get,()->false);}}
    static long temps(Path folder)throws IOException{try(var files=Files.list(folder)){return files.filter(p->p.getFileName().toString().startsWith(".region-stage-")).count();}}
    public static void main(String[] args)throws Exception {
        server=args.length==1&&args[0].equals("server");if(args.length>(server?1:0))throw new IllegalArgumentException("Unknown synthetic mode");
        Path parent=Path.of(server?"target/structure-server-bundle-tests":"target/structure-offline-bundle-tests");Files.createDirectories(parent);Path root=Files.createTempDirectory(parent,"synthetic-");
        try{run(root);}finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
    }
    static void run(Path root)throws Exception {
        Path base=root.resolve("world");long seed=server?123+Terrain.CURRENT_VERSION:123;
        if(server)Files.write(base,RegionServerImportCheck.fixture(9,Terrain.CURRENT_VERSION).bytes());
        else{var game=new LocalGame(base,seed);game.inventory.add(Blocks.STONE,5);game.health=7;game.save();}
        byte[] original=Files.readAllBytes(base);
        Object monitor=new Object();var revision=new AtomicLong();var storeRoot=root.resolve("destination");var store=new RegionGenerationPrototype(storeRoot);
        reject(IOException.class,()->rawCapture(base,monitor,revision::get,()->false));
        var snapshot=capture(base,monitor,revision);try(var stage=snapshot.stage(store,()->false)){reject(IOException.class,()->stage.commit(()->false));synchronized(monitor){stage.commit(()->false);}}
        var firstRestart=new RegionAuthorityPrototype(storeRoot,seed,Terrain.CURRENT_VERSION);
        require((server?firstRestart.serverMetadata().orElseThrow().players().get(0).health():firstRestart.metadata().orElseThrow().health())==7,"Base metadata lost");byte[] saved=Files.readAllBytes(storeRoot.resolve("current.region"));
        var expected=server?RegionServerImportPrototype.decode(original,()->false):RegionOfflineImportPrototype.decode(original,()->false);
        require(Arrays.equals(expected.encode(()->false),store.load(()->false).orElseThrow().worldLibrary().encode(()->false)),"Initial complete payload changed");
        RegionOfflineBundlePrototype.Snapshot wrongMode;synchronized(monitor){wrongMode=server?RegionOfflineBundlePrototype.capture(base,monitor,revision::get,()->false):RegionOfflineBundlePrototype.captureServer(base,monitor,revision::get,()->false);}
        reject(IOException.class,()->wrongMode.stage(store,()->false));
        require(Arrays.equals(saved,Files.readAllBytes(storeRoot.resolve("current.region")))&&temps(storeRoot)==0,"Wrong codec mutated destination");
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
        new CitySimulation(GameConfig.cityGame(),ground,new Terrain(seed),null).save(city);byte[] cityBytes=Files.readAllBytes(city),historyBytes=Files.readAllBytes(history);
        var full=capture(base,monitor,revision);try(var stage=full.stage(store,()->false)){synchronized(monitor){stage.commit(()->false);}}
        var restarted=new RegionAuthorityPrototype(storeRoot,seed,Terrain.CURRENT_VERSION);require(Arrays.equals(cityBytes,restarted.sidecars().orElseThrow().city())&&Arrays.equals(historyBytes,restarted.sidecars().orElseThrow().history().orElseThrow()),"Full bundle lost sidecars");
        saved=Files.readAllBytes(storeRoot.resolve("current.region"));
        for(String changed:List.of("city","history","removed-city","removed-history")){
            try(var stage=capture(base,monitor,revision).stage(store,()->false)){
                switch(changed){case "city" -> {var bytes=cityBytes.clone();bytes[bytes.length-1]^=1;Files.write(city,bytes);}case "history" -> {var bytes=historyBytes.clone();bytes[bytes.length-1]^=1;Files.write(history,bytes);}case "removed-city" -> Files.delete(city);case "removed-history" -> Files.delete(history);}
                synchronized(monitor){reject(IOException.class,()->stage.commit(()->false));}
            }finally{Files.write(city,cityBytes);Files.write(history,historyBytes);}
            require(Arrays.equals(saved,Files.readAllBytes(storeRoot.resolve("current.region")))&&temps(storeRoot)==0,"Sidecar change published/leaked stage");
        }
        try(var stage=capture(base,monitor,revision).stage(store,()->false)){synchronized(monitor){reject(CancellationException.class,()->stage.commit(()->true));}}
        synchronized(monitor){reject(CancellationException.class,()->rawCapture(base,monitor,revision::get,()->true));reject(IOException.class,()->rawCapture(base,monitor,()->-1,()->false));}
        // Missing/non-regular/oversize source files reject before decoding or publication.
        Files.delete(city);reject(IOException.class,()->capture(base,monitor,revision));Files.write(city,cityBytes);
        Files.write(history,new byte[0]);reject(IOException.class,()->capture(base,monitor,revision));Files.write(history,historyBytes);
        Path bad=root.resolve("bad");Files.createDirectory(bad);reject(IOException.class,()->capture(bad,monitor,revision));Files.delete(bad);
        try(var file=FileChannel.open(bad,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){file.position(RegionOfflineImportPrototype.MAX_BYTES);file.write(java.nio.ByteBuffer.wrap(new byte[]{0}));}reject(IOException.class,()->capture(bad,monitor,revision));Files.delete(bad);
        boolean symlinkChecked=false;
        try{Files.createSymbolicLink(bad,base);symlinkChecked=true;reject(IOException.class,()->capture(bad,monitor,revision));}
        catch(UnsupportedOperationException|FileSystemException unavailable){/* Platform cannot create this synthetic symlink; report it explicitly. */}
        finally{Files.deleteIfExists(bad);}
        var cancelled=new AtomicInteger();synchronized(monitor){reject(CancellationException.class,()->rawCapture(base,monitor,revision::get,()->cancelled.incrementAndGet()>10));}
        var revisionCalls=new AtomicInteger();synchronized(monitor){reject(IOException.class,()->rawCapture(base,monitor,()->revisionCalls.incrementAndGet()>=3?1:0,()->false));}
        reject(CancellationException.class,()->snapshot.stage(store,()->true));
        Thread.currentThread().interrupt();try{synchronized(monitor){reject(CancellationException.class,()->rawCapture(base,monitor,revision::get,()->false));}}finally{Thread.interrupted();}
        var stable=capture(base,monitor,revision);try(var first=stable.stage(store,()->false);var second=stable.stage(store,()->false)){
            synchronized(monitor){first.commit(()->false);reject(IOException.class,()->second.commit(()->false));reject(IOException.class,()->first.commit(()->false));}
        }
        try(var stage=capture(base,monitor,revision).stage(store,()->false)){synchronized(monitor){stage.commit(()->false);}}
        require(Arrays.equals(original,Files.readAllBytes(base))&&Arrays.equals(cityBytes,Files.readAllBytes(city))&&Arrays.equals(historyBytes,Files.readAllBytes(history))&&temps(storeRoot)==0,"Source preservation or cleanup failed");
        var fullExpected=expected.withSidecars(RegionSidecarsPrototype.capture(cityBytes,historyBytes,()->false),()->false);
        require(Arrays.equals(fullExpected.encode(()->false),store.load(()->false).orElseThrow().worldLibrary().encode(()->false)),"Final complete payload changed");
        System.out.printf("{\"status\":\"passed\",\"server_source\":%s,\"bundle_slots\":3,\"generation_restart\":true,\"rejection_cases\":%d,\"source_files_unchanged\":true,\"symlink_checked\":%s,\"production_integration\":false,\"qualification\":false}%n",server,rejected,symlinkChecked);
    }
}
