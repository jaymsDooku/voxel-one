import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

/** Only generated synthetic city/history files; exact-byte preservation and private store checks. */
public final class RegionSidecarsCheck {
    static int rejected;
    interface Action{void run()throws Exception;}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void reject(Class<? extends Throwable> type,Action action)throws Exception{try{action.run();}catch(Throwable error){if(type.isInstance(error)){rejected++;return;}throw new AssertionError(error);}throw new AssertionError("Unexpected acceptance");}
    public static void main(String[] args)throws Exception {
        Path parent=Path.of("target/structure-sidecar-tests");Files.createDirectories(parent);Path root=Files.createTempDirectory(parent,"synthetic-");
        try{run(root);}finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
    }
    static void run(Path root)throws Exception {
        var ground=new CitySimulation.Ground(){public int type(int x,int y,int z){return y<=0?Blocks.STONE:0;}public void apply(List<Protocol.Edit> edits){}public boolean occupied(int x,int y,int z,int w,int d){return false;}};
        var sim=new CitySimulation(GameConfig.cityGame(),ground,new Terrain(123),null);Path cityFile=root.resolve("source.city");sim.save(cityFile);
        Path historyFile=root.resolve("source.city.history");byte[] city=Files.readAllBytes(cityFile),history=Files.readAllBytes(historyFile);
        var sidecars=RegionSidecarsPrototype.capture(city,history,()->false);var wire=sidecars.encode(()->false);
        var decoded=RegionSidecarsPrototype.decode(wire,()->false);require(Arrays.equals(wire,decoded.encode(()->false)),"Sidecar envelope changed");
        require(Arrays.equals(city,decoded.city())&&Arrays.equals(history,decoded.history().orElseThrow()),"Sidecar bytes changed");
        var returned=decoded.city();returned[0]^=1;var returnedHistory=decoded.history().orElseThrow();returnedHistory[0]^=1;require(Arrays.equals(city,decoded.city())&&Arrays.equals(history,decoded.history().orElseThrow()),"Getter mutated snapshot");
        var sourceCopy=city.clone();var historyCopy=history.clone();var frozen=RegionSidecarsPrototype.capture(sourceCopy,historyCopy,()->false);sourceCopy[0]^=1;historyCopy[0]^=1;require(Arrays.equals(city,frozen.city())&&Arrays.equals(history,frozen.history().orElseThrow()),"Source mutated snapshot");
        var onlyCity=RegionSidecarsPrototype.capture(city,null,()->false);require(RegionSidecarsPrototype.decode(onlyCity.encode(()->false),()->false).history().isEmpty(),"Missing history invented");
        var base=root.resolve("source.world");new LocalGame(base,123).save();byte[] baseBytes=Files.readAllBytes(base);
        var candidate=RegionOfflineImportPrototype.decode(baseBytes,decoded,()->false);var storeRoot=root.resolve("generation");var store=new RegionGenerationPrototype(storeRoot);
        try(var stage=store.stage(candidate,()->false)){stage.commit(()->false);}
        var authority=new RegionAuthorityPrototype(storeRoot,123,Terrain.CURRENT_VERSION);require(Arrays.equals(wire,authority.sidecars().orElseThrow().encode(()->false)),"Restart lost sidecars");
        authority.edit(new Protocol.Edit(0,128,0,Blocks.STONE));authority.commit(authority.models(),Map.of());
        var restarted=new RegionAuthorityPrototype(storeRoot,123,Terrain.CURRENT_VERSION);var persisted=restarted.sidecars().orElseThrow();
        require(Arrays.equals(wire,persisted.encode(()->false))&&restarted.cell(0,128,0).occupied()!=0,"Geometry save lost sidecars");
        Path restoredCity=root.resolve("restored.city"),restoredHistory=root.resolve("restored.city.history");Files.write(restoredCity,persisted.city());Files.write(restoredHistory,persisted.history().orElseThrow());
        var originalFrame=CitySimulation.load(cityFile);var frame=CitySimulation.load(restoredCity);require(originalFrame.equals(frame),"City loader state changed");
        var original=CityHistory.load(historyFile,cityFile,originalFrame.config(),originalFrame.elapsed());var restored=CityHistory.load(restoredHistory,restoredCity,frame.config(),frame.elapsed());
        require(!original.catalog().isEmpty()&&original.catalog().equals(restored.catalog())&&original.identity().equals(restored.identity())&&original.revision()==restored.revision()&&original.latest()==restored.latest(),"Restored city history differs or was rejected");
        byte[] before=Files.readAllBytes(storeRoot.resolve("current.region"));var noSidecars=RegionOfflineImportPrototype.decode(baseBytes,()->false);
        reject(IOException.class,()->store.stage(noSidecars,()->false));require(Arrays.equals(before,Files.readAllBytes(storeRoot.resolve("current.region"))),"Omission changed generation");
        try(var stage=authority.stageCommit(authority.models(),Map.of(),authority.models().revision(),()->false)){authority.sidecars(onlyCity);synchronized(authority.lock()){reject(IOException.class,stage::apply);}}
        require(Arrays.equals(before,Files.readAllBytes(storeRoot.resolve("current.region")))&&authority.sidecars().orElseThrow().history().isEmpty(),"Stale sidecar stage changed disk or newer root");
        authority.commit(authority.models(),Map.of());require(new RegionAuthorityPrototype(storeRoot,123,Terrain.CURRENT_VERSION).sidecars().orElseThrow().history().isEmpty(),"Retry lost newer sidecar");
        var emptyGeometry=RegionHistoryPrototype.fromCells(123,Terrain.CURRENT_VERSION,List.of(),new RegionHistoryPrototype.CodecBudget(1024,0,0,0),()->false);
        var noMetadata=RegionWorldLibraryPrototype.capture(emptyGeometry,new dev.jayms.net.model.ModelLibrary(),()->false);
        reject(IOException.class,()->noMetadata.withSidecars(sidecars,()->false));
        // Prefix-only synthetic bytes exercise the transport cap; they are not a valid CityFrame fixture.
        var capacity=new byte[RegionSidecarsPrototype.MAX_CONTENT];ByteBuffer.wrap(capacity).putInt(0x43495441);
        var atLimit=RegionSidecarsPrototype.capture(capacity,null,()->false);byte[] limitWire=atLimit.encode(()->false);
        require(limitWire.length==RegionSidecarsPrototype.MAX_BYTES&&RegionSidecarsPrototype.decode(limitWire,()->false).city().length==capacity.length,"Sidecar capacity boundary changed");
        var decodeCalls=new AtomicInteger();reject(CancellationException.class,()->RegionSidecarsPrototype.decode(limitWire,()->decodeCalls.incrementAndGet()>20));
        Thread.currentThread().interrupt();try{reject(CancellationException.class,()->sidecars.encode(()->false));}finally{Thread.interrupted();}
        var mismatch=city.clone();mismatch[mismatch.length-1]^=1;reject(IOException.class,()->RegionSidecarsPrototype.capture(mismatch,history,()->false));
        var broken=history.clone();broken[broken.length-9]^=1;reject(IOException.class,()->RegionSidecarsPrototype.capture(city,broken,()->false));
        reject(IOException.class,()->RegionSidecarsPrototype.capture(new byte[4],null,()->false));reject(IOException.class,()->RegionSidecarsPrototype.capture(city,new byte[10],()->false));
        reject(IOException.class,()->RegionSidecarsPrototype.capture(new byte[RegionSidecarsPrototype.MAX_CONTENT+1],null,()->false));reject(IOException.class,()->RegionSidecarsPrototype.capture(city,new byte[RegionSidecarsPrototype.MAX_CONTENT],()->false));
        for(int length:new int[]{0,23,wire.length-1,wire.length+1,RegionSidecarsPrototype.MAX_BYTES+1})reject(IOException.class,()->RegionSidecarsPrototype.decode(Arrays.copyOf(wire,length),()->false));
        var corrupt=wire.clone();corrupt[12]^=1;reject(IOException.class,()->RegionSidecarsPrototype.decode(corrupt,()->false));
        reject(CancellationException.class,()->RegionSidecarsPrototype.capture(city,history,()->true));reject(CancellationException.class,()->sidecars.encode(()->true));reject(CancellationException.class,()->RegionSidecarsPrototype.decode(wire,()->true));
        var calls=new AtomicInteger();RegionSidecarsPrototype.capture(city,history,()->{calls.incrementAndGet();return false;});int middle=calls.get()/2;calls.set(0);reject(CancellationException.class,()->RegionSidecarsPrototype.capture(city,history,()->calls.incrementAndGet()>middle));
        require(Arrays.equals(city,Files.readAllBytes(cityFile))&&Arrays.equals(history,Files.readAllBytes(historyFile))&&Arrays.equals(baseBytes,Files.readAllBytes(base)),"Migration modified source bundle");
        System.out.printf("{\"status\":\"passed\",\"city_bytes\":%d,\"history_bytes\":%d,\"rejection_cases\":%d,\"generation_restart\":true,\"production_integration\":false,\"semantic_city_validation\":false,\"qualification\":false}%n",city.length,history.length,rejected);
    }
}
