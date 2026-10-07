package dev.jayms.physics;

import dev.jayms.*;
import dev.jayms.net.Blocks;
import java.util.*;
import org.joml.Vector3f;

/** Resettable isolated playground. No account, network, terrain save or city state is touched. */
public final class PhysicsScene implements AutoCloseable {
    public final World voxels=new World();public final PhysicsWorld physics=new PhysicsWorld();
    public final Destruction destruction=new Destruction(voxels,physics);
    public final FluidGrid fluid=new FluidGrid(12,6,8);public final ShallowWater shallow=new ShallowWater(12,8);
    public final Deformable cloth=Deformable.cloth(8,6,.5f,new Vector3f(2,6,4),.0001f);
    public final Deformable soft=Deformable.softBody(new Vector3f(8,5,4),1.4f,.0002f);
    public final ParticleFluid particles=new ParticleFluid();
    public final CharacterController character=new CharacterController(new Vector3f(3,1.01f,10));
    public final VehicleForces.Wheel[] wheels={new VehicleForces.Wheel(),new VehicleForces.Wheel(),new VehicleForces.Wheel(),new VehicleForces.Wheel()};
    public final RigidBody vehicle,platform,door,boat;
    public final float[] erosionHeight=new float[96],erosionWater=new float[96],sediment=new float[96];
    public int detached;public float time;public boolean paused;public int scene=1;public boolean xpbd=true;
    public PhysicsScene() {
        voxels.addChunk(new ChunkPos(0,0,0),new Chunk());
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)voxels.setBlock(x,0,z,Blocks.STONE);
        // The lab floor is fixed terrain; only buildings belong to the destruction transaction.
        physics.bodies.add(new RigidBody(new Vector3f(8,.5f,8),new Vector3f(8,.5f,8),0));
        for(int y=1;y<=4;y++)destruction.place(new VoxelStructures.Cell(7,y,7),Blocks.PLANKS,y==1);
        for(int x=5;x<=9;x++)for(int z=5;z<=9;z++)destruction.place(new VoxelStructures.Cell(x,5,z),Blocks.BRICKS,false);
        // Independent anchored column is a regression guard for collapse propagation.
        for(int y=1;y<=3;y++)destruction.place(new VoxelStructures.Cell(12,y,12),Blocks.STONE,y==1);
        platform=new RigidBody(new Vector3f(3,1.3f,12),new Vector3f(1.5f,.2f,1.2f),0);platform.kinematic=true;physics.bodies.add(platform);
        RigidBody hingeBase=new RigidBody(new Vector3f(11,3,3),new Vector3f(.1f),0);hingeBase.layer=2;hingeBase.mask=0;physics.bodies.add(hingeBase);
        door=new RigidBody(new Vector3f(12,3,3),new Vector3f(1,1.5f,.15f),5);door.layer=2;door.mask=0;physics.bodies.add(door);
        physics.joints.add(new Constraints.Hinge(hingeBase,door,new Vector3f(),new Vector3f(-1,0,0),new Vector3f(0,1,0)));
        door.angularVelocity.y=.6f;
        RigidBody last=hingeBase;
        for(int i=0;i<5;i++) {RigidBody b=new RigidBody(new Vector3f(10-i,5,3),new Vector3f(.15f),1);b.layer=2;physics.bodies.add(b);physics.joints.add(new Constraints.Distance(last,b,1.2f,.0001f,true));last=b;}
        RigidBody spring=new RigidBody(new Vector3f(11,5,5),new Vector3f(.3f),1);spring.layer=2;physics.bodies.add(spring);physics.joints.add(new Constraints.Spring(hingeBase,spring,2,30,5));
        RigidBody trigger=new RigidBody(new Vector3f(3,2,10),new Vector3f(1,1,1),0);trigger.trigger=true;trigger.layer=4;physics.bodies.add(trigger);
        vehicle=new RigidBody(new Vector3f(7,2.1f,11),new Vector3f(1,.4f,1.8f),1200);vehicle.friction=.8f;vehicle.layer=8;physics.bodies.add(vehicle);
        boat=new RigidBody(new Vector3f(2.5f,3,4.5f),new Vector3f(.8f,.3f,.6f),500);boat.layer=16;physics.bodies.add(boat);
        for(int y=0;y<6;y++)for(int z=0;z<8;z++)for(int x=0;x<12;x++){int i=fluid.index(x,y,z);fluid.water[i]=x<4&&y<4?.95f:0;fluid.fuel[i]=y==0?1:0;}
        for(int x=7;x<=10;x++)for(int z=3;z<=5;z++){fluid.heat[fluid.index(x,0,z)]=4;fluid.fuel[fluid.index(x,0,z)]=3;}
        for(int z=0;z<8;z++)for(int x=0;x<12;x++){int i=z*12+x;shallow.depth[i]=x<4?1:.1f;erosionHeight[i]=x<6?2:0;erosionWater[i]=x<4?.5f:0;}
        for(int x=0;x<4;x++)for(int y=0;y<3;y++)for(int z=0;z<3;z++)particles.add(new Vector3f(9+x*.32f,3+y*.32f,7+z*.32f));
    }
    public void destroySupport(){detached+=destruction.destroy(new VoxelStructures.Cell(7,1,7));}
    public void explode(){detached+=destruction.explosion(new Vector3f(7,3,7),1.8f,8);}
    public void fracture(){detached+=destruction.stressFracture(1,4);}
    public void step(float dt,float drive,Vector3f walking,boolean jump) {
        if(paused)return;dt=Math.min(dt,1f/30);time+=dt;
        platform.velocity.set(0,(float)Math.cos(time)*.8f,0);
        vehicle.force.add(VehicleForces.dragLift(vehicle.velocity,physics.wind,1.2f,4,.8f,.1f,new Vector3f(0,1,0)));
        for(int i=0;i<4;i++) {
            Vector3f point=new Vector3f(vehicle.position).add(i%2==0?-.8f:.8f,0,i<2?-1.3f:1.3f);
            var hit=physics.raycast(point,new Vector3f(0,-1,0),1.5f,1);
            Vector3f force=wheels[i].force(hit==null?100:hit.distance(),vehicle.velocity.y,vehicle.velocity.x,drive*1600,drive==0?60:0,.8f,1200*24/4,dt);
            vehicle.force.add(force);
        }
        if(scene==3) {
            int bx=Math.max(0,Math.min(11,(int)Math.floor(boat.position.x-1))),bz=Math.max(0,Math.min(7,(int)Math.floor(boat.position.z-2)));
            float waterHeight=1;for(int y=0;y<6;y++)waterHeight+=fluid.water[fluid.index(bx,y,bz)];
            boat.force.add(VehicleForces.buoyancy(boat.position.y-.3f,boat.position.y+.3f,waterHeight,1.152f,1000,24));
            boat.force.add(new Vector3f(boat.velocity).mul(-400));
        }
        physics.step(dt);character.step(physics,dt,walking,jump);
        if(scene==3) {fluid.step(dt);shallow.step(dt);FluidGrid.erode(erosionHeight,erosionWater,sediment,12,dt,.6f);}
        if(scene==4) {cloth.step(dt,new Vector3f(physics.wind).add(0,-9.81f,0),8,1,xpbd);soft.step(dt,new Vector3f(0,-9.81f,0),8,1,xpbd);particles.step(dt,new Vector3f(0,-9.81f,0),1);}
    }
    @Override public void close() throws Exception {voxels.close();}
}
