package dev.jayms;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.city.GameConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
class RecipeMultiplayerTest {
 @TempDir Path temp;
 static void until(BooleanSupplier condition) throws Exception {
  long end=System.nanoTime()+15_000_000_000L;
  while(!condition.getAsBoolean()) {if(System.nanoTime()>end)fail("Timed out waiting for scoped recipe event");Thread.sleep(10);}
 }
 static Protocol.Pose held(MultiplayerClient c,int item) {var p=c.spawn;return new Protocol.Pose(c.id,p.x(),p.y(),p.z(),0,0,0,0,false,0,item);}
 @Test void twoClientsRejectMissingForgedAndRepeatedCraftsThenShareToolHandsDropsAndPersistence() throws Exception {
  var identity=SecureTransport.server(temp.resolve("synthetic-tls"));var accounts=new AccountStore(temp.resolve("synthetic-accounts.db"));
  accounts.register("crafter","synthetic-test-only-a".toCharArray());accounts.register("viewer","synthetic-test-only-b".toCharArray());
  var full=new Inventory();full.add(165,64);full.add(190,64);full.add(3,64);
  Path save=temp.resolve("world.dat");
  try(var out=new DataOutputStream(Files.newOutputStream(save))) {
   out.writeInt(Protocol.MAGIC);out.writeInt(-8);out.writeLong(Terrain.DEFAULT_SEED);out.writeInt(Terrain.CURRENT_VERSION);new ModelLibrary().write(out);
   out.writeInt(0);out.writeInt(1);out.writeUTF("crafter");full.write(out);out.writeByte(20);out.writeInt(0);
  }
  try(var server=new MultiplayerServer("127.0.0.1",0,save,accounts,identity.context(),Terrain.DEFAULT_SEED,GameConfig.sandbox())) {
   Thread t=new Thread(()->{try{server.run();}catch(IOException e){throw new RuntimeException(e);}});t.start();
   try(var a=new MultiplayerClient("127.0.0.1",server.port(),"crafter","synthetic-test-only-a".toCharArray(),false,identity.fingerprint());
       var b=new MultiplayerClient("127.0.0.1",server.port(),"viewer","synthetic-test-only-b".toCharArray(),false,identity.fingerprint())) {
    assertTrue(b.craft(96,b.spawn)); assertFalse(b.craft(96,b.spawn));
    until(()->{a.poll();b.poll();return !b.craftPending();});assertTrue(b.notice().contains("Missing:"));assertEquals(0,b.inventory.countType(191));
    assertTrue(a.craft(191,a.spawn));until(()->{a.poll();return !a.craftPending();});assertTrue(a.notice().contains("Unknown recipe"));assertEquals(64,a.inventory.countType(165));
    for(int r=96;r<=103;r++) {assertTrue(a.craft(r,a.spawn));assertFalse(a.craft(r,a.spawn));until(()->{a.poll();b.poll();return !a.craftPending();});assertEquals(1,a.inventory.countType(Crafting.recipe(r).output()));}
    assertEquals(55,a.inventory.countType(165));assertEquals(50,a.inventory.countType(190));assertEquals(55,a.inventory.countType(3));
    // Send a duplicate request identity on the actual TLS stream after its first result.
    var f=MultiplayerClient.class.getDeclaredField("nextRequest");f.setAccessible(true);int duplicate=f.getInt(a);
    var stream=MultiplayerClient.class.getDeclaredField("out");stream.setAccessible(true);var out=(DataOutputStream)stream.get(a);
    out.writeByte(Protocol.CRAFT);out.writeInt(duplicate);out.writeInt(96);out.writeInt(1);a.spawn.write(out);out.flush();
    until(()->{a.poll();return a.notice().contains("Repeated inventory request");});assertEquals(1,a.inventory.countType(191));assertEquals(55,a.inventory.countType(165));
    a.move(held(a,191));b.move(held(b,0));until(()->{a.poll();b.poll();return b.players.containsKey(a.id)&&b.players.get(a.id).heldItem()==191;});
    assertEquals(a.models.get(191).definition().fingerprint(),b.models.get(191).definition().fingerprint());
    int slot=0;while(a.inventory.type(slot)!=191)slot++;
    assertTrue(a.edit(new Protocol.Edit(8,(int)a.spawn.y()+3,24,191),a.spawn,slot));
    until(()->{a.poll();b.poll();return a.notice().contains("Placement rejected");});assertEquals(1,a.inventory.countType(191));
    assertTrue(a.drop(slot,a.spawn));until(()->{a.poll();b.poll();return !a.craftPending()&&b.drops.values().stream().anyMatch(d->d.type()==191);});
    assertEquals(0,a.inventory.countType(191));assertEquals(1,b.drops.values().stream().filter(d->d.type()==191).mapToInt(ItemDrop::count).sum());
    var d=b.drops.values().stream().filter(x->x.type()==191).findFirst().orElseThrow();
    b.move(new Protocol.Pose(b.id,d.x(),d.y()-.35f,d.z(),0,0));until(()->{a.poll();b.poll();return b.inventory.countType(191)==1&&a.drops.isEmpty();});
    b.move(held(b,191));until(()->{a.poll();b.poll();return a.players.containsKey(b.id)&&a.players.get(b.id).heldItem()==191;});
    assertTrue(a.connected()&&b.connected());
   } finally {server.close();t.join(3000);}
  }
  try(var server=new MultiplayerServer("127.0.0.1",0,save,accounts,identity.context(),Terrain.DEFAULT_SEED,GameConfig.sandbox())) {
   Thread t=new Thread(()->{try{server.run();}catch(IOException e){throw new RuntimeException(e);}});t.start();
   try(var b=new MultiplayerClient("127.0.0.1",server.port(),"viewer","synthetic-test-only-b".toCharArray(),false,identity.fingerprint())) {assertEquals(1,b.inventory.countType(191));assertNotNull(b.models.get(191));}
   finally {server.close();t.join(3000);}
  }
 }
}
