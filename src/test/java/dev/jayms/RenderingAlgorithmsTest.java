package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.render.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderingAlgorithmsTest {
    @Test void solidChunkMergesToSixQuadsWithOutwardWinding(){
        Chunk chunk=new Chunk();for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)chunk.setBlock(x,y,z,Blocks.STONE);
        MeshData mesh=MeshDataGenerator.generate(chunk);assertEquals(36,mesh.indices().length);assertEquals(24*9,mesh.vertices().length);
        float[] v=mesh.vertices();int[] indices=mesh.indices();
        for(int t=0;t<indices.length;t+=3){int a=indices[t]*9,b=indices[t+1]*9,c=indices[t+2]*9;
            Vector3f ab=new Vector3f(v[b]-v[a],v[b+1]-v[a+1],v[b+2]-v[a+2]),ac=new Vector3f(v[c]-v[a],v[c+1]-v[a+1],v[c+2]-v[a+2]);
            assertTrue(ab.cross(ac).dot(v[a+3],v[a+4],v[a+5])>0);
        }
    }
    @Test void materialsAndLedColoursRemainSeparate(){
        Chunk chunk=new Chunk();chunk.setBlock(0,0,0,Blocks.STONE);chunk.setBlock(1,0,0,Blocks.PLANKS);
        assertEquals(60,MeshDataGenerator.generate(chunk).indices().length);
        chunk=new Chunk();chunk.apply(new Protocol.Edit(0,0,0,Blocks.LED).withColor(0xff0000));chunk.apply(new Protocol.Edit(1,0,0,Blocks.LED).withColor(0x0000ff));
        MeshData mesh=MeshDataGenerator.generate(chunk);assertEquals(60,mesh.indices().length);
        int red=0,blue=0;for(int i=0;i<mesh.vertices().length;i+=9){if(mesh.vertices()[i+6]>.9)red++;if(mesh.vertices()[i+8]>.9)blue++;assertEquals(4,mesh.surface()[i/3]);}
        assertEquals(20,red);assertEquals(20,blue);
    }
    @Test void crossChunkSeamsHideFacesWithoutMutatingWorld(){
        World world=new World();Chunk a=new Chunk(),b=new Chunk();a.setBlock(15,1,1,Blocks.STONE);b.setBlock(0,1,1,Blocks.STONE);
        world.addChunk(new ChunkPos(0,0,0),a);world.addChunk(new ChunkPos(1,0,0),b);
        assertEquals(30,MeshDataGenerator.generate(a).indices().length);assertEquals(30,MeshDataGenerator.generate(b).indices().length);
        b.setBlock(0,1,1,0);assertEquals(36,MeshDataGenerator.generate(a).indices().length);
    }
    @Test void waterUsesDedicatedSurfaceMetadata(){
        Chunk c=new Chunk();c.setBlock(1,1,1,Blocks.WATER);MeshData mesh=MeshDataGenerator.generate(c);
        for(int i=2;i<mesh.surface().length;i+=3)assertEquals(-2,mesh.surface()[i]);
    }
    @Test void screenErrorRespondsToViewportDistanceAndProjection(){
        assertEquals(100,ScreenError.pixels(1,10,2,1000,false));
        assertEquals(50,ScreenError.pixels(1,20,2,1000,false));
        assertEquals(1000,ScreenError.pixels(1,20,2,1000,true));
        int chosen=ScreenError.tileSize(1000,1,400,false,12);
        assertTrue(ScreenError.pixels(chosen/16f,1000,1,400,false)<=12);
        assertTrue(chosen==1024||ScreenError.pixels(chosen*2/16f,1000,1,400,false)>12);
        assertTrue(ScreenError.tileSize(1000,1,400,false,12)>ScreenError.tileSize(100,1,400,false,12));
        assertThrows(IllegalArgumentException.class,()->ScreenError.pixels(1,Float.NaN,1,100,false));
    }
    @Test void distanceFieldAndRadianceRespectOpaqueGeometryAndWater(){
        int n=9*9*9;int[] material=new int[n];byte[] light=new byte[n*4];
        int center=4+9*(4+9*4);material[center]=WorldVoxels.encode(new Protocol.Edit(4,4,4,Blocks.LED).withColor(0xff0000));
        material[center+1]=WorldVoxels.encode(Blocks.WATER);material[center-1]=-1;
        TransportField f=new TransportField(9,9,9,material,light);
        assertTrue(f.distance[center]<0);assertTrue(f.distance[center+1]>0);assertTrue(f.radiance[center*4]>3);assertEquals(1,f.radiance[center*4+3]);
        assertTrue(f.distance[center+2]>=f.distance[center+1]);for(float[] probes:f.probes)for(float value:probes)assertTrue(Float.isFinite(value));
    }
    @Test void atmosphereIsFiniteAndSunwardScatteringIsBrighter(){
        Vector3f sun=new Vector3f(.4f,.8f,.2f).normalize();
        Vector3f forward=Atmosphere.radiance(sun,sun,1,1),away=Atmosphere.radiance(new Vector3f(sun).negate(),sun,1,1);
        assertTrue(forward.isFinite());assertTrue(forward.x>away.x);assertTrue(Atmosphere.radiance(new Vector3f(1,0,0),sun,0,.05f).isFinite());
    }
    @Test void lutAndDecalRejectMalformedDataAndDefendMutableInputs(){
        ColourLut lut=ColourLut.identity(2);assertEquals(24,lut.rgb().length);float[] copy=lut.rgb();copy[0]=1;assertEquals(0,lut.rgb()[0]);
        assertThrows(IllegalArgumentException.class,()->new ColourLut(2,new float[1]));
        Vector3f p=new Vector3f(1,2,3);Decal decal=new Decal(p,new Vector3f(0,1,0),1,.1f,new Vector3f(1,0,0),1);p.set(0);assertEquals(1,decal.center().x);
        assertThrows(IllegalArgumentException.class,()->new Decal(p,new Vector3f(),1,1,new Vector3f(),1));
    }
}
