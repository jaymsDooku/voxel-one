import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.StructurePackage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Synthetic owner fences plus real asynchronous disk reads; no game profile. */
public final class RegionReadViewCheck {
    static final StructurePackage.Position P=new StructurePackage.Position(0,0,0);
    static final List<StructurePackage.Position> POSITIONS=List.of(P);
    static int rejects;
    static void require(boolean ok){if(!ok)throw new AssertionError();}
    static void failed(RegionReadViewPrototype.State state){require(state instanceof RegionReadViewPrototype.Failed);rejects++;}
    static RegionReadViewPrototype view(WorldVoxels w,Object epoch,ModelLibrary models,CompletableFuture<List<Optional<StructurePackage.Cell>>> f){return new RegionReadViewPrototype(w,epoch,models,POSITIONS,-17,180,-17,f);}
    static void edges()throws Exception {
        for(int change=0;change<8;change++) {
            var world=new WorldVoxels(new Terrain(42));var models=new ModelLibrary();var epoch=new Object();
            if(change==5)world.apply(new Protocol.Edit(-18,180,-17,Blocks.STONE));
            var future=new CompletableFuture<List<Optional<StructurePackage.Cell>>>();var view=view(world,epoch,models,future);
            require(view.poll(world,epoch,models) instanceof RegionReadViewPrototype.Pending);
            future.complete(List.of(Optional.empty()));require(view.poll(world,epoch,models) instanceof RegionReadViewPrototype.Ready);
            WorldVoxels current=world;Object currentEpoch=epoch;ModelLibrary currentModels=models;
            switch(change) {
                case 0->world.apply(new Protocol.Edit(-18,180,-17,Blocks.STONE));
                case 1->current=world.snapshot();
                case 2->currentEpoch=new Object(); // root ABA: same object, new publication epoch
                case 3->currentModels=models.copy();
                case 4->{var t=new SparseVoxelOctree(8);t.set(1,2,3,0xff123456);models.stageStructureModel(new ModelDefinition("Read fence",t));}
                case 5->world.clear();
                case 6->world.apply(new Protocol.Edit(1000,180,1000,Blocks.GLASS));
                case 7->{var executor=Executors.newSingleThreadExecutor();try{require(executor.submit(()->{try{view.poll(world,epoch,models);return false;}catch(IllegalStateException expected){return true;}}).get(10,TimeUnit.SECONDS));rejects++;}finally{executor.shutdownNow();}}
            }
            if(change<6){failed(view.poll(current,currentEpoch,currentModels));require(view.poll(world,epoch,models) instanceof RegionReadViewPrototype.Failed);}
            else require(view.poll(current,currentEpoch,currentModels) instanceof RegionReadViewPrototype.Ready);
        }
        for(int variant=0;variant<4;variant++) {
            var world=new WorldVoxels(new Terrain(42));var models=new ModelLibrary();var epoch=new Object();
            var future=new CompletableFuture<List<Optional<StructurePackage.Cell>>>();var view=view(world,epoch,models,future);
            switch(variant){case 0->future.cancel(false);case 1->future.completeExceptionally(new IOException("Synthetic read failure"));case 2->future.complete(List.of());case 3->future.complete(List.of(Optional.of(new StructurePackage.Cell(new StructurePackage.Position(1,0,0),null,0))));}
            failed(view.poll(world,epoch,models));require(world.type(-17,180,-17)==0);
        }
    }
    public static void main(String[] args)throws Exception {
        edges();var root=Files.createTempDirectory(Path.of("target"),"read-view-check-");
        try {
            var expected=DiskRegionCacheCheck.fixture();var source=root.resolve("view.vstr");expected.save(source);
            var reader=new AsyncRegionReadPrototype(cancel->{
                var index=DiskPackageIndexPrototype.open(source,root,cancel);var cache=new DiskRegionCachePrototype(index,2,1024*1024);
                return new AsyncRegionReadPrototype.Reader(){
                    public Optional<StructurePackage.Cell> cell(StructurePackage.Position p,java.util.function.BooleanSupplier stop)throws IOException{return cache.cell(p.x(),p.y(),p.z(),stop);}
                    public void close()throws IOException{cache.close();index.close();}
                };
            },2);
            try {
                var world=new WorldVoxels(new Terrain(42));var epoch=new Object();var models=new ModelLibrary();
                var future=reader.request(POSITIONS);var view=view(world,epoch,models,future);future.get(10,TimeUnit.SECONDS);
                var ready=(RegionReadViewPrototype.Ready)view.poll(world,epoch,models);require(ready.cells().get(0).orElseThrow().equals(expected.cells.get(0)));
                world.apply(new Protocol.Edit(1000,180,1000,Blocks.GLASS));require(view.poll(world,epoch,models) instanceof RegionReadViewPrototype.Ready);
                world.apply(new Protocol.Edit(-18,180,-17,Blocks.STONE));failed(view.poll(world,epoch,models));
            }finally{reader.closeAsync().get(10,TimeUnit.SECONDS);}
            try(var files=Files.list(root)){require(files.map(p->p.getFileName().toString()).toList().equals(List.of("view.vstr")));}
        }finally{try(var files=Files.list(root)){for(var p:files.toList())Files.delete(p);}Files.delete(root);}
        String result="{\"status\":\"passed\",\"rejection_cases\":"+rejects+",\"completed_results_revalidated\":true,\"unrelated_edits_preserved\":true,\"qualification\":false}";System.out.println(result);
        if(args.length==1&&args[0].equals("--evidence"))javax.swing.SwingUtilities.invokeAndWait(()->{
            var f=new javax.swing.JFrame("Voxel One region read fence CLI evidence");var text=new javax.swing.JTextArea("Voxel One: owner-side asynchronous region read fences\n\nActual CLI assertions passed before this window.\n"+result+"\n\nPending, ready and failed states stay distinct.\nCompleted disk reads recheck world identity and root publication epoch.\nAffected chunks, model changes and wrong-owner access reject.\nUnrelated edits remain valid; failed/stale views never revive.\nCancelled, failed, wrong-count and wrong-position results reject.\nReal disk index/cache cleanup completed on its storage thread.\n\nPrivate integration only. Ready data is not placement authorization.\nGame wiring, city/max and GPU/32-client qualification remain pending.");text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,0,15));text.setMargin(new java.awt.Insets(20,20,20,20));f.add(text);f.setSize(1000,540);f.setLocation(60,60);f.setVisible(true);var timer=new javax.swing.Timer(15000,e->f.dispose());timer.setRepeats(false);timer.start();
        });
    }
}
