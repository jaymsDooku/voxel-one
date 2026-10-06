package dev.jayms;

import dev.jayms.player.*;
import dev.jayms.net.Blocks;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class JeepTest {
    @TempDir Path temp;
    World flat() {
        World world=new World();
        for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) {
            Chunk c=new Chunk();
            for(int i=0;i<16;i++) for(int k=0;k<16;k++) c.setBlock(i,0,k,Blocks.STONE);
            world.addChunk(new ChunkPos(x,0,z),c);
        }
        return world;
    }
    Player driver(Jeep jeep) {
        Player p=new Player(jeep.position().add(0,0,2.8f),0,0,new Camera());
        assertTrue(jeep.enter(p)); return p;
    }
    void run(Jeep jeep,World world,float throttle,float steer,boolean boost,int frames) {
        for(int i=0;i<frames;i++) jeep.step(world,1f/60,throttle,steer,boost);
    }
    @Test void driveBoostReverseAndBrake() {
        World world=flat();
        Jeep normal=new Jeep(new Vector3f(0,1.01f,0),0),fast=new Jeep(new Vector3f(0,1.01f,0),0);
        driver(normal); driver(fast);
        run(normal,world,1,0,false,60); run(fast,world,1,0,true,60);
        assertTrue(fast.position().x>normal.position().x*1.5f);
        assertTrue(fast.speed()>normal.speed());
        run(fast,world,0,0,false,180); assertEquals(0,fast.speed(),.01);
        float x=fast.position().x; run(fast,world,-1,0,false,90);
        assertTrue(fast.position().x<x); assertEquals(1,fast.position().y,.015);
    }
    @Test void steerMouseAndNoStrafe() {
        World world=flat(); Jeep jeep=new Jeep(new Vector3f(0,1.01f,0),0); driver(jeep);
        run(jeep,world,0,1,false,60); assertEquals(0,jeep.yaw(),.01);
        run(jeep,world,1,1,false,60); assertTrue(jeep.yaw()>20);
        float before=jeep.yaw(); jeep.mouse(world,8); assertEquals(before+8,jeep.yaw(),.01);
        assertTrue(jeep.position().z>0);
    }
    @Test void wallStopsFullBodyAtBoostSpeedAndGlassIsSolid() {
        World world=flat();
        for(int y=1;y<=4;y++) for(int z=-8;z<=8;z++)
            world.getLoadedChunks().get(new ChunkPos(0,0,Math.floorDiv(z,16))).setBlock(12,y,Math.floorMod(z,16),Blocks.GLASS);
        Jeep jeep=new Jeep(new Vector3f(0,1.01f,0),0); driver(jeep);
        run(jeep,world,1,0,true,180);
        assertTrue(jeep.position().x<=10.101f); assertEquals(0,jeep.speed(),.001);
        assertFalse(jeep.collides(world,jeep.position()));
    }
    @Test void entryExitRejectSpeedAndBlockedSidesThenWalkingWorks() {
        World world=flat(); Jeep jeep=new Jeep(new Vector3f(0,1.01f,0),0); Player p=driver(jeep);
        run(jeep,world,1,0,false,40); assertFalse(jeep.exit(world,p));
        run(jeep,world,0,0,false,120);
        assertTrue(jeep.exit(world,p)); assertFalse(jeep.driving());
        Vector3f before=p.position(); for(int i=0;i<30;i++) p.step(world,1f/60,1,0,false,false);
        assertTrue(before.distance(p.position())>.5f);
        Player far=new Player(new Vector3f(25,1,25),0,0,new Camera()); assertFalse(jeep.enter(far));
        p.driveSeat(jeep.position(),0); assertTrue(jeep.enter(p));
        int px=(int)Math.floor(jeep.position().x);
        for(int x=px-2;x<=px+2;x++) for(int z:new int[]{-2,1}) for(int y=1;y<=3;y++)
            world.getLoadedChunks().get(new ChunkPos(Math.floorDiv(x,16),0,Math.floorDiv(z,16)))
                .setBlock(Math.floorMod(x,16),y,Math.floorMod(z,16),Blocks.STONE);
        assertFalse(jeep.exit(world,p)); assertTrue(jeep.driving());
    }
    @Test void respawnReleasesSeatAndStopsTheVehicle() {
        World world=flat(); Jeep jeep=new Jeep(new Vector3f(0,1.01f,0),0); driver(jeep);
        run(jeep,world,1,0,true,40); assertTrue(jeep.speed()>1);
        jeep.releaseDriver(); assertFalse(jeep.driving()); assertEquals(0,jeep.speed());
        Vector3f parked=jeep.position(); run(jeep,world,1,1,true,60);
        assertEquals(parked.x,jeep.position().x,.001); assertEquals(parked.z,jeep.position().z,.001);
    }
    @Test void unloadedBoundaryAndSaveReload() throws Exception {
        World world=flat(); Jeep jeep=new Jeep(new Vector3f(0,1.01f,0),0); driver(jeep);
        run(jeep,world,1,0,true,600); assertTrue(jeep.position().x<46.11f);
        Path file=temp.resolve("vehicle.jeep"); jeep.save(file);
        Jeep restored=Jeep.load(file,world,new Vector3f(0,1,0));
        assertEquals(jeep.position(),restored.position()); assertEquals(jeep.yaw(),restored.yaw());
        assertFalse(restored.driving()); assertEquals(0,restored.speed());
    }
}
