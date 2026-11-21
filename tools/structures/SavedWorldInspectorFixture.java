package dev.jayms.net;

import dev.jayms.net.model.*;
import java.nio.file.*;
import java.util.List;

/** Synthetic version10 writer fixture, separate from the read-only client. */
public final class SavedWorldInspectorFixture {
    public static Path create(Path root)throws Exception {
        var file=root.resolve("synthetic saved world "+"long path segment ".repeat(6)+".dat");var models=new ModelLibrary();var rows=new WorldVoxels.History();
        var tree=new SparseVoxelOctree(8);for(int x=0;x<8;x++)for(int y=0;y<8;y++)for(int z=0;z<8;z++)tree.set(x,y,z,0xff267ac9);
        int model=models.stageStructureModel(new ModelDefinition("Synthetic component",tree)).id();
        for(int x=-8;x<=15;x++)for(int y=160;y<168;y++)WorldVoxels.remember(rows,new Protocol.Edit(x,y,0,x>=6&&x<=10?Blocks.LED:Blocks.STONE).withColor(x>=6&&x<=10?0xed593c:0));
        WorldVoxels.remember(rows,new Protocol.Edit(6,163,3,model));WorldVoxels.remember(rows,new Protocol.Edit(8,160,4,Blocks.WATER));
        WorldVoxels.remember(rows,new Protocol.Edit(10,162,2,Blocks.piece(Blocks.LED,1),1,1,0,0).withColor(0x3ccfa8));
        try(var save=StagedOfflineSave.stageRegions(file,42,Terrain.CURRENT_VERSION,models,rows,()->false,n->{})){save.publish(new Inventory(),20,List.of(),0);}
        return file;
    }
}
