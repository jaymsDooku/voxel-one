package dev.jayms.net.structure;

import dev.jayms.net.model.SparseVoxelOctree;
import java.util.AbstractList;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import static dev.jayms.net.structure.StructurePackage.*;

/** Compact immutable storage for already validated, frozen, spatially sorted cells. */
final class StructureCells extends AbstractList<Cell> implements RandomAccess {
    private final int[] positions;
    private final short[] models;
    private final SparseVoxelOctree[] geometry;

    StructureCells(List<Cell> cells) {
        int size=cells.size();
        positions=new int[size];models=new short[size];geometry=new SparseVoxelOctree[size];
        for(int i=0;i<size;i++) {
            var cell=cells.get(i);var p=cell.position();
            positions[i]=p.x()|(p.z()<<10)|(p.y()<<20);
            models[i]=(short)cell.model();geometry[i]=cell.geometry();
        }
    }
    @Override public int size(){return positions.length;}
    @Override public Cell get(int index) {
        Objects.checkIndex(index,positions.length);
        int p=positions[index];
        return new Cell(new Position(p&1023,(p>>>20)&255,(p>>>10)&1023),geometry[index],models[index]);
    }
}
