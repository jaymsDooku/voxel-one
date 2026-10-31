import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.io.*;
import java.util.*;
import java.nio.file.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual byte-stream API checks. No live world or file is published by decode. */
public final class RegionHistoryStreamCheck {
    static final RegionHistoryPrototype.CodecBudget BUDGET=new RegionHistoryPrototype.CodecBudget(8*1024*1024,100_000,1024,131_072);
    static int rejects;
    interface Action{void run()throws Exception;}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void reject(Class<? extends Throwable> type,Action action)throws Exception{try{action.run();}catch(Throwable e){if(type.isInstance(e)){rejects++;return;}throw e;}throw new AssertionError("Unexpected stream acceptance");}
    static final class Input extends ByteArrayInputStream {
        boolean closed;int consumed;final int maximum;
        Input(byte[] bytes,int maximum){super(bytes);this.maximum=maximum;}
        @Override public synchronized int read(){int value=super.read();if(value!=-1)consumed++;return value;}
        @Override public synchronized int read(byte[] b,int off,int len){int n=super.read(b,off,Math.min(len,maximum));if(n>0)consumed+=n;return n;}
        @Override public void close(){closed=true;}
    }
    static final class Output extends ByteArrayOutputStream {
        boolean closed;@Override public void close(){closed=true;}
    }
    public static void main(String[] args)throws Exception {
        var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));tree.fill(0,0,0,1,1,1,WorldVoxels.encode(Blocks.GLASS));
        var cells=List.of(new Protocol.CellState(-1,128,-1,tree.freeze()),new Protocol.CellState(17,128,0,new SparseVoxelOctree(16).freeze()));
        var original=RegionHistoryPrototype.fromCells(126,Terrain.CURRENT_VERSION,cells,BUDGET,()->false);byte[] golden=original.encode(BUDGET,()->false);
        var output=new Output();original.encode(output,BUDGET,()->false);require(!output.closed&&Arrays.equals(golden,output.toByteArray()),"Stream writer changed bytes/ownership");output.write(42);require(output.size()==golden.length+1,"Writer unusable after encode");
        var exact=new RegionHistoryPrototype.CodecBudget(golden.length,2,2,131_072);
        for(int chunk:new int[]{1,3,65536}) {
            var input=new Input(golden,chunk);var restored=RegionHistoryPrototype.decode(input,exact,()->false);
            require(!input.closed&&input.consumed==golden.length&&Arrays.equals(golden,restored.encode(exact,()->false)),"Short read/ownership mismatch");
            require(restored.replay().cell(-1,128,-1).sameValues(tree)&&restored.replay().cell(17,128,0).occupied()==0,"Geometry/explicit air lost");
        }
        for(int length:new int[]{0,1,23,24,golden.length-4,golden.length-1}) {
            var input=new Input(Arrays.copyOf(golden,length),3);reject(IOException.class,()->RegionHistoryPrototype.decode(input,BUDGET,()->false));require(!input.closed,"Truncation closed caller");
        }
        var trailing=Arrays.copyOf(golden,golden.length+100);var over=new Input(trailing,65536);
        reject(IOException.class,()->RegionHistoryPrototype.decode(over,exact,()->false));require(over.consumed==golden.length+1&&!over.closed,"Budget read beyond one probe");
        var roomy=new Input(trailing,3);reject(IOException.class,()->RegionHistoryPrototype.decode(roomy,BUDGET,()->false));require(roomy.consumed==golden.length+1,"Trailing decoder drained caller");
        var corrupt=golden.clone();corrupt[corrupt.length-1]^=1;var bad=new Input(corrupt,1);reject(IOException.class,()->RegionHistoryPrototype.decode(bad,BUDGET,()->false));require(!bad.closed,"CRC failure closed caller");
        var small=new RegionHistoryPrototype.CodecBudget(golden.length-1,2,2,131_072);var capped=new Output();reject(IOException.class,()->original.encode(capped,small,()->false));require(capped.size()<=small.bytes()&&!capped.closed,"Writer exceeded budget/closed caller");
        var cancelled=new Input(golden,3);reject(CancellationException.class,()->RegionHistoryPrototype.decode(cancelled,BUDGET,()->true));require(cancelled.consumed==0&&!cancelled.closed,"Precancel consumed caller");
        var count=new AtomicInteger();var mid=new Input(golden,3);reject(CancellationException.class,()->RegionHistoryPrototype.decode(mid,BUDGET,()->count.incrementAndGet()>15));require(mid.consumed<golden.length&&!mid.closed,"Midcancel drained caller");
        count.set(0);var partial=new Output();reject(CancellationException.class,()->original.encode(partial,BUDGET,()->count.incrementAndGet()>15));require(partial.size()<golden.length&&!partial.closed,"Writer cancellation ownership");
        var failing=new InputStream(){int n;public int read()throws IOException{if(n==golden.length/2)throw new IOException("synthetic input failure");return n<golden.length?golden[n++]&255:-1;}};
        reject(IOException.class,()->RegionHistoryPrototype.decode(failing,BUDGET,()->false));
        var zero=new InputStream(){public int read(){return 0;}public int read(byte[] b,int off,int length){return 0;}};
        reject(IOException.class,()->RegionHistoryPrototype.decode(zero,BUDGET,()->false));
        var failOut=new OutputStream(){public void write(int value)throws IOException{throw new IOException("synthetic output failure");}};reject(IOException.class,()->original.encode(failOut,BUDGET,()->false));
        require(Arrays.equals(golden,original.encode(exact,()->false)),"Failed stream altered source/retry");
        Path parent=Path.of("target/structure-region-stream-tests");Files.createDirectories(parent);Path file=Files.createTempFile(parent,"synthetic-",".region");
        try {
            try(var out=Files.newOutputStream(file)){original.encode(out,exact,()->false);}
            require(Arrays.equals(golden,Files.readAllBytes(file)),"File stream changed bytes");
            try(var in=Files.newInputStream(file)){require(RegionHistoryPrototype.decode(in,exact,()->false).replay().cell(-1,128,-1).sameValues(tree),"File restart changed geometry");}
        } finally {Files.delete(file);}

        System.out.println("{\"status\":\"passed\",\"rejection_cases\":"+rejects+",\"bytes\":"+golden.length+",\"short_reads\":true,\"byte_exact\":true,\"caller_streams_open\":true,\"cap_plus_one_probe\":true,\"geometry_and_air\":true,\"file_restart\":true,\"retry\":true,\"qualification\":false}");
    }
}
