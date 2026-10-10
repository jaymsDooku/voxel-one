package dev.jayms.render;

import java.io.*;
import java.lang.management.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** Bounded primitive capture; disk writes and percentile sorting run on one export worker. */
public final class PerformanceRecorder implements AutoCloseable {
    public static final int MAX_SAMPLES=60_000;
    public enum Metric {
        FRAME_MS,UPDATE_CPU_MS,STREAM_CPU_MS,RENDER_CPU_MS,OVERLAY_CPU_MS,VIDEO_CAPTURE_CPU_MS,
        SNAPSHOT_CPU_MS,MESH_WORKER_MS,UPLOAD_CPU_MS,UPLOAD_BYTES,GPU_FRAME_EMA_MS,
        GPU_SHADOW_LAST_MS,GPU_PROBE_LAST_MS,GPU_TERRAIN_LAST_MS,GPU_WATER_LAST_MS,GPU_ATMOSPHERE_LAST_MS,GPU_POST_LAST_MS,
        DRAWS,TRIANGLES,VISIBLE_CHUNKS,OCCLUDED_CHUNKS,LOADED_CHUNKS,MESH_JOBS,MESH_WAITING,OLDEST_JOB_MS,
        DISTANT_TILES,HEAP_USED_BYTES,ESTIMATED_VRAM_BYTES,RENDER_SCALE,ALLOCATION_BYTES,GC_COUNT,GC_MS,VIDEO_ACTIVE
    }
    public enum Route {MANUAL,GROUND,FLIGHT,TELEPORT,ISOMETRIC,CITY,INTERIOR,FRACTIONAL}
    public record Metadata(String os,String architecture,String renderer,String vendor,String glVersion,String javaVersion,
                           String sourceRevision,int processors,long maxHeap,int width,int height,GraphicsProfile settings,Route route,String cache) {}
    public record Export(Path csv,Path summary,int samples) {}
    private final ExecutorService exporter=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"performance-export");t.setDaemon(true);return t;});
    private double[][] columns;
    private int count,warmup,seen;
    private Path directory;
    private Metadata metadata;
    private CompletableFuture<Export> pending;
    private long recorderNanos;
    private volatile String status="";
    public boolean active(){return columns!=null;}
    public int count(){return count;}
    public String status(){if(active())return seen<warmup?"Performance warm-up: "+seen+" / "+warmup+" frames (F9 stops)":"Performance recording: "+count+" frames (F9 stops)";if(pending!=null&&!pending.isDone())return "Exporting performance report...";return status;}
    public void start(Path directory,Metadata metadata,int warmupFrames){
        if(active())throw new IllegalStateException("Performance capture already running");
        if(pending!=null&&!pending.isDone())throw new IllegalStateException("Wait for the previous report export");
        if(warmupFrames<0||warmupFrames>10_000)throw new IllegalArgumentException("Warm-up frame count");
        this.directory=directory;this.metadata=metadata;warmup=warmupFrames;seen=count=0;recorderNanos=0;
        columns=new double[Metric.values().length][MAX_SAMPLES];status="";
    }
    /** Reused caller array is copied into primitive columns. GPU unavailable values stay NaN. */
    public void sample(double[] sample){
        if(!active())return;if(sample.length!=Metric.values().length)throw new IllegalArgumentException("Metric count");
        if(!Double.isFinite(sample[0])||sample[0]<=0)return;
        long start=System.nanoTime();
        if(seen++>=warmup){for(int i=0;i<sample.length;i++)columns[i][count]=sample[i];count++;}
        recorderNanos+=System.nanoTime()-start;
        if(count==MAX_SAMPLES)stop();
    }
    public CompletableFuture<Export> stop(){
        if(!active())return pending==null?CompletableFuture.completedFuture(null):pending;
        double[][] captured=columns;columns=null;int capturedCount=count,excluded=Math.min(seen,warmup);Metadata info=metadata;Path folder=directory;long overhead=recorderNanos;
        pending=CompletableFuture.supplyAsync(()->{try{return export(folder,info,captured,capturedCount,excluded,overhead);}catch(IOException e){throw new CompletionException(e);}},exporter);
        pending.whenComplete((result,error)->status=error==null?(result.samples==0?"Warm-up incomplete; no measured frames. Report: ":"Performance report saved: ")+result.summary.getFileName():"Performance report export failed; no game data changed.");
        return pending;
    }
    public static double percentile(double[] values,int count,double quantile){
        if(count==0)return Double.NaN;double[] sorted=Arrays.copyOf(values,count);Arrays.sort(sorted);return sorted[Math.max(0,Math.min(count-1,(int)Math.ceil(count*quantile)-1))];
    }
    /** 1% low FPS = 1000 / mean duration of the slowest ceil(1%) frames. */
    public static double onePercentLow(double[] values,int count){if(count==0)return Double.NaN;double[] sorted=Arrays.copyOf(values,count);Arrays.sort(sorted);int n=Math.max(1,(int)Math.ceil(count*.01));double sum=0;for(int i=count-n;i<count;i++)sum+=sorted[i];return 1000/(sum/n);}
    private static String quoted(String s){if(s==null)return "null";StringBuilder b=new StringBuilder("\"");for(char c:s.toCharArray()){if(c=='"'||c=='\\')b.append('\\').append(c);else if(c<32)b.append(' ');else b.append(c);}return b.append('"').toString();}
    private static String number(double v){return Double.isFinite(v)?Double.toString(v):"null";}
    private static Export export(Path folder,Metadata info,double[][] data,int count,int warmup,long overhead)throws IOException {
        Files.createDirectories(folder);String name="performance-"+DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now())+"-"+UUID.randomUUID().toString().substring(0,8);
        Path csv=folder.resolve(name+".csv"),json=folder.resolve(name+".json"),tmp=Files.createTempFile(folder,"performance-",".tmp");
        try{
            try(var writer=Files.newBufferedWriter(tmp)){writer.write("frame");for(Metric metric:Metric.values())writer.write(","+metric.name().toLowerCase(Locale.ROOT));writer.write('\n');
                for(int row=0;row<count;row++){writer.write(Integer.toString(row));for(double[] col:data){writer.write(',');if(Double.isFinite(col[row]))writer.write(Double.toString(col[row]));}writer.write('\n');}}
            atomicMove(tmp,csv);
            var b=new StringBuilder("{\n  \"schema\": 1,\n  \"samples\": ").append(count).append(",\n  \"warmupExcluded\": ").append(warmup)
                .append(",\n  \"p50FrameMs\": ").append(number(percentile(data[0],count,.50))).append(",\n  \"p95FrameMs\": ").append(number(percentile(data[0],count,.95))).append(",\n  \"p99FrameMs\": ").append(number(percentile(data[0],count,.99)))
                .append(",\n  \"onePercentLowFps\": ").append(number(onePercentLow(data[0],count))).append(",\n  \"recorderCpuTotalMs\": ").append(overhead/1e6)
                .append(",\n  \"os\": ").append(quoted(info.os)).append(",\n  \"architecture\": ").append(quoted(info.architecture)).append(",\n  \"renderer\": ").append(quoted(info.renderer)).append(",\n  \"vendor\": ").append(quoted(info.vendor)).append(",\n  \"glVersion\": ").append(quoted(info.glVersion)).append(",\n  \"javaVersion\": ").append(quoted(info.javaVersion))
                .append(",\n  \"sourceRevision\": ").append(quoted(info.sourceRevision)).append(",\n  \"processors\": ").append(info.processors).append(",\n  \"maxHeapBytes\": ").append(info.maxHeap).append(",\n  \"width\": ").append(info.width).append(",\n  \"height\": ").append(info.height).append(",\n  \"route\": ").append(quoted(info.route.name())).append(",\n  \"cache\": ").append(quoted(info.cache)).append(",\n  \"settings\": {");
            boolean first=true;for(GraphicsProfile.Key key:GraphicsProfile.Key.values()){if(!first)b.append(',');first=false;b.append('\n').append("    ").append(quoted(key.name())).append(": ").append(quoted(info.settings.get(key)));}
            b.append("\n  },\n  \"labels\": {\"gpu\": \"Delayed completed query results; frame value is EMA, not per-frame GPU percentiles\", \"vram\": \"Estimated geometry and targets, not driver-reported VRAM\", \"allocation\": \"Render-thread allocation only; NaN means unavailable\", \"video\": \"Compare equal routes with video inactive/active; capture CPU time excludes asynchronous encoder work\"}\n}\n");
            tmp=Files.createTempFile(folder,"performance-",".tmp");Files.writeString(tmp,b);atomicMove(tmp,json);return new Export(csv,json,count);
        }finally{Files.deleteIfExists(tmp);}
    }
    private static void atomicMove(Path tmp,Path target)throws IOException{try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,target);}}
    @Override public void close(){stop();exporter.shutdown();try{if(!exporter.awaitTermination(30,TimeUnit.SECONDS))exporter.shutdownNow();}catch(InterruptedException e){Thread.currentThread().interrupt();exporter.shutdownNow();}}
}
