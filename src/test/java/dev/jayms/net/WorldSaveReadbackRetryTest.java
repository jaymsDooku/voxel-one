package dev.jayms.net;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class WorldSaveReadbackRetryTest {
 @TempDir Path folder;
 @Test void heldLeaseRetriesReadbackButPublicationStaysFailBusy()throws Exception {
  var fixture=new ReadbackMetadataTest();fixture.folder=Files.createDirectory(folder.resolve("readback"));Path target=fixture.fixture(),source=folder.resolve("same.tmp");byte[] original=Files.readAllBytes(target);Files.write(source,original);
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();RegionReplayWorker worker=null;
  try {
   var writer=pool.submit(()->{WorldSavePublication.replace(source,target,true,(a,b,options)->{
    entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("Synthetic release deadline");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException(interrupted);}Files.move(a,b,options);
   });return null;});assertTrue(entered.await(5,TimeUnit.SECONDS));
   worker=fixture.worker(target);var observed=worker;
   RegionReplayWorkerTest.await(()->observed.status().sourceRows()>0);
   assertThrows(WorldSavePublication.BusyException.class,()->WorldSavePublication.capture(target,512*1024*1024L,()->false));
   release.countDown();writer.get(5,TimeUnit.SECONDS);ReadbackMetadataTest.ready(worker);
   var local=worker.metadataResult(ReadbackMetadataTest.REV);assertFalse(local.replayComplete());assertEquals(17,local.health);assertArrayEquals(original,Files.readAllBytes(target));
  }finally{release.countDown();if(worker!=null)RegionReplayWorkerTest.closed(worker);pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));fixture.noSpools();}
 }
 @Test void busyRetryCancelsAndTimesOutWithoutMutation()throws Exception {
  Path target=folder.resolve("held.dat"),source=folder.resolve("held.tmp");Files.writeString(target,"original");Files.writeString(source,"staged");
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
  try {
   var writer=pool.submit(()->{WorldSavePublication.replace(source,target,true,(a,b,options)->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("Synthetic deadline");}catch(InterruptedException interrupted){throw new IOException(interrupted);}throw new IOException("Synthetic denied move");});return null;});
   assertTrue(entered.await(5,TimeUnit.SECONDS));var checks=new AtomicInteger();String digest=RegionalReadbackSourceTest.hash("original".getBytes(java.nio.charset.StandardCharsets.UTF_8));
   assertThrows(CancellationException.class,()->WorldSavePublication.captureMatchingReadback(target,digest,1024,()->checks.incrementAndGet()>4));assertTrue(checks.get()>4);
   assertThrows(WorldSavePublication.BusyException.class,()->WorldSavePublication.captureMatchingReadback(target,digest,1024,()->false));
   assertEquals("original",Files.readString(target));assertEquals("staged",Files.readString(source));release.countDown();assertThrows(ExecutionException.class,()->writer.get(5,TimeUnit.SECONDS));
   assertNotNull(WorldSavePublication.captureMatchingReadback(target,digest,1024,()->false));
  }finally{release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
 }
 @Test void changedDigestAfterContentionRejectsAdmission()throws Exception {
  Path target=folder.resolve("changed.dat"),source=folder.resolve("changed.tmp");Files.writeString(target,"original");Files.writeString(source,"replacement");String digest=RegionalReadbackSourceTest.hash("original".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var retried=new CountDownLatch(1);var calls=new AtomicInteger();var pool=Executors.newFixedThreadPool(2);
  try {
   var writer=pool.submit(()->{WorldSavePublication.replace(source,target,true,(a,b,options)->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("Synthetic deadline");}catch(InterruptedException interrupted){throw new IOException(interrupted);}Files.move(a,b,options);});return null;});assertTrue(entered.await(5,TimeUnit.SECONDS));
   var reader=pool.submit(()->WorldSavePublication.captureMatchingReadback(target,digest,1024,()->{if(calls.incrementAndGet()>=3)retried.countDown();return false;}));assertTrue(retried.await(5,TimeUnit.SECONDS));release.countDown();writer.get(5,TimeUnit.SECONDS);
   var failure=assertThrows(ExecutionException.class,()->reader.get(5,TimeUnit.SECONDS));assertInstanceOf(IOException.class,failure.getCause());assertEquals("Save source digest changed",failure.getCause().getMessage());assertEquals("replacement",Files.readString(target));assertFalse(Files.exists(source));
  }finally{release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
 }
 @Test void malformedMetadataAndInterruptAreNotRetried()throws Exception {
  Path target=folder.resolve("bad.dat");Files.writeString(target,"original");Files.write(WorldSavePublication.lockPath(target),new byte[3]);String digest=RegionalReadbackSourceTest.hash("original".getBytes(java.nio.charset.StandardCharsets.UTF_8));var calls=new AtomicInteger();
  var failure=assertThrows(IOException.class,()->WorldSavePublication.captureMatchingReadback(target,digest,1024,()->{calls.incrementAndGet();return false;}));assertFalse(failure instanceof WorldSavePublication.BusyException);assertEquals("Save publication epoch size",failure.getMessage());assertEquals(2,calls.get());
  Thread.currentThread().interrupt();try{assertThrows(CancellationException.class,()->WorldSavePublication.captureMatchingReadback(target,digest,1024,()->false));assertTrue(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
  assertEquals("original",Files.readString(target));
 }
}
