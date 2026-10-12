import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.net.model.*;
import dev.jayms.ui.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;

/** Two synthetic TLS clients; one native renderer views each live authoritative snapshot. */
public class RecipeNetworkPlaytest extends SandboxPlaytest {
 static MultiplayerClient a,b;
 static volatile Protocol.Pose idlePose;
 static volatile boolean viewingB, done;
 static Protocol.Pose pose(MultiplayerClient c,float z,int item,float yaw) {return new Protocol.Pose(c.id,c.spawn.x(),c.spawn.y(),z,yaw,0,0,0,false,0,item);}
 static void play(String id,Path out) throws Exception {
  x("windowsize",id,"1280","720");x("key","e");RecipeBookPlaytest.hit(id,"book",-1);
  RecipeBookPlaytest.recipe(id,96);RecipeBookPlaytest.hit(id,"craft",96);
  await(()->a.inventory.countType(191)==1&&!a.craftPending(),"Native client authoritative craft");
  capture(id,out.resolve("recipe-network-book.png"));x("key","e");
  require(b.craft(100,b.spawn),"Second client stone pickaxe request");
  await(()->b.inventory.countType(195)==1&&!b.craftPending(),"Second client receives stone pickaxe");
  idlePose=pose(b,b.spawn.z()-3,195,90);b.move(idlePose);
  await(()->a.players.containsKey(b.id)&&a.players.get(b.id).heldItem()==195,"First client sees stone tool pose");
  onFrame(()->{int slot=0;while(a.inventory.type(slot)!=191)slot++;((InventoryHud)get("inventoryHud")).selected=slot;return null;});
  capture(id,out.resolve("recipe-network-first-hands.png"));
  x("key","q");await(()->a.inventory.countType(191)==0&&b.drops.values().stream().anyMatch(d->d.type()==191),"Both clients see dropped wooden tool");
  require(a.craft(96,player().pose(a.id)),"Second wooden tool batch");await(()->a.inventory.countType(191)==1&&!a.craftPending(),"Second wooden tool accepted");
  onFrame(()->{int slot=0;while(a.inventory.type(slot)!=191)slot++;((InventoryHud)get("inventoryHud")).selected=slot;return null;});
  await(()->b.players.containsKey(a.id)&&b.players.get(a.id).heldItem()==191,"Second client sees wooden tool pose");
  onFrame(()->{player().driveSeat(new org.joml.Vector3f(a.spawn.x(),a.spawn.y(),a.spawn.z()+3),-65);player().look(0,-10);return null;});
  x("key","F5");capture(id,out.resolve("recipe-network-first-drop.png"));
  onFrame(()->{
   idlePose=player().pose(a.id);viewingB=true;
   set("network",b);int slot=0;while(b.inventory.type(slot)!=195)slot++;((InventoryHud)get("inventoryHud")).selected=slot;
   set("player",new dev.jayms.player.Player(new org.joml.Vector3f(b.spawn.x(),b.spawn.y(),b.spawn.z()-3),65,-10,(Camera)get("camera")));
   return null;
  });
  capture(id,out.resolve("recipe-network-second-hands.png"));
  x("key","F5");capture(id,out.resolve("recipe-network-second-drop.png"));
  require(a.models.get(191).definition().fingerprint().equals(b.models.get(191).definition().fingerprint()),"Wooden model identities agree");
  require(a.models.get(195).definition().fingerprint().equals(b.models.get(195).definition().fingerprint()),"Stone model identities agree");
  require(a.drops.values().stream().anyMatch(d->d.type()==191)&&b.drops.values().stream().anyMatch(d->d.type()==191),"Both snapshots contain the correct ground tool");
  Files.writeString(out.resolve("recipe-network-playtest.txt"),"Playtest: PASS. Linux X11 software OpenGL, two isolated synthetic TLS clients on loopback, on a saved synthetic flat stone platform. A crafted a wooden pickaxe through real recipe-book controls; B crafted a stone pickaxe through the same authoritative request API. A rendered B holding the stone pickaxe; A dropped the wooden tool with Q and both live snapshots received its model/type/count. A crafted and equipped another wooden pickaxe. The same native renderer then switched to B's live snapshot and rendered its stone hand plus A's wooden remote hand. Held-hand and ground-drop views captured for both snapshots. Both model fingerprints agree across clients. This is two network clients with one native render window, not two physical computers. Expected authoritative inventories, held poses, and ground tools matched observed assertions.\n");
 }
 public static void main(String[] args) throws Exception {
  Path out=Path.of(args[0]);RecipeBookPlaytest.outPath=out;Path profile=Path.of("target/recipe-profile");Files.createDirectories(out);Files.createDirectories(profile);
  var tls=SecureTransport.server(profile.resolve("synthetic-tls"));var accounts=new AccountStore(profile.resolve("synthetic-accounts.db"));
  accounts.register("craftviewa","synthetic-client-only-a".toCharArray());accounts.register("craftviewb","synthetic-client-only-b".toCharArray());
  Path save=profile.resolve("server.dat");try(var data=new DataOutputStream(Files.newOutputStream(save))) {
   data.writeInt(Protocol.MAGIC);data.writeInt(-8);data.writeLong(Terrain.DEFAULT_SEED);data.writeInt(Terrain.CURRENT_VERSION);new ModelLibrary().write(data);
   List<Protocol.Edit> edits=new ArrayList<>();
   for(int x=5;x<=13;x++)for(int z=19;z<=28;z++) {edits.add(new Protocol.Edit(x,90,z,3));for(int y=91;y<=95;y++)edits.add(new Protocol.Edit(x,y,z,0));}
   data.writeInt(edits.size());for(var edit:edits)edit.write(data);data.writeInt(2);
   for(String name:List.of("craftviewa","craftviewb")){var inv=new Inventory();inv.add(165,6);inv.add(190,4);inv.add(3,10);data.writeUTF(name);inv.write(data);data.writeByte(20);}data.writeInt(0);
  }
  try(var server=new MultiplayerServer("127.0.0.1",0,save,accounts,tls.context(),Terrain.DEFAULT_SEED,GameConfig.sandbox())) {
   var serverThread=new Thread(()->{try{server.run();}catch(IOException e){throw new RuntimeException(e);}});serverThread.start();
   try {
    a=new MultiplayerClient("127.0.0.1",server.port(),"craftviewa","synthetic-client-only-a".toCharArray(),false,tls.fingerprint());
    b=new MultiplayerClient("127.0.0.1",server.port(),"craftviewb","synthetic-client-only-b".toCharArray(),false,tls.fingerprint());
    idlePose=b.spawn;
    var heartbeat=new Thread(()->{while(!done){(viewingB?a:b).move(idlePose);try{Thread.sleep(1000);}catch(InterruptedException e){return;}}});heartbeat.setDaemon(true);heartbeat.start();
    set("network",a);set("gameConfig",GameConfig.sandbox());glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
    var driver=new Thread(()->{long handle=0;try{
     String id="";for(int i=0;i<180;i++){if(get("player")!=null&&(int)get("fps")>=0){Thread.sleep(1000);handle=((dev.jayms.window.Window)get("window")).getHandle();id=x("search","--name","^Voxel One").lines().findFirst().orElseThrow();break;}Thread.sleep(500);}
     require(!id.isEmpty(),"Network engine ready");x("windowfocus",id);if(((ControlsMenu)get("menu")).open)x("key","Escape");play(id,out);
    }catch(Throwable e){failure=e;try{Files.writeString(out.resolve("recipe-network-playtest.txt"),"FAIL: "+e.getClass().getSimpleName()+": "+e.getMessage()+"\n");}catch(Exception ignored){}}
    finally{if(handle!=0)glfwSetWindowShouldClose(handle,true);}});
    driver.setDaemon(true);driver.start();game.run(new Main.FrameObserver(){public void beforeFrame(Main app){b.poll();for(var j=jobs.poll();j!=null;j=jobs.poll())j.run();}public void afterFrame(Main app){frames.incrementAndGet();}});driver.join(1000);
    if(failure!=null)throw new AssertionError("Network recipe playtest failed");
   } finally {done=true;if(a!=null)a.close();if(b!=null)b.close();server.close();serverThread.join(3000);}
  }
 }
}
