package dev.jayms.net;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
public class WorldSaveEpochTest {
 @TempDir Path folder;
 @Test void identicalByteChildPublicationStillInvalidatesOldEpoch()throws Exception {
  Path target=folder.resolve("same.dat"),source=folder.resolve("same.tmp"),candidate=folder.resolve("retry.tmp"),ready=folder.resolve("epoch-ready.signal"),release=folder.resolve("epoch-release.signal");
  Files.writeString(target,"same bytes");Files.writeString(source,"same bytes");Files.writeString(candidate,"fresh retry");var expected=WorldSavePublication.capture(target,1024,()->false);
  String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
  var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Djava.io.tmpdir="+folder.toAbsolutePath(),"-cp",cp,WorldSaveProcessLockTest.class.getName(),source.toString(),target.toString(),ready.toString(),release.toString()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
  try {
   long end=System.nanoTime()+10_000_000_000L;while(child.isAlive()&&!Files.exists(ready)&&System.nanoTime()<end)Thread.sleep(10);assertTrue(Files.exists(ready));Files.writeString(release,"release");assertTrue(child.waitFor(10,TimeUnit.SECONDS));assertEquals(0,child.exitValue());assertEquals("same bytes",Files.readString(target));
   assertTrue(assertThrows(IOException.class,()->WorldSavePublication.replaceIfUnchanged(candidate,target,expected,()->false)).getMessage().contains("epoch changed"));assertEquals("fresh retry",Files.readString(candidate));assertEquals("same bytes",Files.readString(target));
   var current=WorldSavePublication.capture(target,1024,()->false);WorldSavePublication.replaceIfUnchanged(candidate,target,current,()->false);assertEquals("fresh retry",Files.readString(target));
  }finally{if(child.isAlive()){Files.writeString(release,"release");if(!child.waitFor(3,TimeUnit.SECONDS)){child.destroyForcibly();child.waitFor(3,TimeUnit.SECONDS);}}}
 }
 @Test void emptyLegacyMetadataUpgradesAndMalformedRecordsReject()throws Exception {
  Path target=folder.resolve("upgrade.dat"),source=folder.resolve("upgrade.tmp");Files.writeString(target,"original");Files.writeString(source,"upgraded");Files.createFile(WorldSavePublication.lockPath(target));var expected=WorldSavePublication.capture(target,1024,()->false);assertEquals(32,Files.size(WorldSavePublication.lockPath(target)));WorldSavePublication.replaceIfUnchanged(source,target,expected,()->false);assertEquals("upgraded",Files.readString(target));
  byte[][] bad={new byte[1],new byte[31],new byte[33],new byte[32],ByteBuffer.allocate(32).putLong(0x56315055424c3031L).putLong(0).putLong(0).putLong(-1).array()};
  for(int i=0;i<bad.length;i++) {
   Path t=folder.resolve("bad"+i+".dat"),s=folder.resolve("bad"+i+".tmp");Files.writeString(t,"original");Files.writeString(s,"staged");Files.write(WorldSavePublication.lockPath(t),bad[i]);
   assertThrows(IOException.class,()->WorldSavePublication.capture(t,1024,()->false));assertThrows(IOException.class,()->WorldSavePublication.replace(s,t,true));assertEquals("original",Files.readString(t));assertEquals("staged",Files.readString(s));
  }
 }
 @Test void failedMoveConservativelyInvalidatesThenFreshRetrySucceeds()throws Exception {
  Path target=folder.resolve("failure.dat"),source=folder.resolve("failure.tmp");Files.writeString(target,"original");Files.writeString(source,"staged");var expected=WorldSavePublication.capture(target,1024,()->false);var denied=new IOException("synthetic move denied");
  assertSame(denied,assertThrows(IOException.class,()->WorldSavePublication.replace(source,target,true,(a,b,options)->{throw denied;})));assertEquals("original",Files.readString(target));assertEquals("staged",Files.readString(source));
  assertTrue(assertThrows(IOException.class,()->WorldSavePublication.replaceIfUnchanged(source,target,expected,()->false)).getMessage().contains("epoch changed"));var fresh=WorldSavePublication.capture(target,1024,()->false);WorldSavePublication.replaceIfUnchanged(source,target,fresh,()->false);assertEquals("staged",Files.readString(target));
 }
 @Test void exhaustedSequenceRejectsBeforeMove()throws Exception {
  Path target=folder.resolve("exhausted.dat"),source=folder.resolve("exhausted.tmp");Files.writeString(target,"original");Files.writeString(source,"staged");WorldSavePublication.capture(target,1024,()->false);Path metadata=WorldSavePublication.lockPath(target);var bytes=Files.readAllBytes(metadata);ByteBuffer.wrap(bytes).putLong(24,Long.MAX_VALUE);Files.write(metadata,bytes);
  assertTrue(assertThrows(IOException.class,()->WorldSavePublication.replace(source,target,true,(a,b,options)->fail("Exhausted epoch must not invoke move"))).getMessage().contains("exhausted"));assertEquals("original",Files.readString(target));assertEquals("staged",Files.readString(source));
 }
}
