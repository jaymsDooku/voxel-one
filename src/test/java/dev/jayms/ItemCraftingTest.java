package dev.jayms;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ItemCraftingTest {
 @TempDir Path temp;
 @Test void legacyByteIdentitiesAndWorldValidationStaySeparate() throws Exception {
  assertEquals(36,Inventory.SIZE); assertEquals(9,Inventory.HOTBAR);
  for(int id=0;id<190;id++) { assertTrue(Items.valid(id)); assertEquals(Blocks.name(id),Items.name(id)); assertEquals(id,Items.get(id).placeableBlock()); }
  for(int id=190;id<=198;id++) { assertTrue(Items.valid(id)); assertFalse(Blocks.valid(id)); assertFalse(Items.placeable(id)); assertFalse(new Protocol.Edit(8,60,24,id).valid()); }
  assertFalse(Items.valid(199)); assertFalse(Items.valid(255));
  var inv=new Inventory();inv.add(Blocks.ROAD_LINE_Z,64);inv.add(Items.STONE_AXE,2);
  var bytes=new ByteArrayOutputStream();inv.write(new DataOutputStream(bytes));
  assertEquals(72,bytes.size());assertEquals(189,Byte.toUnsignedInt(bytes.toByteArray()[0]));
  var restored=Inventory.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
  assertEquals(64,restored.countType(189));assertEquals(2,restored.countType(198));
  assertThrows(IOException.class,()->Inventory.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),false));
 }
 @Test void toolsFillSeparateSlotsAndNeverMergePastOne() {
  var inv=new Inventory();assertEquals(4,inv.add(191,40));
  for(int i=0;i<36;i++) assertEquals(1,inv.count(i));
  inv.swap(0,1);assertEquals(1,inv.count(0));assertEquals(1,inv.count(1));
  assertFalse(inv.hasSpace(191)); assertTrue(inv.take(8,191)); assertTrue(inv.hasSpace(191));
 }
 @Test void invalidToolStackAndGroundStackCannotBeLoaded() throws Exception {
  byte[] bytes=new byte[72];bytes[0]=(byte)191;bytes[1]=2;
  assertThrows(IOException.class,()->Inventory.read(new DataInputStream(new ByteArrayInputStream(bytes))));
  var out=new ByteArrayOutputStream();new ItemDrop(1,191,2,8,60,24).write(new DataOutputStream(out));
  assertThrows(IOException.class,()->ItemDrop.read(new DataInputStream(new ByteArrayInputStream(out.toByteArray()))));
 }
 @Test void stableLegacyRecipeIdsAndValidatedDefinitions() {
  assertEquals(Blocks.PLANKS,Crafting.recipe(88).output());assertEquals(Blocks.LED,Crafting.recipe(94).output());
  assertEquals(16,Crafting.ingredientRecipe(Blocks.piece(Blocks.STONE,1)).id());assertNull(Crafting.ingredientRecipe(Blocks.STONE));
  assertEquals(Blocks.piece(1,1),Crafting.recipe(0).output());assertEquals(1,Crafting.recipe(1).output());
  int ordinal=0;
  for(int material:new int[]{1,2,3,4,5,6,7,165,166,167,180}) {
   for(int depth=1;depth<=4;depth++) {
    int cut=ordinal*8+(depth-1)*2,parent=Blocks.piece(material,depth-1),child=Blocks.piece(material,depth);
    assertEquals(Map.of(parent,1),Crafting.recipe(cut).inputs());assertEquals(child,Crafting.recipe(cut).output());assertEquals(8,Crafting.recipe(cut).count());
    assertEquals(Map.of(child,8),Crafting.recipe(cut+1).inputs());assertEquals(parent,Crafting.recipe(cut+1).output());assertEquals(1,Crafting.recipe(cut+1).count());
   } ordinal++;
  }
  var good=new Crafting.Recipe(200,"Test",Map.of(3,1),165,1);
  assertDoesNotThrow(()->Crafting.validateCatalog(List.of(good)));
  assertThrows(IllegalArgumentException.class,()->Crafting.validateCatalog(List.of(good,good)));
  assertThrows(IllegalArgumentException.class,()->Crafting.validateCatalog(List.of(new Crafting.Recipe(201,"Bad",Map.of(199,1),165,1))));
  assertThrows(IllegalArgumentException.class,()->Crafting.validateCatalog(List.of(new Crafting.Recipe(201,"Bad",Map.of(3,0),165,1))));
  assertThrows(IllegalArgumentException.class,()->Crafting.validateCatalog(List.of(new Crafting.Recipe(201,"Bad",Map.of(3,1),0,1))));
  assertThrows(ArithmeticException.class,()->Crafting.validateCatalog(List.of(new Crafting.Recipe(201,"Big",Map.of(3,1),165,Integer.MAX_VALUE))));
  assertThrows(ArithmeticException.class,()->Crafting.validateCatalog(List.of(new Crafting.Recipe(201,"Big",Map.of(3,Integer.MAX_VALUE),165,1))));
 }
 @Test void batchesCountIngredientsAcrossSlotsAndFreeSpaceBeforeOutputs() {
  var inv=new Inventory();inv.add(165,128);inv.add(190,128);
  var r=Crafting.prepare(inv,96,32,0);assertTrue(r.accepted());assertEquals(32,r.inventory().countType(191));
  assertEquals(32,r.inventory().countType(165));assertEquals(64,r.inventory().countType(190));assertEquals(0,r.excess());
  assertEquals(128,inv.countType(165));
  var full=new Inventory();full.add(165,3);full.add(190,2);full.add(2,34*64);
  var freed=Crafting.prepare(full,96,1,0);assertTrue(freed.accepted());assertEquals(1,freed.inventory().countType(191));assertEquals(0,freed.excess());
 }
 @Test void capacityFailureAndBadRequestsLoseNothing() {
  var inv=new Inventory(); inv.add(165,64); inv.add(190,64);inv.add(2,34*64);
  assertFalse(Crafting.prepare(inv,96,2,1).accepted()); assertEquals(64,inv.countType(165));
  var r=Crafting.prepare(inv,96,2,2); assertTrue(r.accepted());assertEquals(2,r.excess());
  for(int q:new int[]{Integer.MIN_VALUE,-1,0,65,Integer.MAX_VALUE}) assertFalse(Crafting.prepare(inv,96,q).accepted());
  assertFalse(Crafting.prepare(inv,999,1).accepted());assertFalse(Crafting.prepare(new Inventory(),96,1).accepted());
 }
 @Test void allToolsHaveExactCostsAndDistinctVoxelModelsOutsideCustomLibrary() throws Exception {
  var models=new ModelLibrary(); Set<String> fingerprints=new HashSet<>();
  for(int id=96;id<=103;id++) {
   var recipe=Crafting.recipe(id);
   assertEquals(Map.of(id<100?165:3,new int[]{3,2,1,3}[id%4],190,new int[]{2,1,2,2}[id%4]),recipe.inputs());
   assertEquals(1,recipe.count());var inv=new Inventory();recipe.inputs().forEach(inv::add);
   var r=Crafting.prepare(inv,id);assertTrue(r.accepted());assertEquals(1,r.inventory().countType(recipe.output()));
   assertTrue(fingerprints.add(models.get(recipe.output()).definition().fingerprint()));
   assertEquals(1,Items.stackLimit(recipe.output()));assertEquals(3,recipe.diagram().size());
  }
  var out=new ByteArrayOutputStream();models.write(new DataOutputStream(out));
  assertEquals(4,out.size()); assertEquals(9,models.nextId());
  var reload=ModelLibrary.read(new DataInputStream(new ByteArrayInputStream(out.toByteArray())));assertNotNull(reload.get(198));
 }
 @Test void localToolOverflowIsSplitAndPersistsThenDropsAndPicksUp() throws Exception {
  Path file=temp.resolve("game.dat");var g=new LocalGame(file,42);var pose=new Protocol.Pose(0,8,70,24,0,0);
  g.inventory.add(165,64);g.inventory.add(190,64);g.inventory.add(2,34*64);
  assertTrue(g.craft(96,2,pose).contains("2 dropped nearby"));assertEquals(2,g.drops.size());
  assertTrue(g.drops.values().stream().allMatch(d->d.type()==191&&d.count()==1));
  g.save();g=new LocalGame(file,999);assertEquals(42,g.seed);assertEquals(2,g.drops.size());
  g.inventory.consume(Map.of(2,128));g.tick(pose,true,.016f,e->3);
  assertEquals(2,g.inventory.countType(191));assertTrue(g.drops.isEmpty());
  int slot=0;while(g.inventory.type(slot)!=191)slot++;
  assertTrue(g.drop(slot,pose).contains("Wooden pickaxe"));assertEquals(1,g.drops.size());
  g.tick(new Protocol.Pose(0,11,70,24,0,0),true,.016f,e->3);assertTrue(g.drops.isEmpty());assertEquals(2,g.inventory.countType(191));
  g.save();var reload=new LocalGame(file,42);assertEquals(2,reload.inventory.countType(191));
 }
 @Test void maximumAndIngredientSearchFollowChangedInventoryAndBackHasNoCycle() {
  var inv=new Inventory();inv.add(165,9); inv.add(190,4);assertEquals(2,Crafting.recipe(96).maximum(inv));
  var hud=new dev.jayms.ui.InventoryHud();hud.open=true;hud.openBook();hud.query("sticks");assertEquals(9,hud.filtered(inv).size());
  hud.query("pickaxe");assertEquals(2,hud.filtered(inv).size());hud.craftableOnly(true);assertEquals(1,hud.filtered(inv).size());
  inv.consume(Map.of(190,4));assertTrue(hud.filtered(inv).isEmpty());assertEquals(96,hud.selectedRecipe());
  hud.craftableOnly(false);hud.ingredient(190);assertEquals(95,hud.selectedRecipe());hud.ingredient(165);assertEquals(88,hud.selectedRecipe());hud.back();assertEquals(95,hud.selectedRecipe());hud.back();assertEquals(96,hud.selectedRecipe());
  hud.ingredient(3);assertEquals(96,hud.selectedRecipe());
 }
 @Test void localAuthorityRejectsFullDropCapacityAndGroundIdExhaustionAtomically() throws Exception {
  var g=new LocalGame(temp.resolve("capacity.dat"),42);var pose=new Protocol.Pose(0,8,70,24,0,0);
  g.inventory.add(165,64);g.inventory.add(190,64);g.inventory.add(2,34*64);
  for(int id=1;id<=Crafting.MAX_DROPS;id++)g.drops.put(id,new ItemDrop(id,2,1,100,70,100));
  assertTrue(g.craft(96,2,pose).contains("Too many ground items"));assertEquals(64,g.inventory.countType(165));assertEquals(64,g.inventory.countType(190));assertEquals(Crafting.MAX_DROPS,g.drops.size());
  g.drops.clear();var f=LocalGame.class.getDeclaredField("nextDrop");f.setAccessible(true);f.setInt(g,Integer.MAX_VALUE);
  assertTrue(g.craft(96,2,pose).contains("IDs exhausted"));assertEquals(64,g.inventory.countType(165));assertTrue(g.drops.isEmpty());
 }
 @Test void heldDropNearWorldBoundaryStaysReachable() throws Exception {
  var g=new LocalGame(temp.resolve("edge.dat"),42);g.inventory.add(191,1);
  var pose=new Protocol.Pose(0,Terrain.LIMIT,70,24,0,0);assertTrue(g.drop(0,pose).contains("Dropped"));
  var d=g.drops.values().iterator().next();assertEquals(Terrain.LIMIT-3,d.x());
  g.tick(new Protocol.Pose(0,d.x(),70,24,0,0),true,.016f,e->3);assertEquals(1,g.inventory.countType(191));assertTrue(g.drops.isEmpty());
 }
 @Test void searchCaretEditingAndEscapeDoNotActivateGameplayOrCloseEarly() throws Exception {
  var hud=new dev.jayms.ui.InventoryHud();hud.open=true;hud.openBook();
  var f=dev.jayms.ui.InventoryHud.class.getDeclaredField("searchFocus");f.setAccessible(true);f.setBoolean(hud,true);
  var inv=new Inventory();hud.character('a');hud.character('c');hud.key(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT,inv,inv::swap,(a,b)->fail("Unexpected craft"));hud.character('b');assertEquals("abc",hud.search());
  hud.key(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE,inv,inv::swap,(a,b)->fail("Unexpected craft"));assertEquals("ac",hud.search());
  hud.key(org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE,inv,inv::swap,(a,b)->fail("Unexpected craft"));assertEquals("a",hud.search());
  hud.key(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,inv,inv::swap,(a,b)->fail("Unexpected craft"));assertTrue(hud.open);assertTrue(hud.bookOpen());assertFalse(hud.searchFocused());
  hud.key(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,inv,inv::swap,(a,b)->fail("Unexpected craft"));assertTrue(hud.open);assertFalse(hud.bookOpen());
  hud.key(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,inv,inv::swap,(a,b)->fail("Unexpected craft"));assertFalse(hud.open);
 }

}
