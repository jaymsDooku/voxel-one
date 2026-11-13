package dev.jayms;

import dev.jayms.net.model.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelFaceCountTest {
    private byte[] bytes(SparseVoxelOctree tree)throws Exception {
        var bytes=new ByteArrayOutputStream();tree.write(new DataOutputStream(bytes));return bytes.toByteArray();
    }
    @Test void exactGreedyCountsMatchRenderingAcrossResolutionsAndDoNotMutateTrees()throws Exception {
        var random=new Random(314);
        for(int size:new int[]{8,16,32})for(int mode=0;mode<4;mode++) {
            var tree=new SparseVoxelOctree(size);
            if(mode==1)tree.fill(0,0,0,size,size,size,0xff123456);
            if(mode==2)for(int y=0;y<size;y++)for(int z=0;z<size;z++)for(int x=0;x<size;x++)
                if(x==0||y==0||z==0||x==size-1||y==size-1||z==size-1)tree.set(x,y,z,0xff000000|random.nextInt(1<<24));
            if(mode==3)for(int i=0;i<size*2;i++)tree.set(random.nextInt(size),random.nextInt(size),random.nextInt(size),0xff000000|random.nextInt(1<<24));
            tree.freeze();byte[] before=bytes(tree);ModelMesher.Geometry mesh;
            try {mesh=ModelMesher.mesh(tree);}
            catch(IOException rejected) {assertEquals(rejected.getMessage(),assertThrows(IOException.class,()->ModelMesher.count(tree)).getMessage());assertArrayEquals(before,bytes(tree));continue;}
            assertEquals(mesh.quads(),ModelMesher.count(tree));assertEquals(mesh.quads()*36,mesh.vertices().length);assertEquals(mesh.quads()*6,mesh.indices().length);
            assertArrayEquals(before,bytes(tree));
        }
    }
    @Test void renderBuffersRemainBitExactAfterRemovingFaceScratchArrays()throws Exception {
        // Recorded from the parent mesher: all axes, both windings, colours and merged faces.
        var random=new Random(91823);var hash=java.security.MessageDigest.getInstance("SHA-256");
        var buffer=new ByteArrayOutputStream();var output=new DataOutputStream(buffer);
        for(int size:new int[]{8,16,32})for(int mode=0;mode<4;mode++) {
            var tree=new SparseVoxelOctree(size);
            if(mode==1)tree.fill(0,0,0,size,size,size,0xff123456);
            if(mode==2)for(int y=0;y<size;y++)for(int z=0;z<size;z++)for(int x=0;x<size;x++)
                tree.set(x,y,z,0xff000000|random.nextInt(1<<24));
            if(mode==3)for(int i=0;i<size*2;i++)tree.set(random.nextInt(size),random.nextInt(size),random.nextInt(size),0xff000000|random.nextInt(1<<24));
            var mesh=ModelMesher.mesh(tree.freeze());output.writeInt(mesh.quads());
            for(float value:mesh.vertices())output.writeInt(Float.floatToRawIntBits(value));
            for(int value:mesh.indices())output.writeInt(value);
        }
        assertEquals("39a6a09f93231f026425c3d20eda01ee780ac1508328e2c4ba57b68a4e367dc0",HexFormat.of().formatHex(hash.digest(buffer.toByteArray())));
    }
    @Test void exactLimitAndRejectedSurfaceUseTheSameRuleAndMessage()throws Exception {
        var solid=new SparseVoxelOctree(8);solid.fill(0,0,0,8,8,8,0xff123456);
        assertEquals(6,ModelMesher.count(8,solid::get,6));assertEquals(6,ModelMesher.mesh(8,solid::get,6).quads());
        var counted=assertThrows(IOException.class,()->ModelMesher.count(8,solid::get,5));
        var rendered=assertThrows(IOException.class,()->ModelMesher.mesh(8,solid::get,5));assertEquals(rendered.getMessage(),counted.getMessage());
        assertEquals(0,ModelMesher.count(8,(x,y,z)->0,0));
        var fragmented=new SparseVoxelOctree(16);
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)if(((x+y+z)&1)==0)fragmented.set(x,y,z,0xff123456);
        counted=assertThrows(IOException.class,()->ModelMesher.count(fragmented));rendered=assertThrows(IOException.class,()->ModelMesher.mesh(fragmented));
        assertEquals(rendered.getMessage(),counted.getMessage());
    }
}
