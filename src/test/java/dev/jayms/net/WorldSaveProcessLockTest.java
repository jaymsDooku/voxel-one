package dev.jayms.net;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class WorldSaveProcessLockTest {
 @TempDir Path folder;
 public static void main(String[] args)throws Exception {
  Path source=Path.of(args[0]),target=Path.of(args[1]),ready=Path.of(args[2]),release=Path.of(args[3]);
  WorldSavePublication.replace(source,target,true,(a,b,options)->{
   Files.writeString(ready,"ready");long end=System.nanoTime()+20_000_000_000L;
   while(!Files.exists(release)&&System.nanoTime()<end)try{Thread.sleep(10);}catch(InterruptedException stopped){Thread.currentThread().interrupt();throw new IOException("Synthetic child interrupted",stopped);}
   if(!Files.exists(release))throw new IOException("Synthetic child release timeout");Files.move(a,b,options);
  });
 }
 @Test void competingProcessRejectsBeforeMutationThenAllowsFreshRetry()throws Exception {
  Path target=folder.resolve("world.dat"),source=folder.resolve("child.tmp"),candidate=folder.resolve("candidate.tmp"),ready=folder.resolve("ready.signal"),release=folder.resolve("release.signal");
  Files.writeString(target,"original");Files.writeString(source,"child winner");Files.writeString(candidate,"fresh retry");
  var expected=WorldSavePublication.capture(target,1024,()->false);
  String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
  var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",cp,WorldSaveProcessLockTest.class.getName(),source.toString(),target.toString(),ready.toString(),release.toString()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
  try {
   long end=System.nanoTime()+10_000_000_000L;while(child.isAlive()&&!Files.exists(ready)&&System.nanoTime()<end)Thread.sleep(10);
   assertTrue(Files.exists(ready),"Child acquired save publication lease");
   assertTrue(assertThrows(IOException.class,()->WorldSavePublication.capture(target,1024,()->false)).getMessage().contains("busy"));
   for(boolean atomic:new boolean[]{false,true})assertTrue(assertThrows(IOException.class,()->WorldSavePublication.replace(candidate,target,atomic)).getMessage().contains("busy"));
   assertTrue(assertThrows(IOException.class,()->WorldSavePublication.replaceIfUnchanged(candidate,target,expected,()->false)).getMessage().contains("busy"));
   assertEquals("original",Files.readString(target));assertEquals("child winner",Files.readString(source));assertEquals("fresh retry",Files.readString(candidate));
   Files.writeString(release,"release");assertTrue(child.waitFor(10,TimeUnit.SECONDS));assertEquals(0,child.exitValue());assertEquals("child winner",Files.readString(target));assertFalse(Files.exists(source));
   assertThrows(IOException.class,()->WorldSavePublication.replaceIfUnchanged(candidate,target,expected,()->false));assertEquals("child winner",Files.readString(target));assertEquals("fresh retry",Files.readString(candidate));
   var current=WorldSavePublication.capture(target,1024,()->false);WorldSavePublication.replaceIfUnchanged(candidate,target,current,()->false);assertEquals("fresh retry",Files.readString(target));assertFalse(Files.exists(candidate));
   assertTrue(Files.isRegularFile(WorldSavePublication.lockPath(target),LinkOption.NOFOLLOW_LINKS));assertEquals(0,Files.size(WorldSavePublication.lockPath(target)));
  }finally{if(child.isAlive()){Files.writeString(release,"release");if(!child.waitFor(3,TimeUnit.SECONDS)){child.destroyForcibly();child.waitFor(3,TimeUnit.SECONDS);}}}
 }
 @Test void invalidLockTypeRejectsWithoutTouchingSaveOrStagedBytes()throws Exception {
  Path target=folder.resolve("typed.dat"),source=folder.resolve("typed.tmp");Files.writeString(target,"original");Files.writeString(source,"staged");Files.createDirectory(WorldSavePublication.lockPath(target));
  assertThrows(IOException.class,()->WorldSavePublication.capture(target,1024,()->false));assertThrows(IOException.class,()->WorldSavePublication.replace(source,target,true));
  assertEquals("original",Files.readString(target));assertEquals("staged",Files.readString(source));
 }
}
