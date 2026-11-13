import dev.jayms.*;import dev.jayms.net.*;import dev.jayms.net.model.*;import java.util.*;
public class StructureBudgetProbe {
 static long[] run(Chunk c,boolean exact){long[] a=new long[20];for(int i=-3;i<20;i++){long s=System.nanoTime();if(exact)MeshDataGenerator.count(c);else MeshDataGenerator.validateBudget(c);if(i>=0)a[i]=System.nanoTime()-s;}return a;}
 public static void main(String[] args){
  Chunk sparse=new Chunk();SparseVoxelOctree t=new SparseVoxelOctree(16);t.set(1,1,1,WorldVoxels.encode(Blocks.STONE));t.freeze();for(int z=0;z<8;z++)for(int x=0;x<16;x++)sparse.replaceCell(x,0,z,t);
  Chunk dense=new Chunk();SparseVoxelOctree d=new SparseVoxelOctree(16);d.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));d.set(0,0,0,0);d.freeze();for(int x=0;x<16;x++)dense.replaceCell(x,0,0,d);
  System.out.print("{\"sparse_exact_ns\":"+Arrays.toString(run(sparse,true))+",\"sparse_bound_ns\":"+Arrays.toString(run(sparse,false))+",\"dense_exact_ns\":"+Arrays.toString(run(dense,true))+",\"dense_fallback_ns\":"+Arrays.toString(run(dense,false))+"}");
 }}