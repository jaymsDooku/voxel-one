import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import static dev.jayms.net.structure.StructurePackage.*;

/** Actual StructureEngine placement/undo against isolated private generation files. */
public final class RegionAuthorityCheck {
    private static int rejected,placements,undos;
    private interface Action{void run()throws Exception;}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void reject(Class<? extends Throwable> type,String message,Action action)throws Exception {
        try{action.run();}catch(Throwable failure){if(type.isInstance(failure)&&(message==null||String.valueOf(failure.getMessage()).contains(message))){rejected++;return;}throw new AssertionError(message,failure);}
        throw new AssertionError("Unexpected acceptance: "+message);
    }
    private static SparseVoxelOctree solid(int material){var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(material));return tree.freeze();}
    private static StructurePackage asset()throws IOException {
        var voxels=new SparseVoxelOctree(8);voxels.set(1,2,3,0xffaabbcc);voxels.set(4,5,6,0xff112233);
        var partial=new SparseVoxelOctree(16);partial.fill(1,2,3,2,3,4,0xfe112233);
        return new StructurePackage("private house parts",new Bounds(3,2,2,1,0,1),List.of(new ModelDefinition("component",voxels)),List.of(
                new Cell(new Position(0,0,0),solid(Blocks.STONE),-1),new Cell(new Position(2,1,0),partial,-1),new Cell(new Position(1,0,1),null,0)));
    }
    private static RegionAuthorityPrototype open(Path root)throws IOException{return new RegionAuthorityPrototype(root,123,Terrain.CURRENT_VERSION);}
    private static long temps(Path root)throws IOException{try(var files=Files.list(root)){return files.filter(p->p.getFileName().toString().startsWith(".region-stage-")).count();}}
    private static void physical(StructurePackage.Source expected,StructurePackage.Source actual,Position p) {
        var before=expected.cell(p.x(),p.y(),p.z());var after=actual.cell(p.x(),p.y(),p.z());
        int type=WorldVoxels.decode(before.uniform(0,0,0,16));
        if(Blocks.isModel(type)){int other=WorldVoxels.decode(after.uniform(0,0,0,16));require(Blocks.isModel(other)&&expected.models().get(type).definition().fingerprint().equals(actual.models().get(other).definition().fingerprint()),"Component definition changed");}
        else require(before.sameValues(after),"Geometry changed across root/file replay");
    }
    private static void place(StructureEngine engine,StructurePackage asset,Position anchor,RegionAuthorityPrototype authority)throws IOException {
        try(var prepared=engine.prepare(asset,anchor,0,authority.snapshot(),()->false,n->{})){prepared.commit("synthetic",authority);placements++;}
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Synthetic parent directory required");Path parent=Path.of(args[0]);Files.createDirectories(parent);Path work=Files.createTempDirectory(parent,"authority-");
        try{run(work);}finally{try(var paths=Files.walk(work)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
    }
    private static void run(Path work)throws Exception {
        var asset=asset();var anchor=new Position(100,128,100);
        for(int rotation=0;rotation<4;rotation++) {
            final int pose=rotation;
            var path=work.resolve("rotation-"+rotation);var authority=open(path);var engine=new StructureEngine();
            var box=StructureEngine.footprint(asset,anchor,rotation);var relative=StructureEngine.rotate(new Position(2,0,1),3,2,rotation);
            var sentinel=new Position(box.minX()+relative.x(),box.minY(),box.minZ()+relative.z());authority.edit(new Protocol.Edit(sentinel.x(),sentinel.y(),sentinel.z(),Blocks.GLASS));
            var old=authority.snapshot();Map<Position,SparseVoxelOctree> cells;
            try(var prepared=engine.prepare(asset,anchor,rotation,old,()->false,n->{})){cells=prepared.cells();prepared.commit("synthetic",authority);placements++;}
            require(authority.generation()==1&&!engine.busy(),"Publication/busy state differs");
            require(old.cell(anchor.x(),anchor.y(),anchor.z()).occupied()==0&&old.models().get(1024)==null,"Old snapshot mutated");
            require(Blocks.isModel(WorldVoxels.decode(authority.cell(anchor.x(),anchor.y(),anchor.z()).uniform(0,0,0,16))),"Anchor alignment changed");
            var placed=authority.snapshot();var restarted=open(path);
            for(var entry:cells.entrySet()){var p=entry.getKey();require(authority.cell(p.x(),p.y(),p.z()).sameValues(entry.getValue()),"Engine prepared cells changed");physical(placed,restarted,p);}
            physical(placed,restarted,sentinel);physical(placed,restarted,new Position(500,0,500));
            long generation=authority.generation();reject(IOException.class,"Occupied",()->engine.prepare(asset,anchor,pose,authority.snapshot(),()->false,n->{}));
            require(authority.generation()==generation&&!engine.busy()&&temps(path)==0,"Overlap changed state");
            authority.edit(new Protocol.Edit(200,128,200,Blocks.PLANKS));engine.undo("synthetic",authority);undos++;
            for(var p:cells.keySet())require(authority.cell(p.x(),p.y(),p.z()).occupied()==0,"Undo left partial structure");
            require(authority.cell(200,128,200).uniform(0,0,0,16)==WorldVoxels.encode(Blocks.PLANKS),"Undo lost unrelated edit");
            require(authority.cell(sentinel.x(),sentinel.y(),sentinel.z()).uniform(0,0,0,16)==WorldVoxels.encode(Blocks.GLASS),"Empty source overwrote destination");
            physical(authority.snapshot(),open(path),new Position(200,128,200));
            for(var p:cells.keySet())require(placed.cell(p.x(),p.y(),p.z()).occupied()!=0,"Later undo changed a retained snapshot");
            reject(IOException.class,null,()->engine.undo("synthetic",authority));
            reject(IOException.class,"identity",()->new RegionAuthorityPrototype(path,124,Terrain.CURRENT_VERSION));
            reject(IOException.class,"identity",()->new RegionAuthorityPrototype(path,123,Terrain.LEGACY_VERSION));
        }
        var stalePath=work.resolve("stale-root");var stale=open(stalePath);var staleEngine=new StructureEngine();
        stale.beforeApply=()->{try{stale.edit(new Protocol.Edit(300,128,300,Blocks.GLASS));}catch(IOException failure){throw new UncheckedIOException(failure);}};
        reject(IOException.class,"Root changed",()->place(staleEngine,asset,anchor,stale));
        require(stale.generation()==0&&stale.cell(anchor.x(),anchor.y(),anchor.z()).occupied()==0&&stale.cell(300,128,300).occupied()!=0&&!staleEngine.busy()&&temps(stalePath)==0,"Stale-root failure lost an edit or published partial cells");
        place(staleEngine,asset,anchor,stale);physical(stale.snapshot(),open(stalePath),new Position(300,128,300));
        var changed=StructureEngine.footprint(asset,anchor,0);var stone=new Position(changed.minX(),changed.minY(),changed.minZ());
        stale.edit(new Protocol.Edit(stone.x(),stone.y(),stone.z(),Blocks.GLASS));byte[] disk=Files.readAllBytes(stalePath.resolve("current.region"));
        reject(IOException.class,"changed",()->staleEngine.undo("synthetic",stale));
        require(stale.cell(stone.x(),stone.y(),stone.z()).uniform(0,0,0,16)==WorldVoxels.encode(Blocks.GLASS)&&Arrays.equals(disk,Files.readAllBytes(stalePath.resolve("current.region")))&&temps(stalePath)==0,"Failed undo changed live/file state");
        int originalModels=stale.models().entries().size();var copy=stale.models();var extra=new SparseVoxelOctree(8);extra.set(1,1,1,0xff123456);
        copy.stageStructureModel(new ModelDefinition("copy only",extra));require(stale.models().entries().size()==originalModels,"Returned model copy mutated live root");
        var sharedPath=work.resolve("disk-fence");var first=open(sharedPath);var outdated=open(sharedPath);var firstEngine=new StructureEngine();
        place(firstEngine,asset,anchor,first);var prior=Files.readAllBytes(sharedPath.resolve("current.region"));var outdatedEngine=new StructureEngine();
        reject(IOException.class,"Disk generation",()->place(outdatedEngine,asset,new Position(400,128,400),outdated));
        require(outdated.generation()==0&&!outdatedEngine.busy()&&temps(sharedPath)==0&&Arrays.equals(prior,Files.readAllBytes(sharedPath.resolve("current.region"))),"Stale live root overwrote a newer disk generation");
        physical(first.snapshot(),open(sharedPath),anchor);
        for(String fault:List.of("force","move","unsupported")) {
            var armed=new AtomicBoolean(true);var path=work.resolve(fault);var authority=new RegionAuthorityPrototype(path,123,Terrain.CURRENT_VERSION,new RegionGenerationPrototype.Operations(){
                public void force(FileChannel file)throws IOException{if(armed.get()&&fault.equals("force"))throw new IOException("injected force");RegionGenerationPrototype.NIO.force(file);}
                public void replace(Path stage,Path current)throws IOException{if(armed.get()){if(fault.equals("unsupported"))throw new AtomicMoveNotSupportedException(stage.toString(),current.toString(),"injected unsupported");throw new IOException("injected move");}RegionGenerationPrototype.NIO.replace(stage,current);}
            });var engine=new StructureEngine();
            reject(IOException.class,null,()->place(engine,asset,anchor,authority));
            require(authority.generation()==0&&authority.cell(anchor.x(),anchor.y(),anchor.z()).occupied()==0&&authority.models().get(1024)==null&&!engine.busy()&&temps(path)==0&&!Files.exists(path.resolve("current.region")),"Failed publication changed a root/model/file");
            reject(IOException.class,null,()->engine.undo("synthetic",authority));armed.set(false);place(engine,asset,anchor,authority);physical(authority.snapshot(),open(path),anchor);
        }
        var cancel=new AtomicBoolean();var arm=new AtomicBoolean(true);var cancelPath=work.resolve("cancel");
        var cancelled=new RegionAuthorityPrototype(cancelPath,123,Terrain.CURRENT_VERSION,new RegionGenerationPrototype.Operations(){
            public void force(FileChannel file)throws IOException{RegionGenerationPrototype.NIO.force(file);if(arm.getAndSet(false))cancel.set(true);}
            public void replace(Path stage,Path current)throws IOException{RegionGenerationPrototype.NIO.replace(stage,current);}
        });var cancelledEngine=new StructureEngine();
        try(var prepared=cancelledEngine.prepare(asset,anchor,0,cancelled.snapshot(),cancel::get,n->{})){reject(CancellationException.class,null,()->prepared.commit("synthetic",cancelled));}
        require(cancelled.generation()==0&&cancelled.models().get(1024)==null&&temps(cancelPath)==0&&!cancelledEngine.busy(),"Cancelled placement published/leaked");
        cancel.set(false);place(cancelledEngine,asset,anchor,cancelled);
        var late=new AtomicBoolean();var postPath=work.resolve("post-move");
        var post=new RegionAuthorityPrototype(postPath,123,Terrain.CURRENT_VERSION,new RegionGenerationPrototype.Operations(){
            public void force(FileChannel file)throws IOException{RegionGenerationPrototype.NIO.force(file);}
            public void replace(Path stage,Path current)throws IOException{RegionGenerationPrototype.NIO.replace(stage,current);late.set(true);throw new IOException("injected after rename");}
        });var postEngine=new StructureEngine();
        try(var prepared=postEngine.prepare(asset,anchor,0,post.snapshot(),late::get,n->{})){prepared.commit("synthetic",post);placements++;}
        require(late.get()&&post.generation()==1&&!postEngine.busy(),"Post-publication outcome lost live root");physical(post.snapshot(),open(postPath),anchor);
        postEngine.undo("synthetic",post);undos++;
        reject(IOException.class,null,()->stale.edit(new Protocol.Edit(0,Protocol.MAX_BUILD_Y+1,0,Blocks.STONE)));
        reject(IOException.class,"Unknown",()->open(work.resolve("invalid-model")).edit(new Protocol.Edit(0,128,0,1024)));
        try(var unlocked=stale.stageCommit(stale.models(),Map.of(),stale.models().revision(),()->false)){reject(IOException.class,"lock",unlocked::apply);}
        var tree=solid(Blocks.STONE);var cell=new Protocol.CellState(0,128,0,tree);var budget=new RegionHistoryPrototype.CodecBudget(1024,1,1,1);
        reject(IOException.class,"Cell budget",()->RegionHistoryPrototype.fromCells(123,3,List.of(cell,cell),budget,()->false));
        reject(IOException.class,"Duplicate",()->RegionHistoryPrototype.fromCells(123,3,List.of(cell,cell),new RegionHistoryPrototype.CodecBudget(1024,2,1,2),()->false));
        require(RegionHistoryPrototype.fromCells(123,3,List.of(cell),budget,()->false).replay().cell(0,128,0).sameValues(tree),"Final-cell import changed geometry");
        reject(IOException.class,"Invalid final",()->RegionHistoryPrototype.fromCells(123,3,List.of(new Protocol.CellState(Terrain.LIMIT+1,128,0,tree)),budget,()->false));
        reject(IOException.class,"Invalid final",()->RegionHistoryPrototype.fromCells(123,3,List.of(new Protocol.CellState(0,128,0,new SparseVoxelOctree(8))),budget,()->false));
        reject(IOException.class,"Leaf budget",()->RegionHistoryPrototype.fromCells(123,3,List.of(cell),new RegionHistoryPrototype.CodecBudget(1024,1,1,0),()->false));
        reject(IOException.class,"Terrain version",()->RegionHistoryPrototype.fromCells(123,0,List.of(cell),budget,()->false));
        reject(CancellationException.class,null,()->RegionHistoryPrototype.fromCells(123,3,List.of(cell),budget,()->true));
        System.out.printf("{\"status\":\"passed\",\"rotations\":4,\"successful_placements\":%d,\"successful_undos\":%d,\"rejection_cases\":%d,\"production_integration\":false,\"qualification\":false}%n",placements,undos,rejected);
    }
}
