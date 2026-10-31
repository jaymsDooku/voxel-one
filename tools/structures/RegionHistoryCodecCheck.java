import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import java.util.zip.CRC32;

/** Independently constructed diagnostic wire and hostile-input checks. */
public final class RegionHistoryCodecCheck {
    private static final RegionHistoryPrototype.CodecBudget BUDGET=new RegionHistoryPrototype.CodecBudget(8*1024*1024,100_000,1024,131_072);
    private static int rejected;
    private interface Action{void run()throws Exception;}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void reject(Class<? extends Throwable> type,String message,Action action)throws Exception {
        try{action.run();}catch(Throwable failure){
            if(type.isInstance(failure)&&(message==null||String.valueOf(failure.getMessage()).contains(message))){rejected++;return;}
            throw new AssertionError("Expected "+type.getSimpleName()+" / "+message,failure);
        }
        throw new AssertionError("Unexpected acceptance: "+message);
    }
    private static Protocol.CellState cell(int x) {
        var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        return new Protocol.CellState(x,128,0,tree.freeze());
    }
    private static int index(Protocol.CellState cell){return (Math.floorMod(cell.y(),16)*16+Math.floorMod(cell.z(),16))*16+Math.floorMod(cell.x(),16);}
    private static byte[] crc(byte[] bytes) {
        var crc=new CRC32();crc.update(bytes,0,bytes.length-4);ByteBuffer.wrap(bytes).putInt(bytes.length-4,(int)crc.getValue());return bytes;
    }
    private static byte[] changed(byte[] source,int offset,int value) {
        var bytes=source.clone();ByteBuffer.wrap(bytes).putInt(offset,value);return crc(bytes);
    }
    private static byte[] changedShort(byte[] source,int offset,int value) {
        var bytes=source.clone();ByteBuffer.wrap(bytes).putShort(offset,(short)value);return crc(bytes);
    }
    private static byte[] changedByte(byte[] source,int offset,int value) {
        var bytes=source.clone();bytes[offset]=(byte)value;return crc(bytes);
    }
    /** Golden writer deliberately does not call the prototype encoder. */
    private static byte[] wire(List<List<Protocol.CellState>> regions)throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeInt(0x56524850);out.writeInt(1);out.writeLong(123);out.writeInt(Terrain.CURRENT_VERSION);out.writeInt(regions.size());
        for(var cells:regions) {
            var first=cells.get(0);out.writeInt(Math.floorDiv(first.x(),16));out.writeInt(Math.floorDiv(first.y(),16));out.writeInt(Math.floorDiv(first.z(),16));out.writeShort(cells.size());
            for(var cell:cells){out.writeShort(index(cell));cell.write(out);}
        }
        out.writeInt(0);return crc(bytes.toByteArray());
    }
    private static RegionHistoryPrototype decode(byte[] bytes)throws IOException{return RegionHistoryPrototype.decode(bytes,BUDGET,()->false);}
    private static void bad(byte[] bytes,String message)throws Exception{reject(IOException.class,message,()->decode(bytes));}
    public static void main(String[] args)throws Exception {
        var valid=wire(List.of(List.of(cell(0)),List.of(cell(16))));
        var prototype=decode(valid);
        require(Arrays.equals(valid,prototype.encode(BUDGET,()->false)),"Golden wire differs");
        bad(changed(valid,0,0),"format/version");bad(changed(valid,4,2),"format/version");
        bad(changed(valid,16,0),"Terrain version");bad(changed(valid,20,-1),"Region budget");bad(changed(valid,20,1025),"Region budget");
        bad(changed(valid,24,Integer.MAX_VALUE),"Invalid/duplicate region");
        bad(changed(valid,58,0),"Invalid/duplicate region");
        bad(changedShort(valid,36,0),"Cell budget");bad(changedShort(valid,36,4097),"Cell budget");
        bad(changedShort(valid,38,4096),"cell order");bad(changedShort(valid,38,1),"outside region");
        bad(changed(valid,40,16),"outside region");bad(changed(valid,44,Protocol.MAX_BUILD_Y+1),"position");
        bad(changedByte(valid,52,32),"resolution");bad(changedByte(valid,53,3),"branch");
        bad(changed(valid,54,0xff000000),"material");
        bad(wire(List.of(List.of(cell(0),cell(0)))),"cell order");
        bad(wire(List.of(List.of(cell(1),cell(0)))),"cell order");
        var model=new SparseVoxelOctree(16);model.fill(0,0,0,1,1,1,WorldVoxels.encode(1024));
        bad(wire(List.of(List.of(new Protocol.CellState(0,128,0,model.freeze())))),"material");
        var corrupt=valid.clone();corrupt[8]^=1;bad(corrupt,"CRC");
        var trailing=new byte[valid.length+1];System.arraycopy(valid,0,trailing,0,valid.length-4);bad(crc(trailing),"Trailing");
        for(int length:new int[]{0,1,27,28,40,53,57,58,61,valid.length-1})bad(Arrays.copyOf(valid,length),null);
        var bytesBudget=new RegionHistoryPrototype.CodecBudget(valid.length-1,2,2,2);
        reject(IOException.class,"byte budget",()->RegionHistoryPrototype.decode(valid,bytesBudget,()->false));
        reject(IOException.class,"byte budget",()->prototype.encode(bytesBudget,()->false));
        for(var budget:List.of(new RegionHistoryPrototype.CodecBudget(valid.length,1,2,2),new RegionHistoryPrototype.CodecBudget(valid.length,2,1,2),new RegionHistoryPrototype.CodecBudget(valid.length,2,2,1))) {
            reject(IOException.class,null,()->RegionHistoryPrototype.decode(valid,budget,()->false));
            reject(IOException.class,null,()->prototype.encode(budget,()->false));
        }
        var exact=new RegionHistoryPrototype.CodecBudget(valid.length,2,2,2);
        require(Arrays.equals(valid,decode(valid).encode(exact,()->false)),"Exact budget rejected");
        var empty=RegionHistoryPrototype.migrate(123,Terrain.CURRENT_VERSION,List.of(),new RegionHistoryPrototype.Budget(1,1,1),()->false);
        var emptyBudget=new RegionHistoryPrototype.CodecBudget(28,0,0,0);
        require(RegionHistoryPrototype.decode(empty.encode(emptyBudget,()->false),emptyBudget,()->false).regions().isEmpty(),"Empty wire changed");
        reject(IllegalArgumentException.class,null,()->new RegionHistoryPrototype.CodecBudget(8*1024*1024+1,1,1,1));
        reject(CancellationException.class,null,()->RegionHistoryPrototype.decode(valid,BUDGET,()->true));
        reject(CancellationException.class,null,()->prototype.encode(BUDGET,()->true));
        var checks=new AtomicInteger();decode(valid);
        RegionHistoryPrototype.decode(valid,BUDGET,()->{checks.incrementAndGet();return false;});
        int finalCheck=checks.get();checks.set(0);var published=new AtomicReference<RegionHistoryPrototype>();
        reject(CancellationException.class,null,()->published.set(RegionHistoryPrototype.decode(valid,BUDGET,()->checks.incrementAndGet()>=finalCheck)));
        require(published.get()==null,"Cancelled decode published a result");
        checks.set(0);reject(CancellationException.class,null,()->RegionHistoryPrototype.decode(valid,BUDGET,()->checks.incrementAndGet()>finalCheck/2));
        checks.set(0);prototype.encode(BUDGET,()->{checks.incrementAndGet();return false;});int encodeChecks=checks.get();
        checks.set(0);var encodedResult=new AtomicReference<byte[]>();
        reject(CancellationException.class,null,()->encodedResult.set(prototype.encode(BUDGET,()->checks.incrementAndGet()>=encodeChecks)));
        require(encodedResult.get()==null,"Cancelled encode published bytes");
        checks.set(0);reject(CancellationException.class,null,()->prototype.encode(BUDGET,()->checks.incrementAndGet()>encodeChecks/2));
        Thread.currentThread().interrupt();
        try{reject(CancellationException.class,null,()->decode(valid));require(Thread.currentThread().isInterrupted(),"Interrupt cleared");}
        finally{Thread.interrupted();}
        require(Arrays.equals(valid,decode(valid).encode(BUDGET,()->false)),"Retry after cancellation changed bytes");
        System.out.printf("{\"status\":\"passed\",\"golden_bytes\":%d,\"rejection_cases\":%d,\"qualification\":false}%n",valid.length,rejected);
    }
}
