package dev.jayms.physics;

import java.util.*;
import java.util.function.Function;

/** Integer cell keys avoid hash aliasing; a body occupies every cell its bounds touch. */
public final class SpatialGrid<T> {
    private record Key(int x,int y,int z) {}
    private final float cellSize;
    private final Map<Key,List<T>> buckets=new HashMap<>();
    public SpatialGrid(float cellSize){if(!Float.isFinite(cellSize)||cellSize<=0)throw new IllegalArgumentException("Positive cell size required");this.cellSize=cellSize;}
    private List<Key> keys(Aabb b) {
        for(int a=0;a<3;a++)if(b.min(a)/cellSize<=Integer.MIN_VALUE+256f||b.max(a)/cellSize>=Integer.MAX_VALUE-256f)throw new IllegalArgumentException("Broadphase bounds exceed integer grid");
        List<Key> keys=new ArrayList<>();
        int x0=(int)Math.floor(b.x0()/cellSize),y0=(int)Math.floor(b.y0()/cellSize),z0=(int)Math.floor(b.z0()/cellSize);
        int x1=(int)Math.floor(b.x1()/cellSize),y1=(int)Math.floor(b.y1()/cellSize),z1=(int)Math.floor(b.z1()/cellSize);
        if((long)x1-x0+1>262144||(long)y1-y0+1>262144||(long)z1-z0+1>262144
                ||((long)x1-x0+1)*((long)y1-y0+1)*((long)z1-z0+1)>262144)throw new IllegalArgumentException("Broadphase bounds too large");
        for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)keys.add(new Key(x,y,z));
        return keys;
    }
    public void rebuild(Collection<T> bodies,Function<T,Aabb> bounds){buckets.clear();for(T body:bodies)for(Key k:keys(bounds.apply(body)))buckets.computeIfAbsent(k,key->new ArrayList<>()).add(body);}
    public Set<T> query(Aabb bounds){Set<T> result=new LinkedHashSet<>();for(Key k:keys(bounds))result.addAll(buckets.getOrDefault(k,List.of()));return result;}
}
