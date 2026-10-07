package dev.jayms.net.city.parcel;

import dev.jayms.net.city.Polygon.Cell;
import dev.jayms.net.city.parcel.ParcelGenerator.*;
import java.util.*;

/** Deterministic cell-based portfolio. Skeleton methods are raster approximations, not vector solvers. */
public final class ParcelPortfolio {
    private ParcelPortfolio() {}
    public enum Algorithm implements ParcelGenerator {
        ROAD_FRONTAGE("Recursive Road-Frontage Split", 0),
        FLOOD_FILL("Competitive Voxel Flood-Fill Growth", 0),
        GRID("Grid / Orthogonal Subdivision", 0),
        BSP("Binary Space Partitioning (BSP)", 1),
        WEIGHTED_VORONOI("Weighted Voronoi Parceling", 1),
        ROAD_VORONOI("Road-Anchored Voronoi", 1),
        TERRAIN_GROWTH("Terrain-Cost Parcel Growth (Dijkstra)", 1),
        GRAMMAR("Shape-Grammar / Rule-Based Subdivision", 1),
        STRAIGHT_SKELETON("Straight-Skeleton (raster wavefront)", 2),
        MEDIAL_AXIS("Medial-Axis / Skeleton Split (raster)", 2),
        OPTIMISED("Constraint-Optimised Parceling", 2),
        HISTORICAL("Historical / Incremental Subdivision", 2),
        MERGE("Parcel Merge", 0), SPLIT("Parcel Split", 0), REPAIR("Parcel Validation & Repair", 0);
        public final String label;
        public final int priority;
        Algorithm(String label, int priority) { this.label=label; this.priority=priority; }
        public List<Parcel> generate(Request r) {
            List<Set<Cell>> raw = switch(this) {
                case ROAD_FRONTAGE -> recursive(r.cells(), r, 0, 0);
                case FLOOD_FILL -> growth(r, seeds(r, false), false);
                case GRID -> grid(r);
                case BSP -> recursive(r.cells(), r, 1, 0);
                case WEIGHTED_VORONOI -> voronoi(r, seeds(r, false), true);
                case ROAD_VORONOI -> voronoi(r, seeds(r, true), false);
                case TERRAIN_GROWTH -> growth(r, seeds(r, true), true);
                case GRAMMAR -> recursive(r.cells(), r, 2, 0);
                case STRAIGHT_SKELETON -> skeleton(r, false);
                case MEDIAL_AXIS -> skeleton(r, true);
                case OPTIMISED -> optimise(r);
                case HISTORICAL -> historical(r);
                case MERGE -> merge(r, baseline(r));
                case SPLIT -> split(r, baseline(r));
                case REPAIR -> baseline(r);
            };
            return repair(r, raw);
        }
    }
    private static final Comparator<Cell> ORDER=Comparator.comparingInt(Cell::x).thenComparingInt(Cell::z);
    public static List<Cell> neighbours(Cell c) {
        return List.of(new Cell(c.x()-1,c.z()),new Cell(c.x()+1,c.z()),new Cell(c.x(),c.z()-1),new Cell(c.x(),c.z()+1));
    }
    private static List<Set<Cell>> baseline(Request r) {
        return r.previous().isEmpty()?grid(r):r.previous().stream().map(p->new LinkedHashSet<>(p.cells())).map(s->(Set<Cell>)s).toList();
    }
    private static List<Set<Cell>> grid(Request r) {
        int width=Math.max(2,(int)Math.sqrt(r.targetArea()));
        int depth=Math.max(2,r.targetArea()/width);
        var groups=new TreeMap<String,Set<Cell>>();
        int x0=r.cells().stream().mapToInt(Cell::x).min().orElse(0),z0=r.cells().stream().mapToInt(Cell::z).min().orElse(0);
        r.cells().stream().sorted(ORDER).forEach(c->groups.computeIfAbsent((c.x()-x0)/width+":"+(c.z()-z0)/depth,k->new LinkedHashSet<>()).add(c));
        return new ArrayList<>(groups.values());
    }
    private static List<Set<Cell>> recursive(Set<Cell> cells, Request r, int mode, int level) {
        if(cells.size()<=r.targetArea()*1.5 || level>=24) return List.of(new LinkedHashSet<>(cells));
        int minX=cells.stream().mapToInt(Cell::x).min().orElse(0),maxX=cells.stream().mapToInt(Cell::x).max().orElse(0);
        int minZ=cells.stream().mapToInt(Cell::z).min().orElse(0),maxZ=cells.stream().mapToInt(Cell::z).max().orElse(0);
        boolean axis=maxX-minX>=maxZ-minZ;
        if(mode==0) {
            // Cut along the frontage's long axis so both children retain a street edge.
            var front=cells.stream().filter(r.frontage()::contains).toList();
            if(front.size()>1) {
                int fx=front.stream().mapToInt(Cell::x).max().orElse(0)-front.stream().mapToInt(Cell::x).min().orElse(0);
                int fz=front.stream().mapToInt(Cell::z).max().orElse(0)-front.stream().mapToInt(Cell::z).min().orElse(0);
                axis=fx>=fz;
            }
        }
        // Grammar: alternate frontage strips and deep lots at a 1:2 ratio.
        if(mode==2 && level%2==1) axis=!axis;
        final boolean xAxis=axis;
        var sorted=cells.stream().sorted(Comparator.<Cell>comparingInt(c->xAxis?c.x():c.z()).thenComparing(ORDER)).toList();
        int index=mode==2?sorted.size()/3:sorted.size()/2;
        int cut=xAxis?sorted.get(index).x():sorted.get(index).z();
        var a=new LinkedHashSet<Cell>();var b=new LinkedHashSet<Cell>();
        for(var c:sorted) ((xAxis?c.x():c.z())<cut?a:b).add(c);
        if(a.isEmpty()||b.isEmpty()) return List.of(new LinkedHashSet<>(cells));
        var result=new ArrayList<Set<Cell>>();result.addAll(recursive(a,r,mode,level+1));result.addAll(recursive(b,r,mode,level+1));return result;
    }
    private static double distance(Cell a,Cell b) { double x=a.x()-b.x(),z=a.z()-b.z();return x*x+z*z; }
    private static List<Cell> seeds(Request r, boolean road) {
        var pool=r.cells().stream().filter(c->!road||r.frontage().contains(c)).sorted(ORDER).toList();
        if(pool.isEmpty()) pool=r.cells().stream().sorted(ORDER).toList();
        if(pool.isEmpty()) return List.of();
        int count=Math.max(1,(int)Math.ceil((double)r.cells().size()/r.targetArea()));
        var result=new ArrayList<Cell>();result.add(pool.get(Math.floorMod(r.seed(),pool.size())));
        while(result.size()<Math.min(count,pool.size())) {
            Cell best=null;double score=-1;
            for(var c:pool) { double d=result.stream().mapToDouble(s->distance(c,s)).min().orElse(0);if(d>score){score=d;best=c;} }
            if(score==0) break;result.add(best);
        }
        return result;
    }
    private static List<Set<Cell>> emptyGroups(int n) { var groups=new ArrayList<Set<Cell>>();for(int i=0;i<n;i++)groups.add(new LinkedHashSet<>());return groups; }
    private static List<Set<Cell>> voronoi(Request r,List<Cell> seeds,boolean weighted) {
        var groups=emptyGroups(seeds.size());if(seeds.isEmpty())return groups;
        var random=new Random(r.seed());double[] weights=new double[seeds.size()];for(int i=0;i<weights.length;i++)weights[i]=weighted?.65+random.nextDouble()*.7:1;
        for(var c:r.cells().stream().sorted(ORDER).toList()) { int best=0;double score=Double.POSITIVE_INFINITY;for(int i=0;i<seeds.size();i++){double d=distance(c,seeds.get(i))/weights[i];if(d<score){score=d;best=i;}}groups.get(best).add(c); }return groups;
    }
    private record Visit(Cell cell,int owner,double cost) {}
    private static List<Set<Cell>> growth(Request r,List<Cell> seeds,boolean terrain) {
        var groups=emptyGroups(seeds.size());
        var queue=new PriorityQueue<Visit>(Comparator.comparingDouble(Visit::cost).thenComparingInt(Visit::owner).thenComparing(Visit::cell,ORDER));
        var costs=new HashMap<Cell,Double>();var owner=new HashMap<Cell,Integer>();
        for(int i=0;i<seeds.size();i++){var c=seeds.get(i);costs.put(c,0.0);owner.put(c,i);queue.add(new Visit(c,i,0));}
        while(!queue.isEmpty()) { var v=queue.remove();if(v.cost()!=costs.get(v.cell())||v.owner()!=owner.get(v.cell()))continue;
            for(var n:neighbours(v.cell()))if(r.cells().contains(n)) {
                double step=terrain?r.terrainCost().applyAsDouble(n):1;
                if(!Double.isFinite(step)||step<=0)throw new IllegalArgumentException("Terrain costs must be finite and positive");
                double next=v.cost()+step;
                if(next<costs.getOrDefault(n,Double.POSITIVE_INFINITY)){costs.put(n,next);owner.put(n,v.owner());queue.add(new Visit(n,v.owner(),next));}
            }
        }
        r.cells().stream().sorted(ORDER).filter(owner::containsKey).forEach(c->groups.get(owner.get(c)).add(c));return groups;
    }
    private static List<Set<Cell>> skeleton(Request r,boolean medial) {
        // Unit-speed erosion gives a cell straight-skeleton approximation. Ridges of the
        // boundary-distance field give the medial axis; neither claims vector precision.
        var distance=new HashMap<Cell,Integer>();var queue=new ArrayDeque<Cell>();
        for(var c:r.cells().stream().sorted(ORDER).toList())if(neighbours(c).stream().anyMatch(n->!r.cells().contains(n))){distance.put(c,0);queue.add(c);}
        while(!queue.isEmpty()){var c=queue.remove();for(var n:neighbours(c))if(r.cells().contains(n)&&!distance.containsKey(n)){distance.put(n,distance.get(c)+1);queue.add(n);}}
        if(medial) {
            var ridges=r.cells().stream().sorted(ORDER).filter(c->neighbours(c).stream().filter(r.cells()::contains).allMatch(n->distance.get(n)<=distance.get(c))).toList();
            var chosen=new ArrayList<Cell>();int spacing=Math.max(2,(int)Math.sqrt(r.targetArea()));
            for(var c:ridges)if(chosen.stream().allMatch(s->distance(c,s)>=spacing*spacing))chosen.add(c);
            if(chosen.isEmpty())chosen.addAll(seeds(r,false));return growth(r,chosen,false);
        }
        // Divide inward wavefronts by source edge direction, then subdivide each basin.
        var sources=new HashMap<Cell,Integer>();queue.clear();
        for(var c:r.cells().stream().sorted(ORDER).toList())if(distance.get(c)==0){int edge=0;var ns=neighbours(c);while(edge<3&&r.cells().contains(ns.get(edge)))edge++;sources.put(c,edge);queue.add(c);}
        while(!queue.isEmpty()){var c=queue.remove();for(var n:neighbours(c))if(r.cells().contains(n)&&!sources.containsKey(n)){sources.put(n,sources.get(c));queue.add(n);}}
        var basins=emptyGroups(4);r.cells().stream().sorted(ORDER).forEach(c->basins.get(sources.get(c)).add(c));
        var result=new ArrayList<Set<Cell>>();for(var basin:basins)if(!basin.isEmpty())result.addAll(recursive(basin,r,1,0));return result;
    }
    private static List<Set<Cell>> optimise(Request r) {
        // Bounded portfolio search minimises area error, perimeter and missing frontage.
        List<Set<Cell>> best=grid(r);double score=score(best,r);
        for(int i=0;i<8;i++){var trial=new Request(r.cells(),r.frontage(),r.targetArea(),r.seed()+i,r.terrainCost(),List.of());var candidate=growth(trial,seeds(trial,true),false);double s=score(candidate,r);if(s<score){score=s;best=candidate;}}return best;
    }
    private static double score(List<Set<Cell>> groups,Request r) {
        double result=0;for(var g:groups){result+=Math.abs(g.size()-r.targetArea());if(g.stream().noneMatch(r.frontage()::contains))result+=r.targetArea();for(var c:g)for(var n:neighbours(c))if(!g.contains(n))result+=.2;}return result;
    }
    private static List<Set<Cell>> historical(Request r) {
        var result=new ArrayList<Set<Cell>>();var remaining=new LinkedHashSet<>(r.cells());
        for(var p:r.previous()){var keep=new LinkedHashSet<>(p.cells());keep.retainAll(remaining);remaining.removeAll(keep);if(!keep.isEmpty())result.addAll(recursive(keep,r,0,0));}
        if(!remaining.isEmpty())result.addAll(recursive(remaining,r,0,0));return result;
    }
    private static List<Set<Cell>> split(Request r,List<Set<Cell>> previous) {
        var result=new ArrayList<Set<Cell>>();var small=new Request(r.cells(),r.frontage(),Math.max(4,r.targetArea()/2),r.seed(),r.terrainCost(),List.of());
        for(var p:previous)result.addAll(recursive(p,small,1,0));return result;
    }
    private static List<Set<Cell>> merge(Request r,List<Set<Cell>> previous) {
        var result=new ArrayList<Set<Cell>>();for(var p:previous)result.add(new LinkedHashSet<>(p));
        boolean changed=true;
        while(changed){changed=false;outer:for(int i=0;i<result.size();i++)for(int j=i+1;j<result.size();j++){
            var a=result.get(i);var b=result.get(j);if(a.size()+b.size()<=r.targetArea()*2&&a.stream().anyMatch(c->neighbours(c).stream().anyMatch(b::contains))){a.addAll(b);result.remove(j);changed=true;break outer;}
        }}return result;
    }
    /** Clip, deduplicate, split disconnected components and fill every missing cell. */
    public static List<Parcel> repair(Request r,List<? extends Set<Cell>> raw) {
        var remaining=new LinkedHashSet<>(r.cells());var groups=new ArrayList<Set<Cell>>();
        for(var p:raw){var clipped=new LinkedHashSet<>(p);clipped.retainAll(remaining);remaining.removeAll(clipped);components(clipped,groups);}
        components(remaining,groups);
        var result=new ArrayList<Parcel>();for(var g:groups)if(!g.isEmpty())result.add(new Parcel(result.size()+1,g));return List.copyOf(result);
    }
    private static void components(Set<Cell> source,List<Set<Cell>> result) {
        var left=new TreeSet<Cell>(ORDER);left.addAll(source);
        while(!left.isEmpty()){var group=new LinkedHashSet<Cell>();var queue=new ArrayDeque<Cell>();var first=left.first();left.remove(first);queue.add(first);
            while(!queue.isEmpty()){var c=queue.remove();group.add(c);for(var n:neighbours(c))if(left.remove(n))queue.add(n);}result.add(group);}
    }
}
