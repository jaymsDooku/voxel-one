package dev.jayms.physics;

import dev.jayms.World;
import dev.jayms.net.Blocks;
import java.util.*;
import org.joml.Vector3f;

/** Explicit world-edit transaction for voxel scenes. Caller owns networking and persistence. */
public final class Destruction {
    private final World world;private final PhysicsWorld physics;
    private final Set<VoxelStructures.Cell> structure=new LinkedHashSet<>(),anchors=new HashSet<>();
    private final Map<VoxelStructures.Cell,RigidBody> colliders=new HashMap<>();
    public Destruction(World world,PhysicsWorld physics){this.world=world;this.physics=physics;}
    public Set<VoxelStructures.Cell> cells(){return Set.copyOf(structure);}
    public void place(VoxelStructures.Cell c,int material,boolean anchor) {
        if(material==Blocks.AIR||material==Blocks.WATER)throw new IllegalArgumentException("Structural material required");
        remove(c);world.setBlock(c.x(),c.y(),c.z(),material);structure.add(c);if(anchor)anchors.add(c);
        RigidBody body=new RigidBody(c.center(),new Vector3f(.5f),0);body.material=material;colliders.put(c,body);physics.bodies.add(body);
    }
    private void remove(VoxelStructures.Cell c){structure.remove(c);anchors.remove(c);RigidBody b=colliders.remove(c);if(b!=null){physics.bodies.remove(b);for(var other:physics.bodies)if(other.inverseMass>0)other.wake();}world.setBlock(c.x(),c.y(),c.z(),Blocks.AIR);}
    public int destroy(VoxelStructures.Cell c) {
        if(!structure.contains(c))return 0;
        remove(c);return detach();
    }
    public int detach() {
        var analysis=VoxelStructures.analyze(structure,structure::contains,anchors::contains,8192);
        if(analysis.truncated())return 0;
        int count=0;
        for(var component:analysis.detached())for(var c:component) {
            int material=world.sample(c.x(),c.y(),c.z());remove(c);
            RigidBody debris=new RigidBody(c.center(),new Vector3f(.49f),1);debris.material=material;debris.restitution=.15f;physics.bodies.add(debris);count++;
        }
        return count;
    }
    public int stressFracture(float weight,float strength) {
        var cracks=VoxelStructures.fracture(structure,anchors::contains,weight,strength);
        for(var c:cracks)remove(c);return cracks.size()+detach();
    }
    public int explosion(Vector3f origin,float radius,float impulse) {
        for(var c:new ArrayList<>(structure))if(c.center().distance(origin)<radius)remove(c);
        int count=detach();physics.explode(origin,radius*3,impulse);return count;
    }
}
