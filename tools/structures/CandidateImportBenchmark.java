import dev.jayms.net.structure.StructurePackage;
import java.io.*;
import java.lang.management.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;

/** Decode-only experiment. Requires separately compiled analysis caps; never creates a world. */
public final class CandidateImportBenchmark {
    record Fixture(int cells,int leaves,int models,int instances,int x,int y,int z) {}
    static final Map<String,Fixture> FIXTURES=Map.of(
            "city",new Fixture(978288,968048,256,10240,512,128,512),
            "maximum",new Fixture(4000000,7967232,512,16384,1024,256,1024));
    static void require(boolean valid){if(!valid)throw new AssertionError("Candidate verification failed");}
    static void verify(StructurePackage p,Fixture f) {
        require(p.cells.size()==f.cells&&p.leafCount==f.leaves&&p.models.size()==f.models&&p.instanceCount==f.instances);
        require(p.bounds.x()==f.x&&p.bounds.y()==f.y&&p.bounds.z()==f.z);
    }
    static long heap(){return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();}
    static long gcCount(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(x->Math.max(0,x.getCollectionCount())).sum();}
    static long gcTime(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(x->Math.max(0,x.getCollectionTime())).sum();}
    static long allocated() {
        var bean=ManagementFactory.getThreadMXBean();
        return bean instanceof com.sun.management.ThreadMXBean b&&b.isThreadAllocatedMemorySupported()?b.getThreadAllocatedBytes(Thread.currentThread().getId()):-1;
    }
    static long rssPeak()throws IOException {
        try(var lines=Files.lines(Path.of("/proc/self/status"))) {
            return lines.filter(x->x.startsWith("VmHWM:")).mapToLong(x->Long.parseLong(x.trim().split("\\s+")[1])*1024).findFirst().orElse(-1);
        }
    }
    static final class Peak implements AutoCloseable {
        final AtomicLong maximum=new AtomicLong(heap());
        final ScheduledExecutorService sampler=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"candidate-heap-sampler");t.setDaemon(true);return t;});
        Peak(){sampler.scheduleAtFixedRate(()->maximum.accumulateAndGet(heap(),Math::max),0,5,TimeUnit.MILLISECONDS);}
        long finish(){maximum.accumulateAndGet(heap(),Math::max);return maximum.get();}
        public void close(){sampler.shutdownNow();}
    }
    static String decode(byte[] bytes,Fixture f,int run)throws Exception {
        System.gc();Thread.sleep(100);
        try(var peak=new Peak()) {
            long count=gcCount(),time=gcTime(),alloc=allocated(),start=System.nanoTime();
            var asset=StructurePackage.decode(bytes);double elapsed=(System.nanoTime()-start)/1e6;
            long allocated=allocated(),used=peak.finish(),collections=gcCount()-count,millis=gcTime()-time;
            verify(asset,f);
            return String.format(Locale.ROOT,"{\"run\":%d,\"decode_ms\":%.6f,\"sampled_heap_peak_bytes\":%d,\"process_rss_high_water_bytes\":%d,\"main_thread_allocated_bytes\":%d,\"gc_collections\":%d,\"gc_ms\":%d}",run,elapsed,used,rssPeak(),alloc<0||allocated<0?-1:allocated-alloc,collections,millis);
        }
    }
    static String bodyHash(byte[] bytes)throws Exception {
        var hash=MessageDigest.getInstance("SHA-256");long total=0;
        try(var in=new GZIPInputStream(new ByteArrayInputStream(bytes),65536)) {
            var buffer=new byte[65536];for(int n;(n=in.read(buffer))!=-1;){total+=n;require(total<=StructurePackage.DECODED);hash.update(buffer,0,n);}
        }
        return HexFormat.of().formatHex(hash.digest());
    }
    static String roundtrip(byte[] input,Fixture f)throws Exception {
        System.gc();Thread.sleep(100);
        try(var peak=new Peak()) {
            var asset=StructurePackage.decode(input);verify(asset,f);
            long start=System.nanoTime();byte[] encoded=asset.encode();double export=(System.nanoTime()-start)/1e6;
            require(bodyHash(input).equals(bodyHash(encoded)));
            verify(StructurePackage.decode(encoded),f);
            return String.format(Locale.ROOT,"{\"export_ms\":%.6f,\"export_bytes\":%d,\"decoded_body_byte_exact\":true,\"second_decode_verified\":true,\"sampled_heap_peak_bytes\":%d,\"process_rss_high_water_bytes\":%d}",export,encoded.length,peak.finish(),rssPeak());
        }
    }
    static void edges(byte[] input)throws Exception {
        var cancelled=new AtomicBoolean();var progress=new AtomicInteger();boolean stopped=false;
        try{StructurePackage.decode(input,cancelled::get,p->{progress.set(p);if(p>=50)cancelled.set(true);});}
        catch(CancellationException expected){stopped=true;}
        require(stopped&&progress.get()<100);
        System.gc();byte[] broken=input.clone();broken[broken.length-8]^=1;progress.set(0);boolean rejected=false;
        try{StructurePackage.decode(broken,()->false,progress::set);}
        catch(IOException expected){rejected=true;}
        require(rejected&&progress.get()<100);
    }
    static int execute(String[] args)throws Exception {
        if(args.length!=5||!FIXTURES.containsKey(args[0])||!args[2].matches("[a-f0-9]{64}"))throw new IllegalArgumentException("fixture path trusted-sha runs output-directory");
        int runs=Integer.parseInt(args[3]);require(runs>=1&&runs<=20);
        require(StructurePackage.class.getField("MAX_LEAVES").getInt(null)==8388608);
        require(StructurePackage.class.getField("MAX_DEPENDENCY_QUADS").getLong(null)==2097152);
        require(StructurePackage.class.getField("MAX_INSTANCE_QUADS").getLong(null)==67108864);
        Path input=Path.of(args[1]),output=Path.of(args[4]);Files.createDirectories(output);
        long length=Files.size(input);require(length>0&&length<=StructurePackage.COMPRESSED);
        byte[] bytes=new byte[(int)length];try(var in=Files.newInputStream(input,LinkOption.NOFOLLOW_LINKS)){require(in.readNBytes(bytes,0,bytes.length)==bytes.length&&in.read()==-1);}
        require(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(args[2]));
        Fixture f=FIXTURES.get(args[0]);
        try(var log=Files.newBufferedWriter(output.resolve(args[0]+"-samples.jsonl"),StandardOpenOption.CREATE_NEW)) {
            for(int i=-1;i<runs;i++){String sample=decode(bytes,f,i);log.write(sample+"\n");log.flush();}
        }
        String roundtrip=roundtrip(bytes,f);edges(bytes);
        String result="{\"status\":\"passed\",\"fixture\":\""+args[0]+"\",\"runs\":"+runs+",\"warmups\":1,\"strict_crc_rejection\":true,\"mid_decode_cancellation\":true,\"roundtrip\":"+roundtrip+",\"production_admission\":false,\"qualification\":false}\n";
        Files.writeString(output.resolve(args[0]+"-result.json"),result,StandardOpenOption.CREATE_NEW);System.out.print(result);return 0;
    }
    public static void main(String[] args) {
        int code;try{code=execute(args);}catch(Throwable failed){System.out.println("{\"status\":\"failed\",\"type\":\""+failed.getClass().getSimpleName()+"\",\"production_admission\":false,\"qualification\":false}");code=1;}
        System.exit(code);
    }
}
