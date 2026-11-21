package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SparseVoxelPackedCellTest {
    static SparseVoxelOctree fixture() {
        var tree=new SparseVoxelOctree(16);tree.fill(1,2,3,7,8,9,WorldVoxels.encode(Blocks.BRICKS));
        tree.fill(12,13,14,13,14,15,0xfe123456);return tree;
    }
    static byte[] bytes(SparseVoxelOctree tree)throws IOException {
        var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){tree.write(out);}return bytes.toByteArray();
    }
    static boolean packedWithoutGraph(SparseVoxelOctree tree)throws Exception {
        var root=SparseVoxelOctree.class.getDeclaredField("root");root.setAccessible(true);
        Object value=root.get(tree);if(value==null||!value.getClass().getSimpleName().equals("PackedCell"))return false;
        var children=value.getClass().getSuperclass().getDeclaredField("children");children.setAccessible(true);
        var packed=value.getClass().getDeclaredField("bytes");packed.setAccessible(true);
        return children.get(value)==null&&packed.get(value) instanceof byte[];
    }
    @Test void packedCellRetainsWireValuesQueriesAndNoNodeCache()throws Exception {
        var source=fixture();var cell=source.packedCellCopy();byte[] expected=bytes(source);
        assertTrue(packedWithoutGraph(cell));assertNotSame(source,cell);assertArrayEquals(expected,bytes(cell));
        assertTrue(source.sameValues(cell));assertTrue(cell.sameValues(source));assertTrue(cell.sameValues(cell));
        assertEquals(source.nodes(),cell.nodes());assertEquals(source.occupied(),cell.occupied());assertEquals(source.leafCount(),cell.leafCount());assertFalse(cell.isEmpty());
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)assertEquals(source.get(x,y,z),cell.get(x,y,z));
        assertEquals(0,cell.get(-1,0,0));assertEquals(0,cell.get(16,0,0));
        assertEquals(source.leaves(),cell.leaves());assertEquals(source.leavesInterruptibly(),cell.leavesInterruptibly());
        var visited=new ArrayList<SparseVoxelOctree.Leaf>();cell.forEachLeaf((x,y,z,side,color)->visited.add(new SparseVoxelOctree.Leaf(x,y,z,side,color)));assertEquals(source.leaves(),visited);
        for(int side:new int[]{1,2,4,8,16})for(int y=0;y<16;y+=side)for(int z=0;z<16;z+=side)for(int x=0;x<16;x+=side){assertEquals(source.uniform(x,y,z,side),cell.uniform(x,y,z,side));assertTrue(source.region(x,y,z,side).sameValues(cell.region(x,y,z,side)));}
        assertEquals(source.intersects(0,0,0,1,1,1),cell.intersects(0,0,0,1,1,1));assertEquals(source.intersects(0,0,0,.01f,.01f,.01f),cell.intersects(0,0,0,.01f,.01f,.01f));
        assertEquals(source.raycast(-1,.3f,.3f,1,0,0,0,4),cell.raycast(-1,.3f,.3f,1,0,0,0,4));assertNull(cell.raycast(Float.NaN,0,0,1,0,0,0,4));
        for(int rotation=0;rotation<4;rotation++)assertArrayEquals(bytes(source.rotatedY(rotation)),bytes(cell.rotatedY(rotation)));
        assertTrue(packedWithoutGraph(cell));assertArrayEquals(expected,bytes(cell));
        assertThrows(IllegalStateException.class,()->cell.set(0,0,0,0));assertThrows(IllegalStateException.class,()->cell.fill(0,0,0,1,1,1,0));assertThrows(IllegalStateException.class,()->cell.replaceRegion(0,0,0,source));
        var copy=cell.copy();copy.set(0,0,0,0xff123456);assertEquals(0,cell.get(0,0,0));
        var destination=new SparseVoxelOctree(32);destination.replaceRegion(16,0,16,cell);assertTrue(source.sameValues(destination.region(16,0,16,16)));
        source.fill(0,0,0,16,16,16,0);assertArrayEquals(expected,bytes(cell));
        var second=cell.packedCellCopy();assertNotSame(cell,second);assertTrue(packedWithoutGraph(second));assertArrayEquals(expected,bytes(second));
    }
    @Test void uniformAirLegacyLayoutsAndInterruptedPackingRemainSafe()throws Exception {
        var empty=new SparseVoxelOctree(16).packedCellCopy();assertTrue(empty.isEmpty());assertEquals(0,empty.nodes());
        var uniform=new SparseVoxelOctree(16);uniform.fill(0,0,0,16,16,16,0xfe123456);var packed=uniform.packedCellCopy();assertArrayEquals(bytes(uniform),bytes(packed));assertEquals(4096,packed.occupied());assertEquals(1,packed.leafCount());assertFalse(packedWithoutGraph(packed));
        // A valid legacy branch with eight equal leaves must retain its original wire layout.
        var raw=new ByteArrayOutputStream();try(var out=new DataOutputStream(raw)){out.writeByte(16);out.writeByte(2);for(int i=0;i<8;i++){out.writeByte(1);out.writeInt(0xffabcdef);}}
        var legacy=SparseVoxelOctree.read(new DataInputStream(new ByteArrayInputStream(raw.toByteArray())));var retained=legacy.packedCellCopy();assertArrayEquals(raw.toByteArray(),bytes(retained));assertEquals(9,retained.nodes());assertEquals(8,retained.leafCount());assertTrue(legacy.sameValues(retained));
        var airBytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(airBytes)){out.writeByte(16);out.writeByte(2);for(int i=0;i<8;i++)out.writeByte(0);}
        var redundantAir=SparseVoxelOctree.read(new DataInputStream(new ByteArrayInputStream(airBytes.toByteArray())));var packedAir=redundantAir.packedCellCopy();
        assertFalse(redundantAir.isEmpty());assertFalse(packedAir.isEmpty());assertEquals(0,packedAir.occupied());assertEquals(0,packedAir.leafCount());assertArrayEquals(airBytes.toByteArray(),bytes(packedAir));assertTrue(redundantAir.sameValues(packedAir));
        assertThrows(IllegalArgumentException.class,()->new SparseVoxelOctree(32).packedCellCopy());
        Thread.currentThread().interrupt();try {assertThrows(CancellationException.class,()->fixture().packedCellCopy());assertThrows(CancellationException.class,retained::leavesInterruptibly);}finally{Thread.interrupted();}
        assertArrayEquals(raw.toByteArray(),bytes(retained));
    }
    @Test void productionPreparationPacksOnlyFractionalCellsAndPreservesMapIdentityAndWire()throws Exception {
        var source=fixture();var uniform=new SparseVoxelOctree(16);uniform.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        var asset=new StructurePackage("Packed placement",new StructurePackage.Bounds(3,1,1,0,0,0),List.of(),List.of(new StructurePackage.Cell(new StructurePackage.Position(0,0,0),source,-1),new StructurePackage.Cell(new StructurePackage.Position(1,0,0),uniform,-1),new StructurePackage.Cell(new StructurePackage.Position(2,0,0),uniform,-1)));
        var empty=new SparseVoxelOctree(16).freeze();var input=new StructurePackage.Source(){final ModelLibrary models=new ModelLibrary();public ModelLibrary models(){return models;}public SparseVoxelOctree cell(int x,int y,int z){return empty;}};
        var engine=new StructureEngine();var target=new StructurePackage.Position(320,180,320);
        try(var prepared=engine.prepare(asset,target,0,input,()->false,p->{})) {
            var cells=prepared.cells();var value=cells.get(target);assertTrue(packedWithoutGraph(value));assertSame(value,cells.get(target));assertTrue(source.sameValues(value));
            var copied=new HashMap<>(cells);assertEquals(copied,cells);assertEquals(cells,copied);assertEquals(copied.hashCode(),cells.hashCode());
            assertSame(cells.get(new StructurePackage.Position(321,180,320)),cells.get(new StructurePackage.Position(322,180,320)));
            assertArrayEquals(bytes(source),bytes(value));assertTrue(packedWithoutGraph(value));assertTrue(input.cell(320,180,320).isEmpty());
        }
        assertFalse(engine.busy());
    }
    @Test void packedBytesStayPrivateAndConcurrentReadersKeepNoCache()throws Exception {
        var source=fixture();byte[] expected=bytes(source);var cell=source.packedCellCopy();
        try(var out=new DataOutputStream(new OutputStream(){public void write(int b){}public void write(byte[] b,int o,int n){Arrays.fill(b,o,o+n,(byte)0);}})){cell.write(out);}
        assertArrayEquals(expected,bytes(cell));
        var executor=Executors.newFixedThreadPool(2);
        try {
            var work=new ArrayList<Callable<Boolean>>();for(int i=0;i<32;i++)work.add(()->Arrays.equals(expected,bytes(cell))&&source.sameValues(cell)&&cell.get(12,13,14)==0xfe123456);
            for(var result:executor.invokeAll(work))assertTrue(result.get());
        } finally {executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
        assertTrue(packedWithoutGraph(cell));
    }
    @Test void fullyFragmentedCellStaysWithinOneBoundedRecord()throws Exception {
        var tree=new SparseVoxelOctree(16);var random=new Random(9812);
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)tree.set(x,y,z,0xff000000|random.nextInt(1<<24));
        byte[] expected=bytes(tree);assertTrue(expected.length>20000&&expected.length<=65536);
        var cell=tree.packedCellCopy();assertTrue(packedWithoutGraph(cell));assertEquals(4096,cell.occupied());assertEquals(4096,cell.leafCount());assertArrayEquals(expected,bytes(cell));assertTrue(tree.sameValues(cell));
        var visited=new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(IOException.class,()->cell.forEachLeaf((x,y,z,side,color)->{visited.incrementAndGet();throw new IOException("Synthetic visitor stop");}));
        assertEquals(1,visited.get());assertTrue(packedWithoutGraph(cell));assertArrayEquals(expected,bytes(cell));
    }
}
