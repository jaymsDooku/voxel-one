package dev.jayms.net.city;

import dev.jayms.MeshData;
import java.util.*;

/** Thin painted polygons follow the route, clipped to the surviving road surface. */
public final class RoadMarkings {
    private RoadMarkings() {}
    public static MeshData mesh(CityFrame city) {
        var roads = new HashMap<Polygon.Cell,CityFrame.Road>();
        for (var r : city.roads()) roads.put(new Polygon.Cell(r.x(),r.z()),r);
        var footprints = RoadOwnership.forFrame(city);
        var owners = RoadOwnership.visible(footprints);
        var vertices = new ArrayList<Float>();
        var indices = new ArrayList<Integer>();
        for (var footprint : footprints) {
            for(int type=1;type<=3;type++) {
                var street = city.addresses().streets().stream().filter(s->s.id()==footprint.street()).findFirst();
                if (street.isEmpty()) continue;
                var route = RoadRoute.points(street.get().route());
                int radius = RoadTypes.width(type)/2;
                var cells = new HashSet<Polygon.Cell>();
                for (var c:footprint.cells()) if (c.type()==type && c.equals(owners.get(c.cell()))) cells.add(c.cell());
                if(cells.isEmpty())continue;
                for (int i=1;i<route.size();i++) {
                    var a=route.get(i-1); var b=route.get(i);
                    float dx=b.x()-a.x(), dz=b.z()-a.z();
                    float length=(float)Math.hypot(dx,dz);
                    if (length==0) continue;
                    float nx=-dz/length, nz=dx/length;
                    // The raster footprint measures lane spacing on its dominant grid axis.
                    float spacing=Math.max(Math.abs(dx),Math.abs(dz))/length;
                    for (int lane=-radius+1;lane<radius;lane+=2) {
                        float offset=lane*spacing, half=.0625f;
                        var quad=List.of(edge(route,i-1,lane,offset-half,nx,nz),
                                edge(route,i,lane,offset-half,nx,nz),
                                edge(route,i,lane,offset+half,nx,nz),
                                edge(route,i-1,lane,offset+half,nx,nz));
                        float minX=Float.MAX_VALUE,minZ=Float.MAX_VALUE,maxX=-Float.MAX_VALUE,maxZ=-Float.MAX_VALUE;
                        for(var p:quad){minX=Math.min(minX,p.x());minZ=Math.min(minZ,p.z());maxX=Math.max(maxX,p.x());maxZ=Math.max(maxZ,p.z());}
                        for(var cell:cells) {
                            if(cell.x()+1<minX||cell.x()>maxX||cell.z()+1<minZ||cell.z()>maxZ)continue;
                            var r=roads.get(cell); if(r==null||r.type()==0)continue;
                            List<Polygon.Point> clipped=quad;
                            clipped=clip(clipped,true,cell.x(),true);
                            clipped=clip(clipped,true,cell.x()+1,false);
                            clipped=clip(clipped,false,cell.z(),true);
                            clipped=clip(clipped,false,cell.z()+1,false);
                            if(clipped.size()<3)continue;
                            int base=vertices.size()/9;
                            for(var p:clipped) for(float v:new float[]{p.x(),r.y()+1.003f,p.z(),0,1,0,.95f,.95f,.9f})vertices.add(v);
                            // Quad order is clockwise from above; reverse for upward-facing triangles.
                            for(int j=1;j<clipped.size()-1;j++){indices.add(base);indices.add(base+j+1);indices.add(base+j);}
                        }
                    }
                }
            }
        }
        float[] v=new float[vertices.size()];for(int i=0;i<v.length;i++)v[i]=vertices.get(i);
        return new MeshData(v,indices.stream().mapToInt(Integer::intValue).toArray());
    }
    private static Polygon.Point edge(List<Polygon.Point> route,int i,int lane,float offset,float nx,float nz) {
        var p=route.get(i);
        if(i>0&&i<route.size()-1) {
            var a=route.get(i-1);var b=route.get(i+1);
            float dx=p.x()-a.x(),dz=p.z()-a.z(),ex=b.x()-p.x(),ez=b.z()-p.z();
            float l=(float)Math.hypot(dx,dz),m=(float)Math.hypot(ex,ez);
            if(l>0&&m>0) {
                float ux=-dz/l,uz=dx/l,vx=-ez/m,vz=ex/m;
                float determinant=ux*vz-uz*vx;
                if(Math.abs(determinant)>.001f) {
                    float half=offset-lane*Math.max(Math.abs(nx),Math.abs(nz));
                    float first=lane*Math.max(Math.abs(ux),Math.abs(uz))+half;
                    float second=lane*Math.max(Math.abs(vx),Math.abs(vz))+half;
                    float x=(first*vz-uz*second)/determinant;
                    float z=(ux*second-first*vx)/determinant;
                    if(Math.hypot(x,z)<=2*(Math.abs(lane)+1))return new Polygon.Point(p.x()+.5f+x,p.z()+.5f+z);
                }
            }
        }
        return new Polygon.Point(p.x()+.5f+nx*offset,p.z()+.5f+nz*offset);
    }
    private static List<Polygon.Point> clip(List<Polygon.Point> input,boolean x,float boundary,boolean greater) {
        var result=new ArrayList<Polygon.Point>();if(input.isEmpty())return result;
        var a=input.get(input.size()-1);float av=x?a.x():a.z();boolean ai=greater?av>=boundary:av<=boundary;
        for(var b:input){float bv=x?b.x():b.z();boolean bi=greater?bv>=boundary:bv<=boundary;
            if(ai!=bi){float t=(boundary-av)/(bv-av);result.add(new Polygon.Point(a.x()+t*(b.x()-a.x()),a.z()+t*(b.z()-a.z())));}
            if(bi)result.add(b);a=b;av=bv;ai=bi;
        }return result;
    }
}
