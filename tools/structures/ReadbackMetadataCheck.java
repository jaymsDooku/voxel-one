package dev.jayms.net;

import dev.jayms.ChunkPos;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.*;

/** Actual owner metadata transfer and incomplete-state guards; not live paging. */
public final class ReadbackMetadataCheck {
    static final RegionReplayWorker.Revision REV=new RegionReplayWorker.Revision(1,2,3);
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void await(BooleanSupplier done)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(!done.getAsBoolean()&&System.nanoTime()<deadline)LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));require(done.getAsBoolean(),"Worker deadline");
    }
    interface Checked {void run()throws Exception;}
    static void reject(Class<? extends Exception> type,Checked operation)throws Exception {
        try{operation.run();throw new AssertionError("Missing guard: "+type.getSimpleName());}catch(Exception expected){if(!type.isInstance(expected))throw expected;}
    }
    static void close(RegionReplayWorker worker)throws Exception {worker.close();await(()->worker.status().state()==RegionReplayWorker.State.CLOSED||worker.status().state()==RegionReplayWorker.State.FAILED);}
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Fresh directory, result JSON, evidence PNG");
        var folder=Path.of(args[0]);var result=Path.of(args[1]);var png=Path.of(args[2]);require(!Files.exists(folder)&&!Files.exists(result)&&!Files.exists(png),"Fresh outputs");Files.createDirectory(folder);
        var spool=Files.createDirectory(folder.resolve("spool"));var world=folder.resolve("world.dat");var models=new ModelLibrary();
        var tree=new SparseVoxelOctree(32);tree.set(31,30,29,0xff123456);int model=models.stageStructureModel(new ModelDefinition("Component",tree)).id();
        var rows=new WorldVoxels.History();WorldVoxels.remember(rows,new Protocol.Edit(0,160,0,Blocks.STONE));WorldVoxels.remember(rows,new Protocol.Edit(1,160,0,model));
        var inventory=new Inventory();inventory.add(Blocks.STONE,3);
        try(var staged=StagedOfflineSave.stageRegions(world,42,Terrain.CURRENT_VERSION,models,rows,()->false,n->{})){staged.publish(inventory,17,List.of(new ItemDrop(3,Blocks.STONE,2,1,2,3)),1);}
        byte[] original=Files.readAllBytes(world);var policy=new AtomicReference<Predicate<Protocol.Edit>>();
        var worker=new RegionReplayWorker(spool,(cancel,progress)->{var source=LocalGame.openRegionalReadback(world,spool,REV,cancel,progress);policy.set(source.allowed());return source;},2,1,56,2);
        LocalGame partial;
        try {
            await(()->worker.status().state()==RegionReplayWorker.State.RUNNING);
            reject(IOException.class,()->worker.metadataResult(new RegionReplayWorker.Revision(9,2,3)));
            partial=worker.metadataResult(REV);require(!partial.replayComplete()&&partial.edits.isEmpty()&&partial.city==null,"Explicit metadata-only state");
            require(partial.seed==42&&partial.generatorVersion==Terrain.CURRENT_VERSION&&partial.health==17&&partial.inventory.countType(Blocks.STONE)==3&&partial.drops.size()==1,"Metadata values");
            require(partial.models.get(model).definition().voxels().size()==32&&partial.readbackFingerprint().equals(worker.fingerprint()),"Model and source identity");
            reject(IOException.class,()->worker.metadataResult(REV));
            reject(IOException.class,partial::save);reject(IOException.class,()->partial.startGame(null,null));reject(IOException.class,()->partial.createModel(null));
            reject(IllegalStateException.class,()->partial.edit(null,0,0));reject(IllegalStateException.class,()->partial.craft(0,null));reject(IllegalStateException.class,()->partial.drop(0,null));
            reject(IllegalStateException.class,()->partial.tick(null,false,1,e->{throw new AssertionError("Partial tick callback");}));
            reject(IOException.class,()->partial.stageStructureHistory(null,null,()->{throw new AssertionError("Partial callback");},n->{throw new AssertionError("Partial progress");}));
            reject(IOException.class,()->partial.stageStructureSave(null,null,()->false,n->{}));reject(IllegalStateException.class,()->partial.structureAuthority(null));
            require(Arrays.equals(original,Files.readAllBytes(world))&&!Files.exists(folder.resolve("world.dat.tmp"))&&!Files.exists(folder.resolve("world.dat.city")),"Guards leave source/sidecars unchanged");
            var independent=new SparseVoxelOctree(8);independent.set(0,0,0,0xffabcdef);partial.models.accept(new ModelLibrary.Entry(10,"owner",new ModelDefinition("Independent",independent)));
            partial.inventory.add(Blocks.STONE,1);partial.drops.clear();partial.health=2;require(!policy.get().test(new Protocol.Edit(0,160,0,10)),"Private worker model policy unchanged");
            var request=worker.submit(new ChunkPos(0,10,0),new RegionReplayPage.CellRange(0,64),2,REV);await(()->request.outcome().phase()==RegionReplayWorker.Phase.READY||request.outcome().phase()==RegionReplayWorker.Phase.FAILED);
            require(new ArrayList<>(request.result(REV).history().values()).equals(new ArrayList<>(rows.values())),"Worker reads survive owner mutation");
            partial.edits=rows;reject(IOException.class,partial::save);require(!partial.replayComplete(),"History assignment cannot promote partial state");
        }finally{close(worker);}
        reject(IOException.class,partial::save);require(Arrays.equals(original,Files.readAllBytes(world)),"Closed-source metadata cannot save");
        try(var load=new OfflineGameLoad(world,42,(path,seed,cancel,progress)->partial)){await(load::done);reject(IOException.class,load::result);}
        var reopened=new RegionReplayWorker(spool,(cancel,progress)->LocalGame.openRegionalReadback(world,spool,REV,cancel,progress),1,1,56,2);
        try{await(()->reopened.status().state()==RegionReplayWorker.State.RUNNING);var clean=reopened.metadataResult(REV);require(!clean.models.has(10)&&clean.inventory.countType(Blocks.STONE)==3&&clean.health==17&&clean.drops.size()==1,"Reopened independent metadata");}
        finally{close(reopened);}
        var complete=new LocalGame(world,0);require(complete.replayComplete()&&complete.edits.size()==2,"Full-load regression");complete.save();require(new LocalGame(world,0).edits.size()==2,"Full save remains enabled");
        try(var files=Files.list(spool)){require(files.findAny().isEmpty(),"Owned worker cleanup");}Files.delete(world);Files.delete(spool);Files.delete(folder);
        String json="{\"status\":\"passed\",\"qualification\":false,\"single_owner_transfer\":true,\"model_resolution\":32,\"independent_owner_state\":true,\"partial_save_and_operation_guards\":true,\"history_cannot_promote_partial_state\":true,\"full_startup_rejects_partial_state\":true,\"complete_load_and_save_regression\":true}";
        Files.writeString(result,json+"\n");System.out.println(json);
        String text="Voxel One: owner metadata transfer CLI\n\nActual saved-world opener/worker/owner workflow passed.\n\nOne transfer; revision mismatch and duplicate extraction rejected.\nSeed, generator, health, inventory, drops and 32-resolution model retained.\nOwner metadata is explicitly incomplete, with no resident replay or city.\nSave, edit, craft, drop, tick, model, structure and city-start paths reject.\nSource bytes and sidecars stay unchanged after rejected operations.\nOwner map/inventory/drop changes do not alter worker policy or reopen state.\nAssigning a full history cannot promote metadata-only state.\nFull startup loader rejects partial state; normal load/save still work.\nWorker cleanup and owner result lifetime passed.\n\nNo live paging installation, promotion path or overlay persistence added.\nActual live identity/revision fences and unloaded-cell rules remain pending.\nNo timing, peak-memory, GPU, network or city qualification claim.\nPublic limits and stable rollout remain gated.";
        javax.swing.SwingUtilities.invokeAndWait(()->{var frame=new javax.swing.JFrame("Voxel One metadata transfer CLI");var area=new javax.swing.JTextArea(text);area.setEditable(false);area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,0,15));area.setMargin(new java.awt.Insets(20,20,20,20));frame.add(area);frame.setSize(1130,550);frame.setLocation(30,30);frame.setVisible(true);
            var timer=new javax.swing.Timer(1200,e->{try{javax.imageio.ImageIO.write(new java.awt.Robot().createScreenCapture(frame.getBounds()),"png",png.toFile());}catch(Exception error){throw new RuntimeException(error);}finally{frame.dispose();}});timer.setRepeats(false);timer.start();});
    }
}
