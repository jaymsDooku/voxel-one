package dev.jayms.net;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class WorldSaveStripeTest {
 @TempDir Path folder;
 @Test void competingThreadRejectsWithoutWaitingAndFreshRetrySucceeds()throws Exception {
  Path target=folder.resolve("thread.dat"),source=folder.resolve("thread.tmp"),candidate=folder.resolve("candidate.tmp");
  Files.writeString(target,"original");Files.writeString(source,"winner");Files.writeString(candidate,"retry");
  var expected=WorldSavePublication.capture(target,1024,()->false);
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try {
   var writer=pool.submit(()->{WorldSavePublication.replace(source,target,true,(a,b,options)->{
    entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IOException("Synthetic release timeout");}
    catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException(interrupted);}Files.move(a,b,options);
   });return null;});
   assertTrue(entered.await(5,TimeUnit.SECONDS));
   var competing=pool.submit(()->{
    assertEquals("Save publication busy",assertThrows(IOException.class,()->WorldSavePublication.capture(target,1024,()->false)).getMessage());
    assertEquals("Save publication busy",assertThrows(IOException.class,()->WorldSavePublication.replace(candidate,target,false)).getMessage());
    assertEquals("Save publication busy",assertThrows(IOException.class,()->WorldSavePublication.replaceIfUnchanged(candidate,target,expected,()->false)).getMessage());return null;
   });
   // The writer remains held until every competing operation has returned.
   competing.get(2,TimeUnit.SECONDS);assertEquals("original",Files.readString(target));assertEquals("retry",Files.readString(candidate));
   release.countDown();writer.get(5,TimeUnit.SECONDS);assertEquals("winner",Files.readString(target));
   assertThrows(IOException.class,()->WorldSavePublication.replaceIfUnchanged(candidate,target,expected,()->false));
   var fresh=WorldSavePublication.capture(target,1024,()->false);WorldSavePublication.replaceIfUnchanged(candidate,target,fresh,()->false);assertEquals("retry",Files.readString(target));
  }finally{release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
 }
 @Test void callbackReentryRejectsAndFailedMoveReleasesStripe()throws Exception {
  Path target=folder.resolve("reentry.dat"),source=folder.resolve("reentry.tmp");Files.writeString(target,"original");Files.writeString(source,"staged");
  var failure=new IOException("Synthetic denied move");
  assertSame(failure,assertThrows(IOException.class,()->WorldSavePublication.replace(source,target,true,(a,b,options)->{
   assertEquals("Save publication busy",assertThrows(IOException.class,()->WorldSavePublication.capture(target,1024,()->false)).getMessage());
   assertEquals("Save publication busy",assertThrows(IOException.class,()->WorldSavePublication.replace(source,target,true)).getMessage());throw failure;
  })));
  assertEquals("original",Files.readString(target));assertEquals("staged",Files.readString(source));
  var fresh=WorldSavePublication.capture(target,1024,()->false);WorldSavePublication.replaceIfUnchanged(source,target,fresh,()->false);assertEquals("staged",Files.readString(target));
 }
}
