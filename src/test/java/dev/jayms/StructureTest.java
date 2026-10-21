package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import static dev.jayms.net.structure.StructurePackage.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

class StructureTest {
    @TempDir Path folder;
    StructurePackage asset() throws Exception {
        var geometry=new SparseVoxelOctree(16);geometry.fill(2,4,6,4,6,8,WorldVoxels.encode(new Protocol.Edit(0,0,0,Blocks.LED).withColor(0x987654)));
        var model=new SparseVoxelOctree(32);model.set(1,3,5,0xff123456);
        return new StructurePackage("House",new Bounds(80,256,16,1,2,3),List.of(new ModelDefinition("Component",model)),
                List.of(new Cell(new Position(0,0,0),geometry,-1),new Cell(new Position(79,255,15),null,0)));
    }
    @Test void compactPackageCellsKeepCoordinateExtremesModelsAndImmutableSnapshots() throws Exception {
        var stone=new SparseVoxelOctree(16);stone.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        var component=new SparseVoxelOctree(32);component.set(31,31,31,0xffabcdef);
        var input=new ArrayList<Cell>();
        input.add(new Cell(new Position(1023,255,1023),stone,-1));
        input.add(new Cell(new Position(0,0,0),null,0));
        input.add(new Cell(new Position(1023,0,0),stone,-1));
        input.add(new Cell(new Position(0,255,1023),stone,-1));
        var asset=new StructurePackage("Coordinate extremes",new Bounds(1024,256,1024,0,0,0),
            List.of(new ModelDefinition("Corner component",component)),input);
        input.clear();stone.set(0,0,0,0);component.set(31,31,31,0);
        assertEquals(4,asset.cells.size());
        assertEquals(new Position(0,0,0),asset.cells.get(0).position());assertEquals(0,asset.cells.get(0).model());
        var positions=new HashSet<Position>();
        for(var cell:asset.cells) {
            positions.add(cell.position());
            if(cell.model()<0){assertEquals(4096,cell.geometry().occupied());assertThrows(IllegalStateException.class,()->cell.geometry().set(0,0,0,0));}
        }
        assertEquals(Set.of(new Position(0,0,0),new Position(1023,255,1023),new Position(1023,0,0),new Position(0,255,1023)),positions);
        assertEquals(1,asset.models.get(0).voxels().occupied());
        assertThrows(IndexOutOfBoundsException.class,()->asset.cells.get(-1));
        assertThrows(IndexOutOfBoundsException.class,()->asset.cells.get(4));
        assertThrows(UnsupportedOperationException.class,()->asset.cells.set(0,asset.cells.get(0)));
        assertThrows(UnsupportedOperationException.class,()->asset.cells.add(asset.cells.get(0)));
        assertThrows(UnsupportedOperationException.class,()->asset.cells.remove(0));
        assertThrows(UnsupportedOperationException.class,()->asset.cells.subList(0,1).clear());
        var loaded=StructurePackage.decode(asset.encode());
        assertEquals(asset.cells.stream().map(Cell::position).toList(),loaded.cells.stream().map(Cell::position).toList());
        assertEquals(asset.cells.stream().map(Cell::model).toList(),loaded.cells.stream().map(Cell::model).toList());
        assertArrayEquals(asset.encode(),loaded.encode());
    }
    @Test void importCommandRetainsLongPathsAndBoundsInput() throws Exception {
        try(var menu=new dev.jayms.ui.StructureMenu(folder)) {
            String command="import /"+"nested directory/".repeat(30)+"house.vstr";
            command.chars().forEach(menu::character);
            assertEquals(command,menu.input);
            for(int i=0;i<5000;i++)menu.character(97);
            assertEquals(4096,menu.input.length());
        }
    }
    @Test void stagedUniformCellsShareImmutableTreesWithoutLinkingWorldEdits() throws Exception {
        var stone=new SparseVoxelOctree(16);stone.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        var asset=new StructurePackage("Repeated blocks",new Bounds(3,1,1,0,0,0),List.of(),List.of(
            new Cell(new Position(0,0,0),stone,-1),new Cell(new Position(1,0,0),stone,-1)));
        try(var world=new World(42)) {
            var engine=new StructureEngine();
            try(var plan=engine.prepare(asset,new Position(0,180,0),0,world.structureSnapshot(),()->false,p->{})) {
                var first=plan.cells().get(new Position(0,180,0));var second=plan.cells().get(new Position(1,180,0));
                assertSame(first,second,"Repeated uniform materials need one immutable staging tree");
                assertThrows(IllegalStateException.class,()->first.set(0,0,0,0));
                stone.set(0,0,0,0);assertEquals(4096,first.occupied());
                var editable=first.copy();editable.set(0,0,0,0);
                assertEquals(4096,second.occupied());assertEquals(4095,editable.occupied());
            }
        }
    }
    @Test void sparseCaptureSkipsEmptyOctreeReadsWithoutSkippingProgressOrValidation()throws Exception {
        var geometry=new SparseVoxelOctree(16);geometry.fill(2,3,4,3,4,5,WorldVoxels.encode(new Protocol.Edit(0,0,0,Blocks.LED).withColor(0x22aaff)));
        int[] reads={0},progress={0};var library=new ModelLibrary();
        Source fast=new Source(){
            public SparseVoxelOctree cell(int x,int y,int z){reads[0]++;if(x!=0||y!=180||z!=0)throw new AssertionError("Empty snapshot read");return geometry;}
            public boolean emptyCell(int x,int y,int z){return x!=0||y!=180||z!=0;}
            public ModelLibrary models(){return library;}
        };
        var asset=StructurePackage.capture("Sparse",new Bounds(128,1,128,0,0,0),new Position(0,180,0),fast,()->false,p->progress[0]=p);
        assertEquals(1,reads[0]);assertEquals(1,asset.cells.size());assertEquals(100,progress[0]);
        assertEquals(geometry.leaves(),asset.cells.get(0).geometry().leaves());
        assertThrows(IOException.class,()->StructurePackage.capture("Outside",new Bounds(1,2,1,0,0,0),new Position(0,1023,0),fast,()->false,p->{}));
        assertThrows(java.util.concurrent.CancellationException.class,()->StructurePackage.capture("Cancel",new Bounds(1,1,1,0,0,0),new Position(0,180,0),fast,()->true,p->{}));
    }
    @Test void defaultCaptureSourceIsReadOnlyOncePerCoordinate()throws Exception {
        int[] reads={0};var library=new ModelLibrary();var tree=new SparseVoxelOctree(16);
        tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        Source source=new Source(){
            public SparseVoxelOctree cell(int x,int y,int z){reads[0]++;return x==1?tree:new SparseVoxelOctree(16);}
            public ModelLibrary models(){return library;}
        };
        var asset=StructurePackage.capture("Default source",new Bounds(3,1,1,0,0,0),new Position(0,180,0),source,()->false,p->{});
        assertEquals(3,reads[0]);assertEquals(new Position(1,0,0),asset.cells.get(0).position());
    }
    @Test void worldSnapshotEmptyQueryKeepsTerrainFractionalAndModelCells()throws Exception {
        try(var world=new World(42)) {
            world.apply(new Protocol.Edit(0,180,0,Blocks.LED,3,1,2,3,0x123456));
            var modelTree=new SparseVoxelOctree(8);modelTree.set(0,0,0,0xff123456);
            int model=world.models().register(new ModelDefinition("Snapshot component",modelTree),"fixture").id();
            world.apply(new Protocol.Edit(1,180,0,model));
            var source=world.structureSnapshot();
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y:new int[]{0,64,127,180,181})
                assertEquals(source.cell(x,y,z).isEmpty(),source.emptyCell(x,y,z));
            world.apply(new Protocol.Edit(0,180,0,0));
            assertFalse(source.emptyCell(0,180,0),"Captured snapshot does not follow later world edits");
        }
    }
    @Test void packageRoundtripKeepsFractionalColoursModel32AndLargeBounds() throws Exception {
        var original=asset();var copy=StructurePackage.decode(original.encode());
        assertEquals(original.bounds,copy.bounds);assertEquals(original.cells.get(0).geometry().leaves(),copy.cells.get(0).geometry().leaves());
        assertEquals(32,copy.models.get(0).voxels().size());assertEquals(0xff123456,copy.models.get(0).voxels().get(1,3,5));
        var path=folder.resolve("house.vstr");copy.save(path);assertEquals(copy.name,StructurePackage.load(path).name);
    }
    @Test void malformedAndTrailingCompressedDataAreRejected() throws Exception {
        var bytes=asset().encode();bytes[bytes.length-8]^=1;
        assertThrows(IOException.class,()->StructurePackage.decode(bytes));
        var good=asset().encode();assertThrows(IOException.class,()->StructurePackage.decode(Arrays.copyOf(good,good.length+1)));
        assertThrows(IOException.class,()->StructurePackage.decode(Arrays.copyOf(good,12)));
        assertThrows(IllegalArgumentException.class,()->new Bounds(1025,1,1,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new Bounds(1,257,1,0,0,0));
        assertThrows(IOException.class,()->new StructurePackage("Duplicate",new Bounds(1,1,1,0,0,0),List.of(),
                List.of(new Cell(new Position(0,0,0),new SparseVoxelOctree(16),-1))));
    }
    @Test void tallSkyscraperAndLongRoadKeepCellDimensionsAcrossRestart()throws Exception {
        var stone=new SparseVoxelOctree(16);stone.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        var cells=new ArrayList<Cell>();
        for(int x=0;x<1024;x++)cells.add(new Cell(new Position(x,0,0),stone,-1));
        for(int y=1;y<256;y++)cells.add(new Cell(new Position(0,y,0),stone,-1));
        var asset=StructurePackage.decode(new StructurePackage("Road and skyscraper",new Bounds(1024,256,1,0,0,0),List.of(),cells).encode());
        var file=folder.resolve("tall-road.dat");var local=new LocalGame(file,42);var world=new World(42,local.models);var engine=new StructureEngine();
        try(var staged=engine.prepare(asset,new Position(320,128,320),0,world.structureSnapshot(),()->false,p->{})){staged.commit("me",local.structureAuthority(world));}
        assertEquals(Blocks.STONE,world.sample(1343,128,320));assertEquals(Blocks.STONE,world.sample(320,383,320));
        var loaded=new LocalGame(file,0);var restored=new World(loaded.seed,loaded.models);for(var edit:loaded.edits.values())restored.apply(edit);
        assertEquals(Blocks.STONE,restored.sample(1343,128,320));assertEquals(Blocks.STONE,restored.sample(320,383,320));
        engine.undo("me",local.structureAuthority(world));assertEquals(0,world.cell(320,383,320).occupied());
    }
    @Test void modelInstanceSurfaceBudgetsRejectComplexityBeforeMutation()throws Exception {
        var tree=new SparseVoxelOctree(32);
        for(int i=0;i<32768;i++)tree.set(i%32,(i/32)%32,i/1024,0xff000000|i);
        var definition=new ModelDefinition("Dense component",tree);var cells=new ArrayList<Cell>();
        for(int i=0;i<180;i++)cells.add(new Cell(new Position(i,0,0),null,0));
        assertThrows(IOException.class,()->new StructurePackage("Too many surfaces",new Bounds(180,1,1,0,0,0),List.of(definition),cells));
        cells.clear();for(int z=0;z<8;z++)for(int x=0;x<8;x++)cells.add(new Cell(new Position(x,0,z),null,0));
        var asset=new StructurePackage("Dense cluster",new Bounds(8,1,8,0,0,0),List.of(definition),cells);
        var local=new LocalGame(folder.resolve("dense-models.dat"),42);var world=new World(42,local.models);var engine=new StructureEngine();
        assertThrows(IOException.class,()->engine.prepare(asset,new Position(320,128,320),0,world.structureSnapshot(),()->false,p->{}));
        assertTrue(local.edits.isEmpty());assertFalse(local.models.hasStructureModels());assertFalse(engine.busy());
    }
    @Test void fragmentedChunkIsRejectedBeforePublication()throws Exception {
        var tree=new SparseVoxelOctree(16);
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
            if((x+y+z)%2==0)tree.set(x,y,z,WorldVoxels.encode(Blocks.STONE));
        var cells=new ArrayList<Cell>();
        for(int z=0;z<8;z++)for(int x=0;x<8;x++)cells.add(new Cell(new Position(x,0,z),tree,-1));
        var asset=new StructurePackage("Fragmented",new Bounds(8,1,8,0,0,0),List.of(),cells);
        var local=new LocalGame(folder.resolve("fragmented.dat"),42);var world=new World(42,local.models);var engine=new StructureEngine();
        assertThrows(IOException.class,()->engine.prepare(asset,new Position(320,128,320),0,world.structureSnapshot(),()->false,p->{}));
        assertFalse(engine.busy());assertTrue(local.edits.isEmpty());assertEquals(0,world.cell(320,128,320).occupied());
    }
    @Test void worldOctreeResolutionRejectedBeforeReadingNodes() throws Exception {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeInt(0);out.writeInt(128);out.writeInt(0);out.writeByte(0);out.writeShort(256);
        var failure=assertThrows(IOException.class,()->Protocol.CellState.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        assertEquals("Invalid octree resolution",failure.getMessage());
    }
    @Test void durableSaveFailureRollsBackGeometryHistoryAndDependencies()throws Exception {
        var path=folder.resolve("failed-world.dat");var local=new LocalGame(path,42);var world=new World(42,local.models);var engine=new StructureEngine();
        Files.createDirectory(path);Files.writeString(path.resolve("keep"),"synthetic obstruction");
        var history=Map.copyOf(local.edits);var models=local.models.entries().size();
        try(var staged=engine.prepare(asset(),new Position(300,128,300),0,world.structureSnapshot(),()->false,p->{})) {
            var positions=List.copyOf(staged.cells().keySet());
            assertThrows(IOException.class,()->staged.commit("me",local.structureAuthority(world)));
            assertEquals(history,local.edits);assertEquals(models,local.models.entries().size());
            for(var p:positions)assertEquals(0,world.cell(p.x(),p.y(),p.z()).occupied());
        }
        assertFalse(engine.busy());assertEquals("synthetic obstruction",Files.readString(path.resolve("keep")));
    }
    @Test void undoRejectsChangedPlacedCellsAndNeverClobbersLaterEdits()throws Exception {
        var local=new LocalGame(folder.resolve("undo-world.dat"),42);var world=new World(42,local.models);var engine=new StructureEngine();
        Position changed;
        try(var staged=engine.prepare(asset(),new Position(300,128,300),0,world.structureSnapshot(),()->false,p->{})) {changed=staged.cells().keySet().iterator().next();staged.commit("me",local.structureAuthority(world));}
        world.apply(new Protocol.Edit(changed.x(),changed.y(),changed.z(),Blocks.STONE));
        assertThrows(IOException.class,()->engine.undo("me",local.structureAuthority(world)));
        assertEquals(Blocks.STONE,world.sample(changed.x(),changed.y(),changed.z()));
    }
    @Test void fourRotationsPreserveEveryLeafAndRestoreOrientation() throws Exception {
        var asset=asset();var source=asset.cells.get(0).geometry();var current=source;
        for(int r=0;r<4;r++) {
            assertEquals(source.occupied(),StructureEngine.rotate(source,r).occupied());
            var box=StructureEngine.footprint(asset,new Position(4,30,8),r);
            assertEquals(r%2==0?80:16,box.width());assertEquals(r%2==0?16:80,box.depth());
            current=StructureEngine.rotate(current,1);
        }
        assertEquals(source.leaves(),current.leaves());
    }
    @Test void dependencyNamespaceSupports512WithoutChangingInventories() throws Exception {
        var library=new ModelLibrary();
        for(int i=0;i<512;i++) {
            var tree=new SparseVoxelOctree(8);tree.set(0,0,0,0xff000000|i);
            int id=library.stageStructureModel(new ModelDefinition("Part "+i,tree)).id();
            assertTrue(Blocks.structureModel(id));assertFalse(Items.valid(id));assertEquals(id,WorldVoxels.decode(WorldVoxels.encode(id)));
        }
        assertThrows(IOException.class,()->library.write(new DataOutputStream(new ByteArrayOutputStream())));
        var bytes=new ByteArrayOutputStream();library.writeAll(new DataOutputStream(bytes));
        var copy=ModelLibrary.readAll(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(513,copy.entries().size());
        assertEquals(Inventory.SIZE*2,newInventoryBytes().length);
    }
    byte[] newInventoryBytes() throws Exception {var bytes=new ByteArrayOutputStream();new Inventory().write(new DataOutputStream(bytes));return bytes.toByteArray();}
    @Test void offlinePlacementConflictCancellationUndoAndRestartAreAtomic() throws Exception {
        var local=new LocalGame(folder.resolve("world.dat"),42);var world=new World(42,local.models);var engine=new StructureEngine(1);
        // A two-cell operation must retain one-operation undo even above a one-cell history target.
        var authority=local.structureAuthority(world);var asset=asset();var cancel=new AtomicBoolean();
        try(var plan=engine.prepare(asset,new Position(100,120,100),1,world.structureSnapshot(),cancel::get,v->{})) {
            cancel.set(true);assertThrows(java.util.concurrent.CancellationException.class,()->plan.commit("me",authority));
        }
        assertFalse(local.models.hasStructureModels());assertTrue(local.edits.isEmpty());cancel.set(false);
        try(var plan=engine.prepare(asset,new Position(100,120,100),1,world.structureSnapshot(),cancel::get,v->{})) {plan.commit("me",authority);}
        assertTrue(local.models.hasStructureModels());
        assertThrows(IOException.class,()->engine.prepare(asset,new Position(100,120,100),1,world.structureSnapshot(),()->false,v->{}));
        var restarted=new LocalGame(folder.resolve("world.dat"),0);assertEquals(local.edits,restarted.edits);assertTrue(restarted.models.hasStructureModels());
        world.apply(new Protocol.Edit(800,120,800,Blocks.STONE));WorldVoxels.remember(local.edits,new Protocol.Edit(800,120,800,Blocks.STONE));
        engine.undo("me",authority);assertEquals(Blocks.STONE,world.sample(800,120,800));
        for(var cell:asset.cells) {var box=StructureEngine.footprint(asset,new Position(100,120,100),1);var p=StructureEngine.rotate(cell.position(),80,16,1);assertEquals(0,world.cell(box.minX()+p.x(),box.minY()+p.y(),box.minZ()+p.z()).occupied());}
        try(var plan=engine.prepare(asset,new Position(100,120,100),0,world.structureSnapshot(),()->false,v->{})) {
            var p=plan.cells().keySet().iterator().next();world.apply(new Protocol.Edit(p.x(),p.y(),p.z(),Blocks.STONE));
            var history=Map.copyOf(local.edits);assertThrows(IOException.class,()->plan.commit("me",authority));assertEquals(history,local.edits);
        }
    }
    @Test void editingSourceDefinitionsAndGeometryCannotChangePlacedCopies()throws Exception {
        var geometry=new SparseVoxelOctree(16);geometry.set(1,2,3,WorldVoxels.encode(Blocks.BRICKS));
        var voxels=new SparseVoxelOctree(32);voxels.set(2,4,6,0xff123456);
        var asset=new StructurePackage("Independent",new Bounds(2,1,1,0,0,0),List.of(new ModelDefinition("Original",voxels)),List.of(new Cell(new Position(0,0,0),geometry,-1),new Cell(new Position(1,0,0),null,0)));
        var local=new LocalGame(folder.resolve("independent.dat"),42);var world=new World(42,local.models);var engine=new StructureEngine();
        try(var staged=engine.prepare(asset,new Position(320,128,320),0,world.structureSnapshot(),()->false,p->{})){staged.commit("me",local.structureAuthority(world));}
        geometry.set(1,2,3,WorldVoxels.encode(Blocks.STONE));voxels.set(2,4,6,0xffabcdef);
        assertEquals(WorldVoxels.encode(Blocks.BRICKS),world.cell(320,128,320).get(1,2,3));
        int id=world.sample(321,128,320);assertEquals(0xff123456,local.models.get(id).definition().voxels().get(2,4,6));
        asset.save(folder.resolve("asset.vstr"));
        new StructurePackage("Independent",new Bounds(1,1,1,0,0,0),List.of(),List.of(new Cell(new Position(0,0,0),geometry,-1))).save(folder.resolve("asset.vstr"));
        assertEquals(0xff123456,local.models.get(id).definition().voxels().get(2,4,6));
    }
    @Test void captureIncludesTerrainOverridesAndDeduplicatedDependencies() throws Exception {
        var world=new World(42);world.apply(new Protocol.Edit(0,120,0,Blocks.FLOWER_POT));world.apply(new Protocol.Edit(2,120,0,Blocks.FLOWER_POT));
        var result=StructurePackage.capture("Road",new Bounds(64,1,1,0,0,0),new Position(0,120,0),world.structureSnapshot(),()->false,p->{});
        assertEquals(2,result.cells.size());assertEquals(1,result.models.size());
    }
}
