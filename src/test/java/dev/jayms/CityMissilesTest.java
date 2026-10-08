package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.physics.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CityMissilesTest {
    World fixture() {
        World world=new World();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<=3;y++)world.addChunk(new ChunkPos(x,y,z),new Chunk());
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)for(int y=14;y<=20;y++)world.setBlock(x,y,z,Blocks.STONE);
        return world;
    }

    @Test void fallingImpactIsBoundedAndEditsArePersistentTransactions() {
        World world=fixture(); CityMissiles missiles=new CityMissiles();
        Map<String,Protocol.Edit> history=new WorldVoxels.History();
        List<Vector3f> impacts=new ArrayList<>();
        assertFalse(missiles.launch(new Vector3f(Float.NaN,0,0)));
        assertTrue(missiles.launch(new Vector3f(.5f,21,.5f)));
        assertFalse(missiles.launch(new Vector3f(.5f,21,.5f)));
        for(int i=0;i<160&&missiles.active();i++)missiles.update(world,.05f,impacts::add,e->{world.apply(e);WorldVoxels.remember(history,e);});
        assertEquals(1,missiles.impacts); assertEquals(1,impacts.size()); assertFalse(missiles.active());
        assertTrue(missiles.removed>0); assertEquals(missiles.removed,history.size());
        assertTrue(missiles.debris().size()>0&&missiles.debris().size()<=CityMissiles.MAX_DEBRIS);
        assertTrue(missiles.debris().stream().anyMatch(b->b.velocity.y>0));
        assertTrue(missiles.debris().stream().anyMatch(b->b.velocity.x*b.position.x+b.velocity.z*b.position.z>0));
        assertEquals(CityMissiles.MAX_SMOKE,missiles.smoke().size());
        var puff=missiles.smoke().get(50);
        float smokeY=puff.position().y, size=missiles.smokeSize(puff);
        missiles.update(world,.2f,impacts::add,e->fail("Second impact"));
        assertTrue(puff.position().y>smokeY);
        assertTrue(missiles.smokeSize(puff)>size);
        for(var edit:history.values()) { assertEquals(Blocks.AIR,edit.type()); assertTrue(edit.valid()); assertEquals(Blocks.AIR,world.sample(edit.x(),edit.y(),edit.z())); assertTrue(new Vector3f(edit.x()+.5f,edit.y()+.5f,edit.z()+.5f).distance(impacts.get(0))<=CityMissiles.RADIUS); }
        assertEquals(Blocks.STONE,world.sample(8,20,8));
        for(int i=0;i<170;i++)missiles.update(world,.05f,impacts::add,e->fail("Second impact"));
        assertTrue(missiles.smoke().isEmpty()); assertTrue(missiles.debris().isEmpty()); assertTrue(missiles.launch(new Vector3f(.5f,21,.5f)));
    }
}
