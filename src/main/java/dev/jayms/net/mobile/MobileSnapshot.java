package dev.jayms.net.mobile;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.net.model.*;
import java.util.*;

/** Versioned mobile view retaining authoritative fractional and model leaf geometry. */
public final class MobileSnapshot {
    private MobileSnapshot() {}
    public static Map<String,Object> capture(WorldVoxels world, Terrain terrain, Protocol.Pose pose,
            Inventory inventory, int health, CityFrame city, String notice, int radius) {
        return capture(world,terrain,pose,inventory,health,city,notice,radius,new ModelLibrary());
    }
    public static Map<String,Object> capture(WorldVoxels world,Terrain terrain,Protocol.Pose pose,
            Inventory inventory,int health,CityFrame city,String notice,int radius,ModelLibrary models) {
        if(radius<8 || radius>24)throw new IllegalArgumentException("Invalid view radius");
        int cx=(int)Math.floor(pose.x()),cz=(int)Math.floor(pose.z());
        int low=Math.max(Terrain.MIN_Y,Math.min(Terrain.MAX_Y-8,(int)Math.floor(pose.y())-8)), high=Math.min(Terrain.MAX_Y,(int)Math.floor(pose.y())+36);
        var cells=new ArrayList<List<Integer>>();var details=new ArrayList<Map<String,Object>>();
        Set<Integer> usedModels=new TreeSet<>();usedModels.add(Blocks.FLOWER_POT);
        for(int slot=0;slot<Inventory.SIZE;slot++)if(inventory.count(slot)>0 && Blocks.isModel(inventory.type(slot)))usedModels.add(inventory.type(slot));
        int expanded=0;
        for(int x=cx-radius;x<=cx+radius;x++)for(int z=cz-radius;z<=cz+radius;z++)for(int y=low;y<=high;y++){
            int type=world.type(x,y,z);
            if(type==Blocks.PARTIAL) {
                var geometry=geometry(world.cell(x,y,z),false);
                details.add(Map.of("cell",List.of(x,y,z),"geometry",geometry));
                expanded+=((List<?>)geometry.get("leaves")).size();
            } else if(Blocks.isModel(type)) {
                var model=models.get(type);if(model==null)throw new IllegalArgumentException("Mobile model definition unavailable");
                usedModels.add(type);expanded+=model.definition().voxels().leaves().size();
            }
            if(expanded>32768 || details.size()>512)throw new IllegalArgumentException("Mobile detail budget exceeded");
            if(type!=0)cells.add(List.of(x,y,z,type,type==Blocks.PARTIAL?0:WorldVoxels.color(type)&0xffffff));
        }
        var slots=new ArrayList<List<Integer>>();
        for(int i=0;i<Inventory.SIZE;i++)slots.add(List.of(inventory.type(i),inventory.count(i)));
        var result=new LinkedHashMap<String,Object>();
        result.put("recipes",recipeCatalog());
        result.put("schema",2);result.put("details",details);result.put("contract",contract(models,usedModels));result.put("game",city.config().city()?"city":"sandbox");result.put("seed",Long.toString(terrain.seed));
        result.put("bounds",List.of(cx-radius,cx+radius,low,high,cz-radius,cz+radius));
        result.put("pose",List.of(pose.x(),pose.y(),pose.z(),(float)Math.toRadians(pose.yaw()),(float)Math.toRadians(pose.pitch())));result.put("cells",cells);
        result.put("inventory",slots);result.put("health",health);result.put("notice",notice);
        var atmosphere=city.config().atmosphere();
        var values=new ArrayList<Double>();
        for(double v:new double[]{atmosphere.radius(),atmosphere.height(),atmosphere.metresPerBlock(),atmosphere.seaLevel()})values.add(v);
        for(var v:List.of(atmosphere.origin(),atmosphere.up(),atmosphere.molecular(),atmosphere.aerosolScattering(),atmosphere.aerosolExtinction(),atmosphere.absorption())){values.add(v.x());values.add(v.y());values.add(v.z());}
        for(double v:new double[]{atmosphere.molecularScale(),atmosphere.aerosolScale(),atmosphere.absorptionCentre(),atmosphere.absorptionWidth(),atmosphere.anisotropy()})values.add(v);
        for(var v:List.of(atmosphere.groundAlbedo(),atmosphere.solarIrradiance())){values.add(v.x());values.add(v.y());values.add(v.z());}values.add(atmosphere.solarRadius());
        result.put("atmosphere",Map.of("version",1,"enabled",atmosphere.enabled(),"values",values));
        double angle=(city.config().hour(city.elapsed())-6)/24*Math.PI*2;
        double sx=Math.cos(angle),sy=Math.sin(angle),sz=-.35;
        double length=Math.sqrt(sx*sx+sy*sy+sz*sz);
        result.put("sun",List.of(sx/length,sy/length,sz/length));
        if(city.config().city()){
            var c=new LinkedHashMap<String,Object>();c.put("time",city.config().time(city.elapsed()).label());
            c.put("treasury",city.economy().budget());
            c.put("roads",city.roads().stream().map(r->List.of(r.x(),r.y(),r.z(),r.type())).toList());
            c.put("buildings",city.buildings().stream().map(b->Map.of("id",b.id(),"type",b.type(),"x",b.x(),"y",b.y(),"z",b.z(),"stock",b.stock())).toList());
            c.put("citizens",city.visibleCitizens().stream().map(n->Map.of("id",n.id(),"name",n.name(),"x",n.x(),"y",n.y(),"z",n.z(),"activity",n.activity(),"money",n.money(),"hunger",n.hunger())).toList());
            c.put("zonePolygons",city.zones().stream().map(zone->Map.of("type",zone.type(),"points",zone.polygon().vertices().stream().map(p->List.of(p.x(),p.z())).toList())).toList());
            c.put("zones",city.zones().size());result.put("city",c);
        }
        return result;
    }
    public static Map<String,Object> geometry(SparseVoxelOctree tree,boolean model) {
        var leaves=tree.leaves().stream().map(leaf->List.of(leaf.x(),leaf.y(),leaf.z(),leaf.side(),
            model?Blocks.FLOWER_POT:WorldVoxels.decode(leaf.color()),
            (model?leaf.color():WorldVoxels.surfaceColor(leaf.color()))&0xffffff)).toList();
        if(leaves.size()>32768)throw new IllegalArgumentException("Mobile geometry budget exceeded");
        return new TreeMap<>(Map.of("resolution",tree.size(),"leaves",leaves));
    }
    public static Map<String,Object> contract(ModelLibrary library,Set<Integer> ids) {
        var materials=java.util.stream.IntStream.rangeClosed(1,189).mapToObj(id->new TreeMap<>(Map.of(
            "id",id,"name",Blocks.name(id),"material",Blocks.material(id),"depth",Blocks.depth(id),"color",WorldVoxels.color(id)&0xffffff))).toList();
        var models=ids.stream().sorted().map(id->{var e=library.get(id);
            if(e==null)throw new IllegalArgumentException("Mobile model definition unavailable");
            return new TreeMap<>(Map.of("id",id,"fingerprint",e.definition().fingerprint(),"geometry",geometry(e.definition().voxels(),true)));}).toList();
        return new TreeMap<>(Map.of("version",1,"fineEdits",true,"materials",materials,"models",models));
    }
    /** Whole-block recipes only until the mobile detail/tool schema is available. */
    public static List<Map<String,Object>> recipeCatalog() {
        return java.util.stream.IntStream.of(88,89,90,91,92,94).mapToObj(id -> {
            var r=Crafting.recipe(id);
            return Map.<String,Object>of("id",r.id(),"name",r.name(),"inputs",r.inputs().entrySet().stream()
                .map(e->List.of(e.getKey(),e.getValue())).toList(),"output",r.output(),"count",r.count());
        }).toList();
    }
    public static void main(String[] args) throws Exception {
        if(args.length==2 && args[0].equals("--contract")) {
            java.nio.file.Files.writeString(java.nio.file.Path.of(args[1]),Json.write(contract(new ModelLibrary(),Set.of(8))));return;
        }
        if(args.length==2 && args[0].equals("--recipes")) {
            java.nio.file.Files.writeString(java.nio.file.Path.of(args[1]),Json.write(recipeCatalog()));return;
        }
        if(args.length!=1)throw new IllegalArgumentException("Usage: OUTPUT_JSON");
        var terrain=new Terrain(Terrain.DEFAULT_SEED);var world=new WorldVoxels(terrain);
        int y=terrain.surfaceHeight(0,0)+1;var inventory=new Inventory();
        inventory.add(Blocks.DIRT,32);inventory.add(Blocks.STONE,32);inventory.add(Blocks.WOOD,32);inventory.add(Blocks.PLANKS,32);inventory.add(Blocks.GLASS,16);
        var pose=new Protocol.Pose(1,.5f,y,.5f,0,0,0,0,false,0,0,false);
        java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]),Json.write(capture(world,terrain,pose,inventory,20,CityFrame.empty(GameConfig.sandbox()),"",24)));
    }
}
