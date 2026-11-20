package dev.jayms.net;

import dev.jayms.net.model.*;
import java.util.*;

/** Inventory byte IDs. 0..189 keep their legacy world meaning; 190..198 are items only. */
public final class Items {
    public static final int STICKS=190, WOODEN_PICKAXE=191, WOODEN_SWORD=192,
            WOODEN_SHOVEL=193, WOODEN_AXE=194, STONE_PICKAXE=195,
            STONE_SWORD=196, STONE_SHOVEL=197, STONE_AXE=198;
    public record Definition(int id, String name, String description, String category,
                             int stackLimit, int placeableBlock, ModelDefinition model) {}
    private static final Map<Integer,Definition> DEFINITIONS = build();
    private static Map<Integer,Definition> build() {
        Map<Integer,Definition> result=new LinkedHashMap<>();
        for(int id=0;id<190;id++) {
            String category=id==Blocks.LED||Blocks.material(id)==Blocks.LED?"Lighting":
                    id==Blocks.FLOWER_POT||Blocks.isModel(id)?"Decorations":
                    id==Blocks.WOOD||id==Blocks.LEAVES?"Materials":"Building";
            result.put(id,new Definition(id,Blocks.name(id), id==0?"Empty slot":"Placeable "+Blocks.name(id), category,64,id,null));
        }
        result.put(STICKS,new Definition(STICKS,"Sticks","Handles made from planks.","Materials",64,0,model(STICKS,"Sticks")));
        String[] names={"Wooden pickaxe","Wooden sword","Wooden shovel","Wooden axe",
                        "Stone pickaxe","Stone sword","Stone shovel","Stone axe"};
        for(int i=0;i<names.length;i++) {
            int id=191+i;
            result.put(id,new Definition(id,names[i],"Carry and equip. No mining or combat bonus.","Tools",1,0,model(id,names[i])));
        }
        return Collections.unmodifiableMap(result);
    }
    private static ModelDefinition model(int id,String name) {
        int handle=0xff996337, edge=id>=195?0xffb4c5cd:0xffd69a51, shadow=id>=195?0xff607885:0xff895529;
        if (id == WOODEN_SWORD || id == STONE_SWORD) {
            var sword = new SparseVoxelOctree(32);
            sword.fill(14, 2, 14, 18, 14, 18, handle);
            sword.fill(10, 12, 14, 22, 14, 18, shadow);
            int bevel = id == STONE_SWORD ? 0xffd4e1df : 0xffe9b969;
            swordSection(sword, 13, 19, 14, 22, edge, bevel);
            swordSection(sword, 14, 19, 22, 25, edge, bevel);
            swordSection(sword, 14, 18, 25, 27, edge, bevel);
            swordSection(sword, 15, 18, 27, 29, edge, bevel);
            swordSection(sword, 15, 17, 29, 31, edge, bevel);
            swordSection(sword, 16, 17, 31, 32, edge, bevel);
            return new ModelDefinition(name, sword.freeze());
        }
        var t=new SparseVoxelOctree(16);
        t.fill(7,1,7,9,12,9,handle);
        if(id==STICKS) { t.fill(4,3,7,6,14,9,0xffd59b5e); }
        else switch((id-191)%4) {
            case 0 -> { t.fill(2,12,6,14,14,10,edge); t.fill(1,10,7,3,13,9,shadow); t.fill(13,10,7,15,13,9,shadow); }
            case 2 -> { t.fill(5,11,6,11,15,10,edge); t.fill(6,10,6,10,12,10,shadow); }
            case 3 -> { t.fill(2,10,6,8,15,10,edge); t.fill(1,11,6,3,14,10,shadow); t.fill(8,12,7,11,14,9,shadow); }
        }
        return new ModelDefinition(name,t.freeze());
    }

    private static void swordSection(SparseVoxelOctree sword, int left, int right, int bottom, int top,
                                     int face, int bevel) {
        sword.fill(left, bottom, 16, right, top, 17, bevel);
        if (right - left > 2) {
            // The blade flat is two voxels deep. The lighter cutting edges are one.
            sword.fill(left + 1, bottom, 15, right - 1, top, 17, face);
        }
    }

    public static Definition get(int id) { return DEFINITIONS.get(id); }
    /** Resolve per-world names and voxel definitions for legacy custom model items. */
    public static Definition get(int id,ModelLibrary models) {
        Definition base=get(id);
        if(base==null)return null;
        var entry=models.get(id);
        return entry==null?base:new Definition(id,entry.definition().name(),base.description(),base.category(),base.stackLimit(),base.placeableBlock(),entry.definition());
    }
    public static float dropX(float x) { return x>Terrain.LIMIT-3?x-3:x+3; }
    public static boolean valid(int id) { return DEFINITIONS.containsKey(id); }
    public static boolean placeable(int id) { return id>0 && valid(id) && get(id).placeableBlock()>0; }
    public static boolean tool(int id) { return id>=191 && id<=198; }
    public static int stackLimit(int id) { var d=get(id); return d==null?0:d.stackLimit(); }
    public static String name(int id) { var d=get(id); return d==null?"Unknown item":d.name(); }
    public static String category(int id) { var d=get(id); return d==null?"All":d.category(); }
    public static String source(int id) {
        return switch(Blocks.material(id)) {
            case Blocks.WOOD -> "Harvest wood from trees.";
            case Blocks.STONE -> "Harvest stone from terrain.";
            case Blocks.SAND -> "Harvest sand from terrain.";
            case Blocks.DIRT -> "Harvest dirt from terrain.";
            case Blocks.LEAVES -> "Harvest leaves from trees.";
            case Blocks.SNOW -> "Harvest snow from snowy terrain.";
            default -> "Harvest from the world or use a material recipe.";
        };
    }
    private Items() {}
}
