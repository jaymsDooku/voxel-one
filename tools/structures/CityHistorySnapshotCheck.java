import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.CRC32;

/** Actual production history writer, synthetic city files and independent version-1 wire contract. */
public final class CityHistorySnapshotCheck {
    static int rejected;
    interface Action{void run()throws Exception;}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void reject(Class<? extends Throwable> type,Action action)throws Exception{try{action.run();}catch(Throwable error){if(type.isInstance(error)){rejected++;return;}throw new AssertionError(error);}throw new AssertionError("Unexpected acceptance");}
    static class Sink extends ByteArrayOutputStream {boolean closed;@Override public void close(){closed=true;}}
    static byte[] snapshot(CityHistory history,String fingerprint,int maximum)throws IOException{var out=new ByteArrayOutputStream();history.writeSnapshot(out,fingerprint,maximum);return out.toByteArray();}
    public static void main(String[] args)throws Exception {
        Path parent=Path.of("target/structure-history-snapshot-tests");Files.createDirectories(parent);Path root=Files.createTempDirectory(parent,"synthetic-");int populatedBytes;
        try{
            String hash="0".repeat(64);var empty=new CityHistory();var actual=snapshot(empty,hash,4096);var expected=new ByteArrayOutputStream();var out=new DataOutputStream(expected);
            out.writeInt(0x48495331);out.writeInt(1);out.writeUTF(hash);out.writeLong(empty.identity().getMostSignificantBits());out.writeLong(empty.identity().getLeastSignificantBits());out.writeLong(0);out.writeDouble(Double.NaN);out.writeDouble(Double.NaN);out.writeDouble(Double.NaN);out.writeBoolean(false);out.writeInt(0);out.writeInt(0);out.writeInt(0);var crc=new CRC32();crc.update(expected.toByteArray());out.writeLong(crc.getValue());require(Arrays.equals(actual,expected.toByteArray()),"Existing empty v1 wire contract changed");
            var ground=new CitySimulation.Ground(){public int type(int x,int y,int z){return y<=0?Blocks.STONE:0;}public void apply(List<Protocol.Edit> edits){}public boolean occupied(int x,int y,int z,int w,int d){return false;}};
            var sim=new CitySimulation(GameConfig.cityGame(),ground,new Terrain(123),null);sim.collectHistory();sim.advance(10);Path city=root.resolve("city");sim.save(city);byte[] cityBytes=Files.readAllBytes(city),saved=Files.readAllBytes(root.resolve("city.history"));String fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cityBytes));
            var history=sim.history;var identity=history.identity();long revision=history.revision();double latest=history.latest();var catalog=history.catalog();var frame=sim.frame();byte[] bytes=snapshot(history,fingerprint,(int)CityHistory.MAX_BYTES);populatedBytes=bytes.length;require(Arrays.equals(saved,bytes),"Stream differs from production file save");
            var exact=new Sink();history.writeSnapshot(exact,fingerprint,bytes.length);require(!exact.closed&&Arrays.equals(bytes,exact.toByteArray()),"Exact budget/output ownership changed");exact.write(7);require(exact.size()==bytes.length+1,"Caller output unusable after snapshot");
            for(int maximum:new int[]{0,1,bytes.length-1}){var shortSink=new Sink();reject(IOException.class,()->history.writeSnapshot(shortSink,fingerprint,maximum));require(shortSink.size()<=maximum&&!shortSink.closed,"Budget exceeded or caller stream closed");}
            for(int maximum:new int[]{-1,(int)CityHistory.MAX_BYTES+1})reject(IllegalArgumentException.class,()->history.writeSnapshot(new Sink(),fingerprint,maximum));
            for(String invalid:List.of("", "0".repeat(63),"g".repeat(64),"A".repeat(64))){var sink=new Sink();reject(IllegalArgumentException.class,()->history.writeSnapshot(sink,invalid,4096));require(sink.size()==0&&!sink.closed,"Invalid fingerprint wrote output");}
            var broken=new OutputStream(){int count;public void write(int value)throws IOException{if(count++==30)throw new IOException("Synthetic sink failure");}};reject(IOException.class,()->history.writeSnapshot(broken,fingerprint,(int)CityHistory.MAX_BYTES));
            require(identity.equals(history.identity())&&revision==history.revision()&&latest==history.latest()&&catalog.equals(history.catalog())&&frame.equals(sim.frame()),"Read-only snapshot mutated simulation/history");require(Arrays.equals(bytes,snapshot(history,fingerprint,bytes.length)),"Retry changed payload");
            Path copied=root.resolve("copy.history");Files.write(copied,bytes);var loaded=CityHistory.load(copied,city,sim.config(),sim.frame().elapsed());require(identity.equals(loaded.identity())&&revision==loaded.revision()&&latest==loaded.latest()&&catalog.equals(loaded.catalog())&&!catalog.isEmpty(),"Production loader rejected stream snapshot");
            reject(IOException.class,()->history.save(root.resolve("city.history"),root.resolve("absent-city")));require(Arrays.equals(saved,Files.readAllBytes(root.resolve("city.history")))&&!Files.exists(root.resolve("city.history.tmp")),"Failed file save replaced prior history or leaked temp");require(Arrays.equals(cityBytes,Files.readAllBytes(city)),"Snapshot changed city source");
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
        System.out.printf("{\"status\":\"passed\",\"existing_wire_version\":1,\"populated_bytes\":%d,\"rejection_cases\":%d,\"production_loader\":true,\"source_unchanged\":true,\"active_city_adapter\":false,\"qualification\":false}%n",populatedBytes,rejected);
    }
}
