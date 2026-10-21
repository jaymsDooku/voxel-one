package dev.jayms.net.structure;

import dev.jayms.net.Protocol;
import dev.jayms.net.city.CityFrame;
import dev.jayms.net.model.SparseVoxelOctree;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/** Live commit-time body reservations. Conservative whole-cell policy, including models. */
public final class StructureOccupancy {
    public static void validate(Map<StructurePackage.Position, SparseVoxelOctree> cells,
                                CityFrame city, Collection<Protocol.Pose> players) throws IOException {
        // Probe the few cells under each body instead of scanning a city-sized placement
        // once per body. The caller still holds the authority lock over this live check.
        if(city!=null) {
            for(var c:city.visibleCitizens())check(cells,c.x()-.3f,c.y(),c.z()-.3f,c.x()+.3f,c.y()+1.8f,c.z()+.3f,"city entity");
            for(var h:city.horses())check(cells,h.x()-.7f,h.y(),h.z()-1.2f,h.x()+.7f,h.y()+2.3f,h.z()+1.2f,"city entity");
            for(var c:city.agriculture().cows())check(cells,c.x()-.5f,c.y(),c.z()-.8f,c.x()+.5f,c.y()+1.8f,c.z()+.8f,"city entity");
        }
        for(var p:players)check(cells,p.x()-.3,p.y(),p.z()-.3,p.x()+.3,p.y()+1.8,p.z()+.3,"player");
    }
    private static void check(Map<StructurePackage.Position,SparseVoxelOctree> cells,
                              double minX,double minY,double minZ,double maxX,double maxY,double maxZ,String body)throws IOException {
        if(!Double.isFinite(minX)||!Double.isFinite(minY)||!Double.isFinite(minZ)
                ||!Double.isFinite(maxX)||!Double.isFinite(maxY)||!Double.isFinite(maxZ))
            throw new IOException("Invalid live body bounds; nothing changed");
        if(minX>=maxX||minY>=maxY||minZ>=maxZ
                ||maxX<=Integer.MIN_VALUE||maxY<=Integer.MIN_VALUE||maxZ<=Integer.MIN_VALUE
                ||minX>=2147483648d||minY>=2147483648d||minZ>=2147483648d)return;
        // Clamp in floating point before narrowing; subtracting after a saturated
        // long conversion would wrap for very large negative finite positions.
        long x0=(long)Math.max(Integer.MIN_VALUE,Math.floor(minX)),x1=(long)Math.min(Integer.MAX_VALUE,Math.ceil(maxX)-1d);
        long y0=(long)Math.max(Integer.MIN_VALUE,Math.floor(minY)),y1=(long)Math.min(Integer.MAX_VALUE,Math.ceil(maxY)-1d);
        long z0=(long)Math.max(Integer.MIN_VALUE,Math.floor(minZ)),z1=(long)Math.min(Integer.MAX_VALUE,Math.ceil(maxZ)-1d);
        for(long y=y0;y<=y1;y++)for(long z=z0;z<=z1;z++)for(long x=x0;x<=x1;x++) {
            var tree=cells.get(new StructurePackage.Position((int)x,(int)y,(int)z));
            if(tree!=null&&!tree.isEmpty())throw new IOException("Structure would overlap a "+body+"; nothing changed");
        }
    }
    private StructureOccupancy() {}
}
