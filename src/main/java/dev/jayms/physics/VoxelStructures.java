package dev.jayms.physics;

import java.util.*;
import java.util.function.Predicate;
import org.joml.Vector3f;

/** Six-neighbour connectivity. Unknown boundaries are anchors: streaming never destroys buildings. */
public final class VoxelStructures {
    public record Cell(int x,int y,int z) {
        public Vector3f center() { return new Vector3f(x+.5f,y+.5f,z+.5f); }
        Cell offset(int[] d) { return new Cell(x+d[0],y+d[1],z+d[2]); }
    }
    private static final int[][] DIRECTIONS={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    public record Analysis(List<Set<Cell>> detached, boolean truncated, int visited) {}
    /** Only publish removals after an entire component was classified within the work limit. */
    public static Analysis analyze(Collection<Cell> seeds, Predicate<Cell> solid,
            Predicate<Cell> anchor, int limit) {
        if(limit<1) throw new IllegalArgumentException("Positive traversal limit required");
        Set<Cell> seen=new HashSet<>(); List<Set<Cell>> detached=new ArrayList<>();
        for(Cell seed:seeds) {
            if(seen.contains(seed)||!solid.test(seed)) continue;
            if(seen.size()>=limit)return new Analysis(List.of(),true,seen.size());
            Set<Cell> component=new LinkedHashSet<>(); ArrayDeque<Cell> queue=new ArrayDeque<>();
            queue.add(seed); seen.add(seed); boolean supported=false;
            while(!queue.isEmpty()) {
                Cell cell=queue.remove(); component.add(cell); supported|=anchor.test(cell);
                for(int[] d:DIRECTIONS) {
                    Cell next=cell.offset(d);
                    if(!seen.contains(next)&&solid.test(next)) {
                        if(seen.size()>=limit) return new Analysis(List.of(),true,seen.size());
                        seen.add(next);queue.add(next);
                    }
                }
            }
            if(!supported) detached.add(Collections.unmodifiableSet(component));
        }
        return new Analysis(List.copyOf(detached),false,seen.size());
    }
    public static List<Cell> neighbors(Cell c) { return Arrays.stream(DIRECTIONS).map(c::offset).toList(); }
    /** Load follows shortest connected paths to anchors, preferring downward support; fracture removes the most overstressed bond cell. */
    public static Map<Cell,Float> stress(Set<Cell> cells,Predicate<Cell> anchor,float weight) {
        if(!Float.isFinite(weight)||weight<0)throw new IllegalArgumentException("Nonnegative voxel weight required");
        Map<Cell,Integer> distance=new HashMap<>();ArrayDeque<Cell> queue=new ArrayDeque<>();
        for(Cell c:cells)if(anchor.test(c)){distance.put(c,0);queue.add(c);}
        while(!queue.isEmpty()) {
            Cell c=queue.remove();for(Cell n:neighbors(c))if(cells.contains(n)&&!distance.containsKey(n)){distance.put(n,distance.get(c)+1);queue.add(n);}
        }
        Map<Cell,Float> loads=new HashMap<>();var order=new ArrayList<>(cells);
        order.sort(Comparator.<Cell>comparingInt(c->distance.getOrDefault(c,Integer.MAX_VALUE)).reversed());
        for(Cell c:order) {
            float load=loads.getOrDefault(c,0f)+weight;loads.put(c,load);
            if(anchor.test(c)||!distance.containsKey(c))continue;
            var supports=neighbors(c).stream().filter(n->cells.contains(n)&&distance.getOrDefault(n,Integer.MAX_VALUE)<distance.get(c)).toList();
            var below=supports.stream().filter(n->n.y<c.y).toList();if(!below.isEmpty())supports=below;
            for(Cell n:supports)loads.merge(n,load/supports.size(),Float::sum);
        }
        return Map.copyOf(loads);
    }
    public static Set<Cell> fracture(Set<Cell> cells,Predicate<Cell> anchor,float weight,float strength) {
        Set<Cell> broken=new LinkedHashSet<>();
        stress(cells,anchor,weight).forEach((c,s)->{if(!anchor.test(c)&&s>strength) broken.add(c);});
        return broken;
    }
    private VoxelStructures() {}
}
