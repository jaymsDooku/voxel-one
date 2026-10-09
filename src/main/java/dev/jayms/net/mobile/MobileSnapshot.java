package dev.jayms.net.mobile;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.util.*;

/** Whole-block mobile view of the same authoritative terrain and edit stream. */
public final class MobileSnapshot {
    private MobileSnapshot() {}
    public static Map<String,Object> capture(WorldVoxels world, Terrain terrain, Protocol.Pose pose,
            Inventory inventory, int health, CityFrame city, String notice, int radius) {
        if(radius<8 || radius>24)throw new IllegalArgumentException("Invalid view radius");
        int cx=(int)Math.floor(pose.x()),cz=(int)Math.floor(pose.z());
        int low=Math.max(Terrain.MIN_Y,Math.min(Terrain.MAX_Y-8,(int)Math.floor(pose.y())-8)), high=Math.min(Terrain.MAX_Y,(int)Math.floor(pose.y())+36);
        var cells=new ArrayList<List<Integer>>();
        for(int x=cx-radius;x<=cx+radius;x++)for(int z=cz-radius;z<=cz+radius;z++)for(int y=low;y<=high;y++){
            int type=world.type(x,y,z);
            // A subdivided cell uses its center material in the initial whole-block touch client.
            if(type==Blocks.PARTIAL)type=WorldVoxels.decode(world.cell(x,y,z).get(8,8,8));
            if(type!=0)cells.add(List.of(x,y,z,type,WorldVoxels.color(type)&0xffffff));
        }
        var slots=new ArrayList<List<Integer>>();
        for(int i=0;i<Inventory.SIZE;i++)slots.add(List.of(inventory.type(i),inventory.count(i)));
        var result=new LinkedHashMap<String,Object>();
        result.put("schema",1);result.put("game",city.config().city()?"city":"sandbox");result.put("seed",Long.toString(terrain.seed));
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
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Usage: OUTPUT_JSON");
        var terrain=new Terrain(Terrain.DEFAULT_SEED);var world=new WorldVoxels(terrain);
        int y=terrain.surfaceHeight(0,0)+1;var inventory=new Inventory();
        inventory.add(Blocks.DIRT,32);inventory.add(Blocks.STONE,32);inventory.add(Blocks.WOOD,32);inventory.add(Blocks.PLANKS,32);inventory.add(Blocks.GLASS,16);
        var pose=new Protocol.Pose(1,.5f,y,.5f,0,0,0,0,false,0,0,false);
        java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]),Json.write(capture(world,terrain,pose,inventory,20,CityFrame.empty(GameConfig.sandbox()),"",24)));
    }
}
