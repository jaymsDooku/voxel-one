import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;

/** Checks fixture history-count assumptions against the actual canonical replay path. */
public final class HistoryRecordCountProbe {
    private static SparseVoxelOctree full(int material) {
        var tree=new SparseVoxelOctree(16);
        tree.fill(0,0,0,16,16,16,WorldVoxels.encode(material));
        return tree.freeze();
    }
    private static SparseVoxelOctree fragmented() {
        var tree=new SparseVoxelOctree(16);
        tree.fill(0,0,0,1,1,1,0xfe123456);
        tree.fill(15,15,15,16,16,16,0xfeabcdef);
        return tree.freeze();
    }
    private static void check(String label,SparseVoxelOctree tree,int expected) {
        var indexed=new WorldVoxels.History();
        var legacy=new java.util.LinkedHashMap<String,Protocol.Edit>();
        var cell=new Protocol.CellState(0,0,0,tree);
        WorldVoxels.rememberCell(indexed,cell);WorldVoxels.rememberCell(legacy,cell);
        if(indexed.size()!=expected||!indexed.equals(legacy))throw new AssertionError(label);
        WorldVoxels.rememberCell(indexed,cell);
        if(indexed.size()!=expected)throw new AssertionError("Repeated replacement: "+label);
        WorldVoxels.rememberCell(indexed,new Protocol.CellState(0,0,0,full(Blocks.STONE)));
        if(indexed.size()!=1)throw new AssertionError("Later full-cell replacement: "+label);
        System.out.printf("{\"case\":\"%s\",\"records\":%d,\"indexed_legacy_equal\":true,\"replacement_checked\":true}%n",label,expected);
    }
    public static void main(String[] args) {
        check("whole_geometry",full(Blocks.STONE),1);
        check("model_instance",full(1024),1);
        var fractional=new SparseVoxelOctree(16);
        fractional.fill(0,0,0,8,8,8,WorldVoxels.encode(Blocks.STONE));
        check("single_fractional_leaf",fractional.freeze(),2);
        check("two_separated_fractional_leaves",fragmented(),3);
    }
}
