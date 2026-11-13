package dev.jayms;

import java.util.concurrent.*;
/** Deterministic queue/readiness integration diagnostic, with no rendering. */
public final class HighColumnReadinessProbe {
    public static void main(String[] args)throws Exception {
        var release=new CountDownLatch(1);
        try(var world=new World(42)) {
            var column=new ChunkPos(0,0,0);world.addChunk(new ChunkPos(0,23,0),new Chunk());
            if(!world.renderedColumns().contains(column))throw new AssertionError("Initial manually resident column");
            var scheduler=new TerrainScheduler(42,world.terrain().version,(position,terrain)->{
                try{if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("Worker release timeout");}
                catch(InterruptedException failure){Thread.currentThread().interrupt();throw new AssertionError(failure);}
                return new Chunk();
            });
            var field=World.class.getDeclaredField("terrainScheduler");field.setAccessible(true);field.set(world,scheduler);
            try {
                world.streamAsync(8,383,8,1_000_000_000L);
                int base=0;for(int y=-2;y<=7;y++)if(world.getLoadedChunks().containsKey(new ChunkPos(0,y,0)))base++;
                boolean ready=world.skyColumnReady(column),complete=world.renderedColumns().contains(column);
                if(world.terrainPending()!=16||base!=0)throw new AssertionError("High-only queue setup");
                boolean expected=args[0].equals("parent");
                if(ready!=expected||complete!=expected)throw new AssertionError("Unexpected readiness");
                System.out.println("{\"implementation\":\""+args[0]+"\",\"pending\":16,\"resident_base_chunks\":0,\"sky_ready\":"+ready+",\"rendered_column_complete\":"+complete+"}");
            }finally{release.countDown();}
            if(args[0].equals("fixed")) {
                long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                while(!world.skyColumnReady(column)&&System.nanoTime()<end){world.streamAsync(8,383,8,2_000_000);Thread.sleep(1);}
                for(int y=-2;y<=7;y++)if(!world.getLoadedChunks().containsKey(new ChunkPos(0,y,0)))throw new AssertionError("Missing base after release");
                System.out.println("{\"implementation\":\"fixed\",\"after_worker_release\":\"all ten base chunks resident; sky ready\"}");
            }
        }finally{release.countDown();}
    }
}
