package dev.jayms.net;

import dev.jayms.ChunkPos;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/** Exercises full saved-world readback through the production writer/opener. */
public final class RegionalReadbackSourceCheck {
    static final RegionReplayWorker.Revision REV=new RegionReplayWorker.Revision(1,2,3);
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void await(BooleanSupplier done)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(!done.getAsBoolean()&&System.nanoTime()<deadline)LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        require(done.getAsBoolean(),"Worker deadline");
    }
    static void close(RegionReplayWorker worker)throws Exception {worker.close();await(()->worker.status().state()==RegionReplayWorker.State.CLOSED||worker.status().state()==RegionReplayWorker.State.FAILED);}
    static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static void save(Path path,ModelLibrary models,WorldVoxels.History rows,int health)throws Exception {
        var inventory=new Inventory();inventory.add(Blocks.STONE,3);
        try(var staged=StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,models,rows,()->false,n->{})) {
            staged.publish(inventory,health,List.of(new ItemDrop(3,Blocks.STONE,2,1,2,3)),1);
        }
    }
    static RegionReplayWorker worker(Path world,Path spool,int rows) {
        return new RegionReplayWorker(spool,(cancel,progress)->LocalGame.openRegionalReadback(world,spool,REV,cancel,progress),2,1,rows*28L,rows);
    }
    static RegionReplayPage page(RegionReplayWorker worker,int rows)throws Exception {
        var request=worker.submit(new ChunkPos(0,10,0),new RegionReplayPage.CellRange(0,64),rows,REV);
        await(()->request.outcome().phase()==RegionReplayWorker.Phase.READY||request.outcome().phase()==RegionReplayWorker.Phase.FAILED);
        return request.result(REV);
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Fresh directory, result JSON, evidence PNG");
        var folder=Path.of(args[0]);var result=Path.of(args[1]);var png=Path.of(args[2]);
        require(!Files.exists(folder)&&!Files.exists(result)&&!Files.exists(png),"Fresh outputs");Files.createDirectory(folder);
        var spool=Files.createDirectory(folder.resolve("spool"));var world=folder.resolve("world.dat");var models=new ModelLibrary();
        var voxels=new SparseVoxelOctree(32);voxels.set(31,30,29,0xff123456);int model=models.stageStructureModel(new ModelDefinition("Source component",voxels)).id();
        var rows=new WorldVoxels.History();WorldVoxels.remember(rows,new Protocol.Edit(0,160,0,0));
        WorldVoxels.remember(rows,new Protocol.Edit(0,160,0,Blocks.piece(Blocks.LED,4),4,15,14,13,0x123456));WorldVoxels.remember(rows,new Protocol.Edit(1,160,0,model));
        save(world,models,rows,11);byte[] original=Files.readAllBytes(world);String originalHash=hash(original);var expected=new WorldVoxels(new Terrain(42));rows.values().forEach(expected::apply);
        RegionReplayPage retained;var worker=worker(world,spool,rows.size());
        try {
            await(()->worker.status().state()==RegionReplayWorker.State.RUNNING);require(originalHash.equals(worker.fingerprint()),"Full source identity");
            retained=page(worker,rows.size());require(new ArrayList<>(rows.values()).equals(new ArrayList<>(retained.history().values())),"Ordered replay oracle");
            for(var entry:retained.cells().entrySet()){var p=entry.getKey();require(entry.getValue().tree().sameValues(expected.cell(p.x(),p.y(),p.z())),"Voxel geometry oracle");}
            require(Arrays.equals(original,Files.readAllBytes(world)),"Opening/readback leaves source unchanged");
            save(world,models,rows,12);require(!originalHash.equals(hash(Files.readAllBytes(world))),"Changed source path identity");
            require(page(worker,rows.size()).history().equals(retained.history()),"Retained snapshot after path replacement");require(originalHash.equals(worker.fingerprint()),"Sealed source identity does not follow path");
        }finally{close(worker);}
        require(retained.cells().size()==2,"Detached lifetime");try(var files=Files.list(spool)){require(files.findAny().isEmpty(),"Owned worker cleanup");}
        String replacementHash=hash(Files.readAllBytes(world));var reopened=worker(world,spool,rows.size());
        try{await(()->reopened.status().state()==RegionReplayWorker.State.RUNNING);require(replacementHash.equals(reopened.fingerprint())&&!originalHash.equals(reopened.fingerprint()),"Reopen validates new source identity");page(reopened,rows.size());}
        finally{close(reopened);}
        byte[] before=Files.readAllBytes(world);var resident=new LocalGame(world,0);require(resident.seed==42&&resident.health==12&&resident.inventory.countType(Blocks.STONE)==3&&resident.drops.size()==1,"Resident metadata regression");
        require(resident.models.get(model).definition().voxels().size()==32,"Model dependency restored");require(new ArrayList<>(resident.edits.values()).equals(new ArrayList<>(rows.values())),"Resident replay regression");require(Arrays.equals(before,Files.readAllBytes(world)),"Resident read leaves source unchanged");
        var bad=folder.resolve("invalid.dat");Files.write(bad,Arrays.copyOf(before,before.length+1));byte[] invalidBytes=Files.readAllBytes(bad);
        var entered=new CountDownLatch(1);var proceed=new CountDownLatch(1);
        var invalid=new RegionReplayWorker(spool,(cancel,progress)->{entered.countDown();try{if(!proceed.await(15,TimeUnit.SECONDS))throw new IOException("Synthetic gate deadline");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException(error);}return LocalGame.openRegionalReadback(bad,spool,REV,cancel,progress);},1,1,rows.size()*28L,rows.size());
        try {
            require(entered.await(15,TimeUnit.SECONDS),"Invalid source gate");var request=invalid.submit(new ChunkPos(0,10,0),new RegionReplayPage.CellRange(0,64),rows.size(),REV);proceed.countDown();
            await(()->invalid.status().state()==RegionReplayWorker.State.FAILED);require(request.outcome().phase()==RegionReplayWorker.Phase.FAILED&&invalid.fingerprint().isEmpty(),"No page or identity from invalid full source");
            require(Arrays.equals(invalidBytes,Files.readAllBytes(bad)),"Rejected source unchanged");
        }finally{proceed.countDown();close(invalid);}
        try(var files=Files.list(spool)){require(files.findAny().isEmpty(),"Invalid-source cleanup");}
        Files.delete(bad);Files.delete(world);Files.delete(spool);Files.delete(folder);
        String json="{\"status\":\"passed\",\"qualification\":false,\"source_rows\":"+rows.size()+",\"edited_cells\":2,\"model_resolution\":32,\"original_source_sha256\":\""+originalHash+"\",\"replacement_source_sha256\":\""+replacementHash+"\",\"unchanged_source_reads\":true,\"invalid_full_source_rejected\":true}";
        Files.writeString(result,json+"\n");System.out.println(json);
        String text="Voxel One: saved-world regional readback CLI\n\nActual production writer/opener/worker workflow passed.\n\nFull v10 prefix, model references, replay, drops and EOF validated.\n32-resolution component and coloured fractional geometry retained.\nOrdered history and voxel geometry matched independent replay.\nReadback and resident loading left source bytes unchanged.\nFull-source SHA-256 matched the actual saved bytes.\nPath replacement left the existing private snapshot unchanged.\nReopen produced the new validated source identity.\nMalformed full-world tail returned no page or fingerprint.\nWorker/cache/spool cleanup and detached result lifetime passed.\n\nPrivate readback only; live World/server/rendering integration is pending.\nSource hash is not a live authority or path-replacement fence.\nNo timing, peak-memory, GPU, network or city qualification claim.\nPublic limits and stable rollout remain gated.";
        javax.swing.SwingUtilities.invokeAndWait(()->{
            var frame=new javax.swing.JFrame("Voxel One saved-world readback CLI");var area=new javax.swing.JTextArea(text);area.setEditable(false);
            area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,0,15));area.setMargin(new java.awt.Insets(20,20,20,20));frame.add(area);frame.setSize(1100,520);frame.setLocation(30,30);frame.setVisible(true);
            var timer=new javax.swing.Timer(1200,e->{try{javax.imageio.ImageIO.write(new java.awt.Robot().createScreenCapture(frame.getBounds()),"png",png.toFile());}catch(Exception error){throw new RuntimeException(error);}finally{frame.dispose();}});timer.setRepeats(false);timer.start();
        });
    }
}
