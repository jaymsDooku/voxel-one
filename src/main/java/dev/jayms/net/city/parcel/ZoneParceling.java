package dev.jayms.net.city.parcel;

import dev.jayms.net.city.*;
import dev.jayms.net.city.Polygon.Cell;
import dev.jayms.net.city.parcel.ParcelGenerator.*;
import java.util.*;
import java.util.function.ToDoubleFunction;

/** Shared preview/simulation entry point. A parcel owns the complete building site, including entrance. */
public final class ZoneParceling {
    private ZoneParceling() {}
    public static List<Parcel> generate(CityFrame.Zone zone, Collection<Cell> roads, ToDoubleFunction<Cell> cost) {
        var cells=zone.polygon().cells();var roadSet=new HashSet<>(roads);var frontage=new LinkedHashSet<Cell>();
        for(var c:cells)if(ParcelPortfolio.neighbours(c).stream().anyMatch(roadSet::contains))frontage.add(c);
        return ParcelPortfolio.Algorithm.values()[zone.algorithm()].generate(new Request(cells,frontage,
                zone.type()==3?576:144,zone.id(),cost,zone.parcels()));
    }
    public static boolean fits(Parcel parcel,int x,int z,int type) {
        for(int dx=0;dx<StructureBlueprint.width(type);dx++)
            for(int dz=-1;dz<=StructureBlueprint.depth(type);dz++)
                if(!parcel.cells().contains(new Cell(x+dx,z+dz)))return false;
        return true;
    }
    public static List<Cell> sites(CityFrame.Zone zone) {
        if(zone.parcels().isEmpty())return new ArrayList<>(zone.polygon().cells());
        var result=new ArrayList<Cell>();
        for(var p:zone.parcels())for(var c:p.cells())if(fits(p,c.x(),c.z(),zone.type()))result.add(c);
        return List.copyOf(result);
    }
    public static void validate(Polygon polygon,List<Parcel> parcels) {
        if(parcels.isEmpty())return;var remaining=new HashSet<>(polygon.cells());var ids=new HashSet<Integer>();
        if(parcels.size()>4096)throw new IllegalArgumentException("Too many parcels");
        for(var p:parcels){if(p.id()<1||!ids.add(p.id())||p.cells().isEmpty())throw new IllegalArgumentException("Invalid parcel ID or empty parcel");
            for(var c:p.cells())if(!remaining.remove(c))throw new IllegalArgumentException("Parcels overlap or leave zone");
            var visited=new HashSet<Cell>();var queue=new ArrayDeque<Cell>();queue.add(p.cells().iterator().next());
            while(!queue.isEmpty()){var c=queue.remove();if(!visited.add(c))continue;for(var n:ParcelPortfolio.neighbours(c))if(p.cells().contains(n)&&!visited.contains(n))queue.add(n);}
            if(visited.size()!=p.cells().size())throw new IllegalArgumentException("Disconnected parcel");
        }
        if(!remaining.isEmpty())throw new IllegalArgumentException("Parcels must cover the zone");
    }
}
