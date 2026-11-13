package dev.jayms.net;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.*;

/** Synthetic history-only durable save comparison; no renderer or city qualification. */
public final class OfflineSaveProbe {
    public static void main(String[] args)throws Exception {
        if(args.length!=1||!java.util.Set.of("sync","staged").contains(args[0]))throw new IllegalArgumentException("sync|staged");
        Path directory=Path.of("target/structure-offline-save-probe");Files.createDirectories(directory);
        Path file=directory.resolve("world.dat");if(Files.exists(file))throw new IllegalStateException("Probe target must be absent");
        var local=new LocalGame(file,42);
        for(int i=0;i<100000;i++){var edit=new Protocol.Edit(i%512,180,500+i/512,Blocks.BRICKS);local.edits.put(edit.key(),edit);}
        local.inventory.add(Blocks.STONE,4);local.health=17;local.drops.put(1,new ItemDrop(1,Blocks.STONE,2,1,180,1));
        var pool=Executors.newSingleThreadExecutor(r->new Thread(r,"synthetic-save-worker"));
        try {
            for(int run=-3;run<20;run++) {
                long start=System.nanoTime(),ownerNanos,stageNanos=0;
                if(args[0].equals("sync")){local.save();ownerNanos=System.nanoTime()-start;}
                else {
                    long staging=System.nanoTime();var staged=pool.submit(()->StagedOfflineSave.stage(file,local.seed,local.generatorVersion,local.models,(WorldVoxels.History)local.edits,()->false,p->{})).get();
                    stageNanos=System.nanoTime()-staging;long publish=System.nanoTime();
                    try(staged){staged.publish(local.inventory,local.health,local.drops.values(),local.drops.size());}
                    ownerNanos=System.nanoTime()-publish;
                }
                long wall=System.nanoTime()-start;
                byte[] bytes=Files.readAllBytes(file);String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                if(run>=0)System.out.printf(java.util.Locale.ROOT,"{\"run\":%d,\"mode\":\"%s\",\"wall_ms\":%.6f,\"owner_ms\":%.6f,\"stage_ms\":%.6f,\"bytes\":%d,\"sha256\":\"%s\"}%n",run,args[0],wall/1e6,ownerNanos/1e6,stageNanos/1e6,bytes.length,hash);
                Files.delete(file);
            }
        }finally{pool.shutdownNow();Files.deleteIfExists(file);}
    }
}
