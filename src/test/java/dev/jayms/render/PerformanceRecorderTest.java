package dev.jayms.render;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class PerformanceRecorderTest {
 @TempDir Path dir;
 PerformanceRecorder.Metadata info(){return new PerformanceRecorder.Metadata("synthetic OS","synthetic arch","test renderer","test vendor","3.3","17","unknown",4,1000000,640,400,GraphicsProfile.preset(GraphicsProfile.Preset.BALANCED),PerformanceRecorder.Route.GROUND,"WARM");}
 @Test void percentilesAndOnePercentLowUseActualDurations(){double[] frames=new double[100];for(int i=0;i<100;i++)frames[i]=i+1;assertEquals(50,PerformanceRecorder.percentile(frames,100,.5));assertEquals(95,PerformanceRecorder.percentile(frames,100,.95));assertEquals(99,PerformanceRecorder.percentile(frames,100,.99));assertEquals(10,PerformanceRecorder.onePercentLow(frames,100));assertTrue(Double.isNaN(PerformanceRecorder.percentile(frames,0,.95)));}
 @Test void warmupUnavailableGpuAtomicExportAndNoPrivateFields()throws Exception {
  try(var r=new PerformanceRecorder()){r.start(dir,info(),2);double[] frame=new double[PerformanceRecorder.Metric.values().length];java.util.Arrays.fill(frame,Double.NaN);frame[0]=10;r.sample(frame);r.sample(frame);r.sample(frame);frame[0]=20;r.sample(frame);var out=r.stop().get(10,TimeUnit.SECONDS);assertEquals(2,out.samples());String json=Files.readString(out.summary()),csv=Files.readString(out.csv());assertTrue(json.contains("\"warmupExcluded\": 2"));assertTrue(json.contains("\"p95FrameMs\": 20.0"));assertEquals(3,csv.lines().count());assertFalse(json.contains("user.home"));assertFalse(json.contains("world"));assertFalse(csv.contains("NaN"));try(var paths=Files.list(dir)){assertEquals(2,paths.count());}}
 }
 @Test void captureIsBoundedAndFullBufferStopsAutomatically()throws Exception {
  try(var r=new PerformanceRecorder()){r.start(dir,info(),0);double[] frame=new double[PerformanceRecorder.Metric.values().length];frame[0]=16;for(int i=0;i<PerformanceRecorder.MAX_SAMPLES+2;i++)r.sample(frame);assertFalse(r.active());assertEquals(PerformanceRecorder.MAX_SAMPLES,r.stop().get(20,TimeUnit.SECONDS).samples());}
 }
}
