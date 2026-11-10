package dev.jayms;

import dev.jayms.net.model.SparseVoxelOctree;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackedOctreeQueryTest {
    static byte[] bytes(SparseVoxelOctree t)throws IOException {
        var b=new ByteArrayOutputStream();t.write(new DataOutputStream(b));return b.toByteArray();
    }
    @Test void denseAndSparseWorldCellsMatchAllPointAndAlignedQueries()throws Exception {
        var random=new Random(20261110);
        for(int kind=0;kind<4;kind++) {
            var source=new SparseVoxelOctree(16);
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
                if(kind==0||random.nextInt(1<<kind)==0)source.fill(x,y,z,x+1,y+1,z+1,random.nextBoolean()?0xfe123456:0xffaabbcc);
            var packed=source.packedCellCopy();byte[] before=bytes(packed);
            for(int side:new int[]{1,2,4,8,16})for(int y=0;y<16;y+=side)for(int z=0;z<16;z+=side)for(int x=0;x<16;x+=side) {
                assertEquals(source.uniform(x,y,z,side),packed.uniform(x,y,z,side));
                if(side==1)assertEquals(source.get(x,y,z),packed.get(x,y,z));
            }
            assertEquals(0,packed.get(-1,0,0));assertEquals(0,packed.get(16,0,0));
            assertArrayEquals(before,bytes(packed));
        }
    }
    @Test void redundantBranchesPreserveStructuralUniformSemantics()throws Exception {
        for(int tag:new int[]{0,1}) {
            var b=new ByteArrayOutputStream();var out=new DataOutputStream(b);out.writeByte(16);out.writeByte(2);
            for(int i=0;i<8;i++){out.writeByte(tag);if(tag==1)out.writeInt(0xff123456);}
            var source=SparseVoxelOctree.read(new DataInputStream(new ByteArrayInputStream(b.toByteArray())),16);
            var packed=source.packedCellCopy();assertEquals(-1,packed.uniform(0,0,0,16));
            for(int y=0;y<16;y+=8)for(int z=0;z<16;z+=8)for(int x=0;x<16;x+=8)
                assertEquals(tag==0?0:0xff123456,packed.uniform(x,y,z,8));
            assertArrayEquals(b.toByteArray(),bytes(packed));
        }
    }
    @Test void modelSizesAndOverflowingAlignedQueriesKeepTheirBounds()throws Exception {
        var random=new Random(88);
        for(int size:new int[]{8,16,32}) {
            var source=new SparseVoxelOctree(size);
            for(int y=0;y<size;y++)for(int z=0;z<size;z++)for(int x=0;x<size;x++)source.set(x,y,z,((x+y+z)&1)==0?0xff123456:0xff654321);
            var packed=source.packedModelCopy();
            for(int i=0;i<100;i++) {
                int side=1<<random.nextInt(Integer.numberOfTrailingZeros(size)+1);
                int x=random.nextInt(size/side)*side,y=random.nextInt(size/side)*side,z=random.nextInt(size/side)*side;
                assertEquals(source.uniform(x,y,z,side),packed.uniform(x,y,z,side));
            }
            for(var t:List.of(source,packed)) {
                assertThrows(IllegalArgumentException.class,()->t.uniform(Integer.MAX_VALUE,0,0,1));
                assertThrows(IllegalArgumentException.class,()->t.uniform(0,Integer.MAX_VALUE,0,1));
                assertThrows(IllegalArgumentException.class,()->t.uniform(0,0,Integer.MAX_VALUE,1));
                assertThrows(IllegalArgumentException.class,()->t.uniform(1,0,0,2));
                assertThrows(IllegalArgumentException.class,()->t.uniform(0,0,0,0));
            }
        }
    }
    @Test void concurrentQueriesUseNoSharedCursorAndMutableCopiesStayIndependent()throws Exception {
        var source=new SparseVoxelOctree(16);source.fill(2,3,4,9,10,11,0xfe123456);
        var packed=source.packedCellCopy();var pool=Executors.newFixedThreadPool(2);
        try {
            var jobs=new ArrayList<Callable<Boolean>>();
            for(int i=0;i<8;i++)jobs.add(()->{
                for(int p=0;p<4096;p++) {
                    int x=p&15,y=p>>>8,z=(p>>>4)&15;
                    if(source.get(x,y,z)!=packed.get(x,y,z)||source.uniform(x,y,z,1)!=packed.uniform(x,y,z,1))return false;
                }
                return true;
            });
            for(var result:pool.invokeAll(jobs))assertTrue(result.get());
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
        var copy=packed.copy();copy.set(2,3,4,0);assertEquals(0xfe123456,packed.get(2,3,4));assertEquals(0,copy.get(2,3,4));
        assertArrayEquals(bytes(source),bytes(packed));
    }
}
