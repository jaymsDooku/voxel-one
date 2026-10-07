package dev.jayms.physics;

import dev.jayms.*;
import dev.jayms.net.Blocks;
import dev.jayms.net.Protocol;
import dev.jayms.player.Player;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PhysicsTest {
    RigidBody body(float x,float y,float z,float mass){return new RigidBody(new Vector3f(x,y,z),new Vector3f(.5f),mass);}
    PhysicsWorld floor(){PhysicsWorld w=new PhysicsWorld();w.bodies.add(new RigidBody(new Vector3f(0,-.5f,0),new Vector3f(20,.5f,20),0));return w;}
    void advance(PhysicsWorld w,int frames){for(int i=0;i<frames;i++)w.step(1f/60);}
    @Test void sweptCollisionStopsFastAndThinObjects(){
        Aabb a=new Aabb(0,0,0,1,1,1),b=new Aabb(5,0,0,5.01f,1,1);
        var h=a.sweep(b,new Vector3f(100,0,0));assertNotNull(h);assertEquals(.04f,h.time(),1e-6);assertEquals(-1,h.normal().x);
        assertNull(a.sweep(b,new Vector3f(-100,0,0)));assertNull(a.sweep(new Aabb(5,1,0,6,2,1),new Vector3f(10,0,0)));
    }
    @Test void negativeGridCoordinatesAndLargeBounds(){SpatialGrid<RigidBody> grid=new SpatialGrid<>(2);RigidBody a=body(-2,0,0,1);grid.rebuild(List.of(a),RigidBody::bounds);assertTrue(grid.query(new Aabb(-3,0,0,-1,1,1)).contains(a));assertFalse(grid.query(new Aabb(5,0,0,6,1,1)).contains(a));}
    @Test void ccdAndRaycast(){PhysicsWorld w=floor();RigidBody a=body(0,3,0,1);a.velocity.y=-1000;w.bodies.add(a);w.step(1f/60);assertTrue(a.position.y>=.499f);var hit=w.raycast(new Vector3f(0,5,0),new Vector3f(0,-2,0),10,1);assertNotNull(hit);assertSame(a,hit.body());assertTrue(hit.distance()<5);}
    @Test void impulseMomentumAndRestitution(){PhysicsWorld w=new PhysicsWorld();w.gravity.zero();RigidBody a=body(-1,0,0,1),b=body(1,0,0,1);a.velocity.x=10;a.restitution=b.restitution=1;w.bodies.addAll(List.of(a,b));advance(w,20);assertEquals(10,a.velocity.x+b.velocity.x,.01);assertTrue(b.velocity.x>9);assertTrue(a.position.x<b.position.x);}
    @Test void frictionGravityStackAndSleep(){PhysicsWorld w=floor();RigidBody a=body(0,1,0,1),b=body(0,2.01f,0,1);a.velocity.x=2;w.bodies.addAll(List.of(a,b));advance(w,300);assertTrue(Math.abs(a.velocity.x)<.1,"friction "+a.velocity);assertTrue(a.position.y>=.49);assertTrue(b.position.y>=.49);assertTrue(a.sleeping,"sleep "+a.velocity+" quiet="+a.quietTime);a.impulse(new Vector3f(0,3,0));assertFalse(a.sleeping);w.step(.02f);assertTrue(a.velocity.y>0);}
    @Test void layersAndTriggerTransitions(){PhysicsWorld w=new PhysicsWorld();w.gravity.zero();RigidBody a=body(0,0,0,1),b=body(0,0,0,0);b.trigger=true;b.layer=2;a.mask=1;w.bodies.addAll(List.of(a,b));w.step(.01f);assertTrue(w.overlaps().isEmpty());a.mask=3;w.step(.01f);assertEquals(1,w.entered().size());assertEquals(0,a.position.length());a.position.x=4;w.step(.01f);assertEquals(1,w.exited().size());}
    @Test void distanceRopeSpringAndBreakage(){RigidBody a=body(0,0,0,0),b=body(3,0,0,1);Constraints.Distance d=new Constraints.Distance(a,b,1,0,false);d.solve(.01f);assertEquals(1,b.position.x,.001);b.position.x=.5f;var rope=new Constraints.Distance(a,b,1,0,true);rope.solve(.01f);assertEquals(.5f,b.position.x);b.position.x=3;rope.breakForce=1;rope.solve(.01f);assertTrue(rope.broken());var spring=new Constraints.Spring(a,b,1,20,2);spring.solve(.01f);assertTrue(b.velocity.x<0);}
    @Test void hingeKeepsAnchorAndAllowsDoorRotation(){RigidBody a=body(0,0,0,0),b=body(1,0,0,1);var hinge=new Constraints.Hinge(a,b,new Vector3f(),new Vector3f(-1,0,0),new Vector3f(0,1,0));b.rotation.rotateY(.8f);b.rotation.transform(new Vector3f(1,0,0),b.position);hinge.solve(.01f);assertEquals(0,b.anchor(new Vector3f(-1,0,0)).distance(a.position),.0001);assertTrue(Math.abs(b.rotation.y)>.2f);}
    @Test void componentsAreAnchoredOrDetachedAndBudgetSafe(){var a=new VoxelStructures.Cell(0,0,0);var b=new VoxelStructures.Cell(0,1,0);var c=new VoxelStructures.Cell(5,2,0);Set<VoxelStructures.Cell> cells=Set.of(a,b,c);var r=VoxelStructures.analyze(cells,cells::contains,a::equals,20);assertEquals(List.of(Set.of(c)),r.detached());var limited=VoxelStructures.analyze(List.of(a),cells::contains,x->false,1);assertTrue(limited.truncated());assertTrue(limited.detached().isEmpty());}
    @Test void supportCutCreatesDebrisAndPreservesOtherTower()throws Exception{try(var s=new PhysicsScene()){s.destroySupport();assertEquals(28,s.detached);assertEquals(0,s.voxels.sample(7,5,7));assertEquals(Blocks.STONE,s.voxels.sample(12,2,12));float y=s.physics.bodies.stream().filter(b->b.inverseMass==1&&b.material==Blocks.BRICKS).findFirst().orElseThrow().position.y;for(int i=0;i<60;i++)s.step(1f/120,0,new Vector3f(),false);assertTrue(s.physics.bodies.stream().filter(b->b.inverseMass==1&&b.material==Blocks.BRICKS).anyMatch(b->b.position.y<y));int count=s.detached;s.destroySupport();assertEquals(count,s.detached);}}
    @Test void voxelStressFracturesOverloadedCells(){Set<VoxelStructures.Cell> cells=new HashSet<>();for(int y=0;y<6;y++)cells.add(new VoxelStructures.Cell(0,y,0));var cracks=VoxelStructures.fracture(cells,c->c.y()==0,1,2);assertTrue(cracks.contains(new VoxelStructures.Cell(0,1,0)));assertFalse(cracks.contains(new VoxelStructures.Cell(0,0,0)));}
    @Test void wheelSuspensionTractionBuoyancyDragAndBlast(){var wheel=new VehicleForces.Wheel();var f=wheel.force(.5f,0,0,100,0,.8f,3000,.01f);assertTrue(f.y>0);assertTrue(wheel.angularSpeed>0);assertEquals(0,wheel.force(3,0,0,0,0,.8f,3000,.01f).length());assertEquals(4905,VehicleForces.buoyancy(0,2,1,1,1000,9.81f).y,.01);assertTrue(VehicleForces.dragLift(new Vector3f(10,0,0),new Vector3f(),1,1,1,.2f,new Vector3f(0,1,0)).x<0);assertTrue(VehicleForces.explosion(new Vector3f(1,0,0),new Vector3f(),5,10).x>0);assertEquals(0,VehicleForces.explosion(new Vector3f(10,0,0),new Vector3f(),5,10).length());}
    @Test void waterConservesMassAndWallsBlockFlow(){FluidGrid g=new FluidGrid(6,3,2);g.water[g.index(0,2,0)]=1;for(int y=0;y<3;y++)for(int z=0;z<2;z++)g.solid[g.index(3,y,z)]=true;for(int i=0;i<200;i++)g.step(.02f);double sum=0;for(float d:g.water){assertTrue(d>=0&&d<=1);sum+=d;}assertEquals(1,sum,1e-5);assertEquals(0,g.water[g.index(4,0,0)]);assertTrue(g.water[g.index(0,0,0)]>0);}
    @Test void fireConsumesFuelAndHeatSmokePressureSpread(){FluidGrid g=new FluidGrid(5,4,1);g.heat[g.index(1,0,0)]=4;g.fuel[g.index(1,0,0)]=2;for(int i=0;i<100;i++)g.step(.02f);assertTrue(g.fuel[g.index(1,0,0)]<2);assertTrue(g.heat[g.index(2,0,0)]>0);assertTrue(g.smoke[g.index(1,1,0)]>0);assertTrue(g.pressure[g.index(1,1,0)]>0);}
    @Test void shallowWaterConservationAndWaveTransport(){ShallowWater g=new ShallowWater(8,4);for(int i=0;i<g.depth.length;i++)g.depth[i]=i%8<2?1:.1f;double before=0;for(float d:g.depth)before+=d;for(int i=0;i<100;i++)g.step(.01f);double after=0;for(float d:g.depth){assertTrue(Float.isFinite(d)&&d>=0);after+=d;}assertEquals(before,after,1e-4);assertTrue(g.depth[7]>.1f);}
    @Test void erosionConservesSoilAndSediment(){float[] soil={3,0,0,0},water={1,0,0,0},sediment=new float[4];for(int i=0;i<100;i++)FluidGrid.erode(soil,water,sediment,4,.02f,.5f);float sum=0;for(int i=0;i<4;i++)sum+=soil[i]+sediment[i];assertEquals(3,sum,1e-4);assertTrue(soil[0]<3);assertTrue(soil[1]>0);}
    @Test void clothPinningXpbdAndSoftbodyRemainFinite(){for(boolean xpbd:List.of(false,true)){var cloth=Deformable.cloth(5,5,.3f,new Vector3f(0,4,0),.0001f);var pin=new Vector3f(cloth.particles.get(0).position);for(int i=0;i<200;i++)cloth.step(1f/120,new Vector3f(2,-9.81f,0),10,0,xpbd);assertEquals(pin,cloth.particles.get(0).position);for(var p:cloth.particles)assertTrue(p.position.isFinite());assertTrue(cloth.particles.get(24).position.x>1.2f);}var soft=Deformable.softBody(new Vector3f(0,3,0),1,.0001f);for(int i=0;i<200;i++)soft.step(1f/120,new Vector3f(0,-9.81f,0),10,0,true);assertTrue(soft.particles.stream().allMatch(p->p.position.y>=0&&p.position.isFinite()));}
    @Test void sphPressureSeparatesParticlesAndConservesMomentum(){ParticleFluid p=new ParticleFluid();p.restDensity=.1f;p.add(new Vector3f(0,2,0));p.add(new Vector3f(.3f,2,0));float before=p.positions.get(0).distance(p.positions.get(1));p.step(.02f,new Vector3f(),0);assertTrue(p.positions.get(0).distance(p.positions.get(1))>before);assertEquals(0,new Vector3f(p.velocities.get(0)).add(p.velocities.get(1)).length(),.0001);assertTrue(p.densities()[0]>0);}
    @Test void computeCpuPinnedAndGravity(){float[] p={0,4,0,1,2,0,0,0,0,5,0,0,0,0,0,0};ComputeParticles.cpu(p,.1f,-10);assertEquals(.2f,p[0],1e-6);assertEquals(3.9f,p[1],1e-6);assertEquals(5,p[9]);}
    @Test void characterRidesMovingPlatform(){PhysicsWorld w=new PhysicsWorld();RigidBody platform=new RigidBody(new Vector3f(0,0,0),new Vector3f(3,.5f,3),0);platform.kinematic=true;w.bodies.add(platform);CharacterController c=new CharacterController(new Vector3f(0,.501f,0));for(int i=0;i<20;i++){w.step(1f/60);c.step(w,1f/60,new Vector3f(),false);}assertTrue(c.grounded);platform.velocity.x=1;for(int i=0;i<60;i++){w.step(1f/60);c.step(w,1f/60,new Vector3f(),false);}assertEquals(1,c.position.x,.03);c.step(w,1f/60,new Vector3f(),true);assertFalse(c.grounded);assertTrue(c.velocity.y>0);}
    @Test void voxelSweepPartialAndUnloadedBoundary(){World w=new World();w.addChunk(new ChunkPos(0,0,0),new Chunk());w.apply(new Protocol.Edit(6,1,3,Blocks.STONE,4,0,0,0));var b=new VoxelQueries.Box(3,1,3,3.5f,2,3.5f);float move=VoxelMotion.allowed(w,b,12,0);assertEquals(2.5f,move,.001);assertEquals(0,VoxelMotion.allowed(w,new VoxelQueries.Box(15.5f,1,3,16,2,3.5f),5,0),.001);}
    @Test void characterStepsAndTriggers() {
        PhysicsWorld w=floor();RigidBody stair=new RigidBody(new Vector3f(2,.25f,0),new Vector3f(.5f,.25f,2),0);
        RigidBody trigger=new RigidBody(new Vector3f(0,1,0),new Vector3f(1,2,2),0);trigger.trigger=true;trigger.layer=4;
        w.bodies.addAll(List.of(stair,trigger));CharacterController c=new CharacterController(new Vector3f(0,.001f,0));
        c.step(w,1f/60,new Vector3f(),false);assertEquals(1,c.entered().size());
        float highest=0;for(int i=0;i<80;i++){c.step(w,1f/60,new Vector3f(3,0,0),false);highest=Math.max(highest,c.position.y);}
        assertTrue(highest>.49f);assertTrue(c.position.x>3);assertTrue(c.overlaps().isEmpty());
    }
    @Test void sampledGroundNormalAndMissingSupport() {
        World w=new World();Chunk chunk=new Chunk();for(int x=0;x<16;x++)for(int z=0;z<16;z++)chunk.setBlock(x,0,z,Blocks.STONE);w.addChunk(new ChunkPos(0,0,0),chunk);
        assertEquals(1,VoxelMotion.groundNormal(w,new Vector3f(8,1.01f,8),.3f,.1f).y,.00001);
        assertEquals(0,VoxelMotion.groundNormal(w,new Vector3f(8,3,8),.3f,.1f).length());
    }

    @Test void roofWeightReachesColumnAndCracksThenDetaches() throws Exception {
        try(var scene=new PhysicsScene()) {
            var cells=scene.destruction.cells();
            var loads=VoxelStructures.stress(cells,c->c.y()==1,1);
            assertEquals(28,loads.get(new VoxelStructures.Cell(7,2,7)),.001f);
            scene.fracture();assertTrue(scene.detached>0);assertEquals(Blocks.STONE,scene.voxels.sample(12,2,12));
        }
    }

    @Test void pathologicalQueriesFailBeforeAllocatingOrLooping() {
        SpatialGrid<RigidBody> grid=new SpatialGrid<>(1);
        assertThrows(IllegalArgumentException.class,()->grid.query(new Aabb(-1e9f,-1e9f,-1e9f,1e9f,1e9f,1e9f)));
        assertThrows(IllegalArgumentException.class,()->VoxelQueries.boxes(new World(),new VoxelQueries.Box(-1e9f,-1e9f,-1e9f,1e9f,1e9f,1e9f)));
        assertThrows(IllegalArgumentException.class,()->new PhysicsWorld().step(Float.NaN));
    }

}
