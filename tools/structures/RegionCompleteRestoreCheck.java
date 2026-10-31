import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual generated city/history plus private offline/server envelopes; detached restore only. */
public final class RegionCompleteRestoreCheck {
    static int rejected;
    interface Action{void run()throws Exception;}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void reject(Class<? extends Throwable> type,Action action)throws Exception{try{action.run();}catch(Throwable error){if(type.isInstance(error)){rejected++;return;}throw new AssertionError(error);}throw new AssertionError("Unexpected acceptance");}
    static byte[] city(CityFrame frame,int version)throws IOException{var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(0x43495430+version);frame.write(out,version);return bytes.toByteArray();}
    static byte[] checksum(byte[] bytes){var crc=new java.util.zip.CRC32();crc.update(bytes,0,bytes.length-8);ByteBuffer.wrap(bytes).putLong(bytes.length-8,crc.getValue());return bytes;}
    static byte[] patchHistory(byte[] bytes,int offset,int value){var copy=bytes.clone();ByteBuffer.wrap(copy).putInt(offset,value);return checksum(copy);}
    public static void main(String[] args)throws Exception {
        Path parent=Path.of("target/structure-complete-restore-tests");Files.createDirectories(parent);Path root=Files.createTempDirectory(parent,"synthetic-");
        try{
            var ground=new CitySimulation.Ground(){public int type(int x,int y,int z){return y<=0?Blocks.STONE:0;}public void apply(List<Protocol.Edit> edits){}public boolean occupied(int x,int y,int z,int w,int d){return false;}};
            var simulation=new CitySimulation(GameConfig.cityGame(),ground,new Terrain(126),null);simulation.collectHistory();simulation.advance(10);var frame=simulation.frame();var identity=simulation.history.identity();long revision=simulation.history.revision();double latest=simulation.history.latest();
            var sidecars=RegionCityOwnerPrototype.capture(simulation,()->false).sidecars();byte[] original=sidecars.encode(()->false),city=sidecars.city(),history=sidecars.history().orElseThrow();
            var restored=RegionCityRestorePrototype.decode(sidecars,()->false);require(frame.equals(restored.frame())&&restored.history().orElseThrow().identity().equals(identity)&&restored.history().orElseThrow().revision()==revision&&restored.history().orElseThrow().catalog().equals(simulation.history.catalog())&&restored.history().orElseThrow().latest()==latest,"Detached city/history changed");
            var running=new CitySimulation(restored.frame().config(),ground,new Terrain(126),restored.frame());running.history=restored.history().orElseThrow();require(running.frame().config().equals(frame.config())&&running.frame().elapsed()==frame.elapsed()&&new HashSet<>(running.frame().roads()).equals(new HashSet<>(frame.roads()))&&running.frame().citizens().size()==frame.citizens().size()&&running.frame().buildings().size()==frame.buildings().size(),"Detached simulation did not restore core state");running.advance(1);require(running.frame().elapsed()>frame.elapsed(),"Detached simulation did not advance");require(simulation.frame().equals(frame)&&simulation.history.latest()==latest,"Detached simulation aliased source");
            var noHistory=RegionCityRestorePrototype.decode(RegionSidecarsPrototype.capture(city,null,()->false),()->false);require(noHistory.frame().equals(frame)&&noHistory.history().isEmpty(),"Missing legacy history invented data");
            for(int version=1;version<=17;version++){
                byte[] old=city(CityFrame.empty(GameConfig.sandbox()),version);var decoded=RegionCityRestorePrototype.decode(RegionSidecarsPrototype.capture(old,null,()->false),()->false);require(decoded.frame().config().equals(GameConfig.sandbox())&&decoded.frame().elapsed()==0&&decoded.frame().roads().isEmpty()&&decoded.frame().buildings().isEmpty()&&decoded.frame().citizens().isEmpty()&&decoded.history().isEmpty(),"Legacy city empty-state contract changed");
            }
            var models=new ModelLibrary();var voxels=new SparseVoxelOctree(8);voxels.set(1,2,3,0xff112233);var definition=new ModelDefinition("synthetic",voxels);models.accept(new ModelLibrary.Entry(42,"fixture",definition));models.accept(new ModelLibrary.Entry(75,"unused",definition));models.acceptWorldEntry(new ModelLibrary.Entry(4096,"placed",definition));
            var physical=new WorldVoxels(new Terrain(126));physical.apply(new Protocol.Edit(0,128,0,4096));physical.apply(new Protocol.Edit(1,128,0,Blocks.piece(Blocks.GLASS,1),1,1,0,1));physical.apply(new Protocol.Edit(2,Terrain.MIN_Y,0,0));
            var cells=new ArrayList<Protocol.CellState>();for(int[] p:new int[][]{{0,128,0},{1,128,0},{2,Terrain.MIN_Y,0}})cells.add(new Protocol.CellState(p[0],p[1],p[2],physical.cell(p[0],p[1],p[2])));
            var geometry=RegionHistoryPrototype.fromCells(126,Terrain.CURRENT_VERSION,cells,new RegionHistoryPrototype.CodecBudget(8*1024*1024,100000,1024,131072),()->false);var inventory=new Inventory();inventory.add(42,3);var metadata=RegionOfflineMetadataPrototype.capture(inventory,7,List.of(new ItemDrop(17,Blocks.STONE,2,1,128,1)),models,()->false);
            var candidate=RegionWorldLibraryPrototype.capture(geometry,models,metadata,()->false).withSidecars(sidecars,()->false);var store=new RegionGenerationPrototype(root.resolve("generation"));try(var stage=store.stage(candidate,()->false)){stage.commit(()->false);}byte[] saved=Files.readAllBytes(root.resolve("generation/current.region"));
            reject(IOException.class,()->candidate.restoreComplete(GameConfig.sandbox(),()->false));
            require(candidate.restoreComplete(GameConfig.cityGame(),()->false).city().orElseThrow().frame().equals(frame),"Matching city mode rejected");
            var sandboxCandidate=RegionWorldLibraryPrototype.capture(geometry,models,metadata,()->false).withSidecars(RegionSidecarsPrototype.capture(city(CityFrame.empty(GameConfig.sandbox()),17),null,()->false),()->false);
            reject(IOException.class,()->sandboxCandidate.restoreComplete(GameConfig.cityGame(),()->false));
            require(!sandboxCandidate.restoreComplete(GameConfig.sandbox(),()->false).city().orElseThrow().frame().config().city(),"Matching sandbox mode rejected");
            require(RegionWorldLibraryPrototype.capture(geometry,models,metadata,()->false).restoreComplete(GameConfig.cityGame(),()->false).city().isEmpty(),"Legacy absent sidecar rejected");
            var complete=store.load(()->false).orElseThrow().worldLibrary().restoreComplete(()->false);require(complete.offline().orElseThrow().health()==7&&complete.offline().orElseThrow().inventory().countType(42)==3&&complete.offline().orElseThrow().drops().size()==1&&complete.server().isEmpty()&&complete.city().orElseThrow().frame().equals(frame)&&complete.models().get(75)!=null,"Complete offline state changed");for(var cell:cells)require(cell.tree().sameValues(complete.world().cell(cell.x(),cell.y(),cell.z())),"Complete geometry changed");
            complete.models().accept(new ModelLibrary.Entry(76,"restored only",definition));require(candidate.restoreComplete(()->false).models().get(76)==null,"Restored model library aliased snapshot");
            var server=RegionServerImportPrototype.decode(RegionServerImportCheck.fixture(9,Terrain.CURRENT_VERSION).bytes(),()->false).withSidecars(sidecars,()->false).restoreComplete(()->false);require(server.offline().isEmpty()&&server.server().orElseThrow().players().size()==2&&server.server().orElseThrow().drops().size()==1&&server.city().orElseThrow().history().orElseThrow().identity().equals(identity),"Complete server state changed");
            require(RegionWorldLibraryPrototype.capture(geometry,models,metadata,()->false).restoreComplete(()->false).city().isEmpty(),"Missing city invented data");
            var cityInput=new ByteArrayInputStream(city);var fields=new DataInputStream(cityInput);fields.readInt();GameConfig.read(fields,17);int clockOffset=city.length-cityInput.available();fields.readDouble();int roadOffset=city.length-cityInput.available();
            for(int length:new int[]{4,city.length/2,city.length-1,city.length+1})reject(IOException.class,()->RegionCityRestorePrototype.decode(RegionSidecarsPrototype.capture(Arrays.copyOf(city,length),null,()->false),()->false));
            for(int count:new int[]{-1,8193}){var bad=city.clone();ByteBuffer.wrap(bad).putInt(roadOffset,count);reject(IOException.class,()->RegionCityRestorePrototype.decode(RegionSidecarsPrototype.capture(bad,null,()->false),()->false));}
            var badClock=city.clone();ByteBuffer.wrap(badClock).putDouble(clockOffset,Double.NaN);reject(IOException.class,()->RegionCityRestorePrototype.decode(RegionSidecarsPrototype.capture(badClock,null,()->false),()->false));
            var historyInput=new ByteArrayInputStream(history);var h=new DataInputStream(historyInput);h.readInt();h.readInt();h.readUTF();h.readLong();h.readLong();h.readLong();int historyClock=history.length-historyInput.available();h.readDouble();h.readDouble();h.readDouble();h.readBoolean();int epochOffset=history.length-historyInput.available();
            for(int count:new int[]{-1,65}){var bad=patchHistory(history,epochOffset,count);var framed=RegionSidecarsPrototype.capture(city,bad,()->false);reject(IOException.class,()->RegionCityRestorePrototype.decode(framed,()->false));}
            var badTime=history.clone();ByteBuffer.wrap(badTime).putDouble(historyClock,Double.NaN);checksum(badTime);var badHistory=RegionSidecarsPrototype.capture(city,badTime,()->false);reject(IOException.class,()->RegionCityRestorePrototype.decode(badHistory,()->false));
            var badCandidate=RegionWorldLibraryPrototype.capture(geometry,models,metadata,()->false).withSidecars(badHistory,()->false);reject(IOException.class,()->badCandidate.restoreComplete(()->false));
            byte[] sandbox=city(CityFrame.empty(GameConfig.sandbox()),17);var out=new ByteArrayOutputStream();simulation.history.writeSnapshot(out,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sandbox)),4*1024*1024);var sandboxHistory=RegionSidecarsPrototype.capture(sandbox,out.toByteArray(),()->false);reject(IOException.class,()->RegionCityRestorePrototype.decode(sandboxHistory,()->false));
            reject(CancellationException.class,()->candidate.restoreComplete(()->true));var calls=new AtomicInteger();reject(CancellationException.class,()->RegionCityRestorePrototype.decode(sidecars,()->calls.incrementAndGet()>100));Thread.currentThread().interrupt();try{reject(CancellationException.class,()->candidate.restoreComplete(()->false));}finally{Thread.interrupted();}
            require(candidate.restoreComplete(()->false).city().orElseThrow().history().orElseThrow().identity().equals(identity),"Retry failed");require(Arrays.equals(original,sidecars.encode(()->false))&&Arrays.equals(saved,Files.readAllBytes(root.resolve("generation/current.region"))),"Restore changed source bytes");
            require(frame.equals(simulation.frame())&&identity.equals(simulation.history.identity())&&revision==simulation.history.revision()&&latest==simulation.history.latest(),"Restore mutated source simulation");
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
        System.out.printf("{\"status\":\"passed\",\"game_mode_guard\":true,\"legacy_city_formats\":17,\"offline_and_server_metadata\":true,\"rejection_cases\":%d,\"source_unchanged\":true,\"live_region_backend\":false,\"qualification\":false}%n",rejected);
    }
}
