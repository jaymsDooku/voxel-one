import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.ui.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;

/** Real production GLFW UI on the assigned X11 display; raw-material fixture is synthetic. */
public class RecipeBookPlaytest extends SandboxPlaytest {
 static String step="startup";
 static Path outPath;
 static final List<String> scenes=new ArrayList<>();
 static void mark(String name) throws Exception {
  onFrame(()->{Object recorder=get("recorder");var f=recorder.getClass().getDeclaredField("started");f.setAccessible(true);
   scenes.add(name+"\t"+((System.nanoTime()-f.getLong(recorder))/1_000_000_000.0));return null;});
 }
 static InventoryHud hud() throws Exception { return (InventoryHud)get("inventoryHud"); }
 static Object property(Object obj,String name) throws Exception { var m=obj.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(obj); }
 static void hit(String id,String action,int value) throws Exception {
  Object[] chosen={null};
  for(int attempt=0;attempt<12&&chosen[0]==null;attempt++) {
  onFrame(()->{var f=InventoryHud.class.getDeclaredField("hits");f.setAccessible(true);
   for(Object h:(List<?>)f.get(hud())) if(property(h,"action").equals(action)&&(value<0||((Integer)property(h,"value"))==value)) { chosen[0]=h;break; }
   return null;
  });
  if(chosen[0]==null)Thread.sleep(500);
  }
  require(chosen[0]!=null,"Visible UI action "+action+" "+value+" at "+step);
  Object h=chosen[0];float x=(Float)property(h,"x")+(Float)property(h,"w")/2,y=(Float)property(h,"y")+(Float)property(h,"h")/2;
  x("mousemove","--window",id,""+(int)x,""+(int)y);x("click","1");
 }
 static void search(String id,String value) throws Exception {
  hit(id,"search",-1);try { await(()->hud().searchFocused(),"Search receives focus at "+step); } catch(AssertionError e) { capture(id,outPath.resolve("recipe-book-failure.png")); throw e; }int n=hud().search().length();
  if(n>0)x("key","--repeat",""+n,"--delay","20","BackSpace");
  if(!value.isEmpty())x("type","--clearmodifiers","--delay","25",value);
  try { await(()->hud().search().equals(value),"Search text entry "+value); }
  catch(AssertionError e) { capture(id,outPath.resolve("recipe-book-failure.png"));throw new AssertionError("Search expected "+value+" observed "+hud().search()+" focus="+hud().searchFocused()+" at "+step); }
  x("key","Return");
 }
 static void recipe(String id,int recipe) throws Exception {
  search(id,Crafting.recipe(recipe).name());hit(id,"recipe",recipe);
 }
 static void craft(String id,int recipe,int expected) throws Exception {
  recipe(id,recipe);int output=Crafting.recipe(recipe).output();int before=local().inventory.countType(output);
  hit(id,"craft",recipe);await(()->local().inventory.countType(output)==before+expected,"Craft output "+Items.name(output));
  if(recipe==96)mark("craft-wooden-pickaxe");
 }
 static void gather(String id,int type) throws Exception {
  onFrame(()->{var w=(World)get("world");w.apply(new Protocol.Edit(8,fixtureY+2,21,type));warmFixture();return null;});
  int before=local().inventory.countType(type);x("click","1");await(()->!local().drops.isEmpty(),"Harvest creates drop");
  x("keydown","w");try{await(()->local().inventory.countType(type)==before+1,"Harvest pickup");}finally{x("keyup","w");}
  onFrame(()->{player().driveSeat(new org.joml.Vector3f(8.5f,fixtureY+1,24.5f),-90);return null;});
 }
 static void play(String id,Path out) throws Exception {
  require(!local().city.frame().config().city(),"Sandbox only");
  x("windowsize",id,"1280","720");await(()->(int)get("framebufferWidth")==1280,"Ordinary window");
  onFrame(()->{var w=(World)get("world");fixtureY=Math.min(90,w.terrain().column(8,24).height()+2);
   for(int dx=5;dx<=13;dx++)for(int dz=19;dz<=28;dz++) {w.apply(new Protocol.Edit(dx,fixtureY,dz,3));for(int dy=1;dy<=5;dy++)w.apply(new Protocol.Edit(dx,fixtureY+dy,dz,0));}
   var p=new dev.jayms.player.Player(new org.joml.Vector3f(8.5f,fixtureY+1,24.5f),-90,0,(Camera)get("camera"));p.toggleFlight();set("player",p);warmFixture();return null;
  });
  step="initial missing state";
  x("key","F10"); x("key","e");await(()->hud().open,"Inventory opens");hit(id,"book",-1);
  search(id,"pickaxe");require(hud().filtered(local().inventory).size()==2,"Both pickaxes visible without materials");
  recipe(id,96);capture(id,out.resolve("recipe-book-missing.png"));mark("missing-ingredients");
  int slots=local().inventory.countType(191);hit(id,"craft",96);require(local().inventory.countType(191)==slots,"Disabled craft consumes nothing");
  var pos=new org.joml.Vector3f(player().position());x("keydown","w");Thread.sleep(300);x("keyup","w");require(pos.distance(player().position())<.01,"Book blocks movement");
  hit(id,"search",-1);await(()->hud().searchFocused(),"Manual search focus");
  x("type","z");await(()->hud().search().endsWith("z"),"Search consumes Z text");
  x("key","Escape");await(()->!hud().searchFocused(),"Escape leaves search focus");
  require(hud().open&&hud().bookOpen(),"Escape clears focus before closing book; open="+hud().open+" book="+hud().bookOpen());
  step="harvest and reopen";
  x("key","e");await(()->!hud().open,"Inventory closes");gather(id,6);gather(id,3);
  onFrame(()->{local().inventory.add(6,4);local().inventory.add(3,20);return null;});
  x("key","e");hit(id,"book",-1);recipe(id,88);hit(id,"max",-1);require(hud().quantity()==5,"Maximum wood batches");hit(id,"craft",88);await(()->local().inventory.countType(165)==20,"Five wood to twenty planks");
  step="sticks and links";
  recipe(id,95);for(int i=0;i<3;i++)hit(id,"more",-1);require(hud().quantity()==4,"Bounded batch quantity");hit(id,"craft",95);await(()->local().inventory.countType(190)==16,"Four batches make sixteen sticks");
  recipe(id,96);hit(id,"ingredient",190);require(hud().selectedRecipe()==95,"Ingredient recipe navigation");hit(id,"back",-1);require(hud().selectedRecipe()==96,"Back returns to tool");
  recipe(id,100);hit(id,"ingredient",3);capture(id,out.resolve("recipe-book-stone-source.png"));
  step="eight tools";
  for(int r=96;r<=103;r++)craft(id,r,1);
  search(id,"pickaxe");capture(id,out.resolve("recipe-book-tools.png"));mark("browse-both-pickaxes");
  search(id,"");hit(id,"category",1);require(hud().filtered(local().inventory).size()==8,"Tools category");
  x("key","Tab");x("key","Return");require(hud().filtered(local().inventory).stream().allMatch(r->r.category().equals("Materials")),"Keyboard focus and Enter category activation");
  hit(id,"category",1);hit(id,"filter",-1);require(!hud().filtered(local().inventory).isEmpty(),"Craftable now before consumption");
  recipe(id,102);hit(id,"craft",102);await(()->local().inventory.countType(190)==0,"Craft consumes remaining sticks");
  require(hud().filtered(local().inventory).isEmpty(),"Craftable filter updates to empty after craft");capture(id,out.resolve("recipe-book-empty.png"));
  hit(id,"filter",-1);hit(id,"category",0);
  step="equip and drop";
  int toolSlot=0;while(local().inventory.type(toolSlot)!=191)toolSlot++;
  int maximumBefore=Crafting.recipe(96).maximum(local().inventory);
  hit(id,"slot",toolSlot);hit(id,"slot",0);require(local().inventory.type(0)==191,"Tool moved to hotbar by real inventory clicks");
  require(Crafting.recipe(96).maximum(local().inventory)==maximumBefore,"Availability follows all slots after moving");
  x("key","e");onFrame(()->{warmFixture();return null;});
  x("key","1");await(()->player().heldItem()==191,"Equipped wooden pickaxe");capture(id,out.resolve("recipe-book-held.png"));mark("held-wooden-pickaxe");
  for(int item=192;item<=198;item++) {
   final int tool=item;
   onFrame(()->{int slot=0;while(local().inventory.type(slot)!=tool)slot++;local().inventory.swap(slot,0);return null;});
   x("key","1");await(()->player().heldItem()==tool,"Equipped variant "+Items.name(tool));
   capture(id,out.resolve("recipe-book-held-"+tool+".png"));
   int count=local().inventory.countType(tool);x("key","q");await(()->local().inventory.countType(tool)==count-1,"Dropped variant "+tool);
   onFrame(()->{player().driveSeat(new org.joml.Vector3f(11.5f,fixtureY+1,24.5f),-90);return null;});
   await(()->local().inventory.countType(tool)==count,"Picked up variant "+tool);
   onFrame(()->{player().driveSeat(new org.joml.Vector3f(8.5f,fixtureY+1,24.5f),-90);return null;});
  }
  onFrame(()->{int slot=0;while(local().inventory.type(slot)!=191)slot++;local().inventory.swap(slot,0);((World)get("world")).apply(new Protocol.Edit(8,fixtureY+2,21,3));warmFixture();return null;});
  x("key","1");await(()->player().heldItem()==191,"Wooden pickaxe restored");
  int before=local().inventory.countType(191);x("click","3");require(local().inventory.countType(191)==before,"Tool cannot place as terrain");
  require(((String)get("notice")).contains("cannot be placed"),"Right-click reached non-placeable tool validation");
  x("key","q");await(()->local().inventory.countType(191)==0,"Q drops held tool");capture(id,out.resolve("recipe-book-drop.png"));
  onFrame(()->{player().driveSeat(new org.joml.Vector3f(11.5f,fixtureY+1,24.5f),-90);return null;});
  await(()->local().inventory.countType(191)==1,"Tool pickup restores inventory");
  onFrame(()->{player().driveSeat(new org.joml.Vector3f(8.5f,fixtureY+1,24.5f),-90);return null;});
  step="goods and small layout";
  x("key","e");hit(id,"book",-1);craft(id,89,4);capture(id,out.resolve("recipe-book-goods.png"));mark("brick-goods-recipe");
  x("windowsize",id,"640","480");await(()->(int)get("framebufferWidth")==640,"Small window");
  search(id,"pickaxe");capture(id,out.resolve("recipe-book-small-grid.png"));hit(id,"recipe",100);capture(id,out.resolve("recipe-book-small-detail.png"));
  recipe(id,93);hit(id,"ingredient",4);capture(id,out.resolve("recipe-book-small-goods.png"));
  x("key","e");x("windowsize",id,"1280","720");await(()->(int)get("framebufferWidth")==1280,"Ordinary window restored");
  onFrame(()->{var old=local().inventory;local().save();var reload=new LocalGame(Path.of("target/recipe-profile/world.dat"),0);
   for(int tool=191;tool<=198;tool++)require(reload.inventory.countType(tool)==old.countType(tool)&&old.countType(tool)>=1,"Saved tool "+tool);
   local().inventory=new Inventory();local().inventory.add(3,64);local().inventory.add(2,35*64);return null;
  });
  step="overflow";
  x("key","e");hit(id,"book",-1);recipe(id,16); // Stone cut: legacy stable ID 16.
  hit(id,"craft",16);await(()->local().drops.values().stream().anyMatch(d->d.type()==137+8),"Fractional overflow spawned");
  capture(id,out.resolve("recipe-book-overflow.png"));mark("overflow-drops");
  onFrame(()->{local().inventory.consume(Map.of(2,64));return null;});
  await(()->local().drops.isEmpty()&&local().inventory.countType(145)==8,"Overflow pickup after freeing one slot");
  capture(id,out.resolve("recipe-book-overflow-pickup.png"));mark("overflow-pickup");
  x("key","e");x("key","F10");
  Files.write(out.resolve("recipe-video-scenes.tsv"),scenes);
  Files.writeString(out.resolve("recipe-book-playtest.txt"),"Playtest: PASS. Linux X11, software OpenGL, isolated synthetic Sandbox profile, production GLFW callbacks and real X11 input. Raw-material fixture: one wood and one stone harvested and picked up; four wood and twenty stone supplied for the complete recipe tour. E releases mouse; pickaxe search exposes both unavailable variants; disabled crafting loses nothing; W suppressed while book open; Escape clears search focus first. Maximum five wood batches produce twenty planks; four stick batches produce sixteen sticks. Ingredient link and Back work; stone source shown. All eight tools crafted via book and equipped/dropped/picked up; seven additional variants use controlled slot swaps plus real hotbar and Q input; Tools and Materials categories verified with Tab/Enter; Craftable now becomes empty after consuming the last sticks with an extra stone shovel; wooden pickaxe moved into the hotbar by real inventory clicks with ingredient totals retained, equipped, right-click cannot place it, Q drops it, pickup restores it; all eight tools survive saved inventory reload. Brick goods recipe crafted. Ordinary 1280x720 and small 640x480 grid/detail captures. Legacy Stone cut ID16 creates eight overflow pieces with full inventory; freeing one slot picks them up. F10 records the tour. Expected results matched observed assertions.\n");
 }
 public static void main(String[] args) throws Exception {
  Path out=Path.of(args[0]);outPath=out;Files.createDirectories(out);glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
  set("offlineSave",Path.of("target/recipe-profile/world.dat").toAbsolutePath());set("gameConfig",GameConfig.sandbox());
  var driver=new Thread(()->{long handle=0;try{
   String id="";for(int i=0;i<180;i++){if(get("player")!=null&&(int)get("fps")>=0){Thread.sleep(1000);handle=((dev.jayms.window.Window)get("window")).getHandle();id=x("search","--name","^Voxel One").lines().findFirst().orElseThrow();break;}Thread.sleep(500);}
   require(!id.isEmpty(),"Engine ready");x("windowfocus",id);if(((ControlsMenu)get("menu")).open)x("key","Escape");play(id,out);
  }catch(Throwable e){failure=e;try{Files.writeString(out.resolve("recipe-book-playtest.txt"),"FAIL: "+e.getClass().getSimpleName()+": "+e.getMessage()+" at "+step+"\n");}catch(Exception ignored){}}
   finally{if(handle!=0)glfwSetWindowShouldClose(handle,true);}});
  driver.setDaemon(true);driver.start();try { game.run(new Main.FrameObserver(){public void beforeFrame(Main app){for(var j=jobs.poll();j!=null;j=jobs.poll())j.run();}public void afterFrame(Main app){frames.incrementAndGet();}}); } catch(Throwable e) { Files.writeString(out.resolve("recipe-book-playtest.txt"),"FAIL: engine "+e.getClass().getSimpleName()+": "+e.getMessage()+" at "+step+"\n"); throw e; } driver.join(1000);
  if(failure!=null)throw new AssertionError("Recipe book playtest failed");require(Files.exists(out.resolve("recipe-book-playtest.txt")),"Driver completed");
 }
}
