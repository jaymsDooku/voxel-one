import dev.jayms.net.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

/** Actual history snapshot/read/write/iteration diagnostic; not game qualification. */
public final class HistoryRootProbe {
    private static volatile Object held;
    private static volatile long consumed;
    private static String stats(double[] v){var s=v.clone();Arrays.sort(s);return String.format(Locale.ROOT,"{\"median\":%.6f,\"p95\":%.6f,\"worst\":%.6f,\"samples\":%s}",(s[9]+s[10])/2,s[18],s[19],Arrays.toString(v));}
    public static void main(String[] args)throws Exception {
        if(args.length!=1||Files.exists(Path.of(args[0])))throw new IllegalArgumentException("Fresh result path required");
        var source=new WorldVoxels.History();var keys=new String[100000];long expected=0;
        for(int i=0;i<keys.length;i++){var e=new Protocol.Edit(i%1024-512,180,i/1024-50,Blocks.STONE);keys[i]=e.key();source.put(keys[i],e);expected+=e.x();}
        var queries=new String[65536];var random=new Random(8927);long lookupExpected=0;
        for(int i=0;i<queries.length;i++){int k=random.nextInt(keys.length);queries[i]=keys[k];lookupExpected+=source.get(queries[i]).x();}
        var first=source.get(keys[0]);var replacement=new Protocol.Edit(first.x(),first.y(),first.z(),Blocks.BRICKS);
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        double[][] times=new double[4][20],bytes=new double[4][20];
        for(int run=-3;run<20;run++) {
            long before=bean.getThreadAllocatedBytes(thread),begin=System.nanoTime();var copy=WorldVoxels.History.copyOf(source);
            double ms=(System.nanoTime()-begin)/1e6;long allocated=bean.getThreadAllocatedBytes(thread)-before;held=copy;
            if(run>=0){times[0][run]=ms;bytes[0][run]=allocated;}
            before=bean.getThreadAllocatedBytes(thread);begin=System.nanoTime();copy.put(keys[0],replacement);
            ms=(System.nanoTime()-begin)/1e6;allocated=bean.getThreadAllocatedBytes(thread)-before;
            if(run>=0){times[1][run]=ms;bytes[1][run]=allocated;}
            if(source.get(keys[0]).type()!=Blocks.STONE||copy.get(keys[0]).type()!=Blocks.BRICKS||!copy.keySet().iterator().next().equals(keys[0]))throw new AssertionError("Copy/order isolation");
            before=bean.getThreadAllocatedBytes(thread);begin=System.nanoTime();long sum=0;
            for(var key:queries)sum+=source.get(key).x();
            ms=(System.nanoTime()-begin)/1e6;allocated=bean.getThreadAllocatedBytes(thread)-before;
            if(sum!=lookupExpected)throw new AssertionError("Key lookup");consumed^=sum;if(run>=0){times[2][run]=ms;bytes[2][run]=allocated;}
            before=bean.getThreadAllocatedBytes(thread);begin=System.nanoTime();sum=0;int index=0;
            for(var e:source.entrySet()){if(!e.getKey().equals(keys[index++]))throw new AssertionError("Replay order");sum+=e.getValue().x();}
            ms=(System.nanoTime()-begin)/1e6;allocated=bean.getThreadAllocatedBytes(thread)-before;
            if(sum!=expected||index!=keys.length)throw new AssertionError("Iteration");consumed^=sum;if(run>=0){times[3][run]=ms;bytes[3][run]=allocated;}
        }
        var frozen=WorldVoxels.History.copyOf(source);var cursor=frozen.cellCopyCursor();int cells=0;
        source.clear();while(true){var batch=cursor.nextBatch();if(batch.isEmpty())break;if(batch.size()>1024)throw new AssertionError("Cursor budget");cells+=batch.size();}
        if(cells!=100000||frozen.size()!=100000||!source.isEmpty())throw new AssertionError("Cell index isolation");
        String[] names={"snapshot","first_edit","lookup_65536","iterate_100000"};var json=new StringBuilder("{\"status\":\"passed\",\"records\":100000,\"cells\":100000,\"warmups\":3,\"runs\":20,\"qualification\":false");
        for(int i=0;i<4;i++)json.append(",\"").append(names[i]).append("_ms\":").append(stats(times[i])).append(",\"").append(names[i]).append("_allocated_bytes\":").append(stats(bytes[i]));
        json.append(",\"copy_order_cell_cursor_isolation\":true}");Files.writeString(Path.of(args[0]),json+"\n");System.out.println(json);
    }
}
