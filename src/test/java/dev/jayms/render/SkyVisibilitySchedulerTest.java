package dev.jayms.render;

import dev.jayms.*;
import dev.jayms.net.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SkyVisibilitySchedulerTest {
    static SkyVisibilityScheduler.Prepared await(SkyVisibilityScheduler s,World w)throws Exception {return await(s,w,p->true);}
    static SkyVisibilityScheduler.Prepared await(SkyVisibilityScheduler s,World w,java.util.function.Predicate<SkyVisibilityScheduler.Prepared> expected)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<end){var p=s.update(w,1_000_000);assertTrue(s.pending()<=4);if(p!=null&&p.valid(w)&&expected.test(p))return p;Thread.sleep(1);}
        fail("No current sky atlas prepared");return null;
    }
    @Test void cachedTilesMatchFullSkyIncludingUnloadedFractionalRoofs()throws Exception {
        try(var world=new World(42);var sky=new SkyVisibilityScheduler()) {
            var left=new Chunk();left.setBlock(15,0,0,Blocks.STONE);world.addChunk(new ChunkPos(-1,4,0),left);
            var right=new Chunk();right.setBlock(0,0,0,Blocks.GLASS);world.addChunk(new ChunkPos(0,4,0),right);
            world.apply(new Protocol.Edit(-1,140,0,Blocks.STONE,1,0,0,0));
            var expected=WorldSkyVisibility.build(world);SkyVisibilityScheduler.Prepared result=null;
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(System.nanoTime()<end){var p=sky.update(world,1_000_000);if(p!=null)result=p;if(result!=null&&result.valid(world)&&java.util.Arrays.equals(result.data.data,expected.data))break;Thread.sleep(1);}
            assertNotNull(result);assertArrayEquals(expected.data,result.data.data);
            assertEquals(expected.sample(-.75f,100,.25f),result.data.sample(-.75f,100,.25f));
            world.apply(new Protocol.Edit(-1,140,0,0));assertFalse(result.valid(world));
            SkyVisibilityScheduler.Prepared latest=null;var exact=WorldSkyVisibility.build(world);
            end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(System.nanoTime()<end){var p=sky.update(world,1_000_000);if(p!=null)latest=p;if(latest!=null&&latest.valid(world)&&java.util.Arrays.equals(latest.data.data,exact.data))break;Thread.sleep(1);}
            assertNotNull(latest);assertArrayEquals(exact.data,latest.data.data);assertEquals(1,latest.data.sample(-.75f,100,.25f));
        }
    }
    @Test void tinySlicesPublishCompleteLocalSkyWithoutPackingStarvation()throws Exception {
        try(var world=new World(42);var sky=new SkyVisibilityScheduler()){
            for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){
                var c=new Chunk();c.setBlock(0,0,0,Blocks.STONE);world.addChunk(new ChunkPos(x,4,z),c);
            }
            var exact=WorldSkyVisibility.build(world);SkyVisibilityScheduler.Prepared latest=null;
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);int pumps=0;
            while(System.nanoTime()<end){
                var next=sky.update(world,0,0,250_000);if(next!=null)latest=next;pumps++;
                if(latest!=null&&latest.localReady(world,0,0)&&java.util.Arrays.equals(exact.data,latest.data.data))break;
                Thread.sleep(1);
            }
            assertNotNull(latest);assertTrue(latest.localReady(world,0,0));assertArrayEquals(exact.data,latest.data.data);
            assertTrue(pumps<1000,"Every bounded stage must get service");
            sky.update(world,0,0,5_000_000);
            assertTrue(sky.nearChecks<=36,"A settled near area is searched once, not once per resident column");
            assertTrue(sky.scanChecks<=81);sky.close();assertEquals(0,sky.pending());assertEquals(0,sky.cachedBytes());
        }
    }
    @Test void editDuringQueuedMergeCannotPublishAnOldRoof()throws Exception {
        var release=new CountDownLatch(1);
        try(var world=new World(42);var sky=new SkyVisibilityScheduler()){
            var c=new Chunk();c.setBlock(0,0,0,Blocks.STONE);world.addChunk(new ChunkPos(0,4,0),c);
            await(sky,world);
            // Complete the next tile job without collecting it, then hold the CPU lane
            // so the ensuing merged snapshot cannot finish until after the roof edit.
            world.addChunk(new ChunkPos(1,4,0),new Chunk());
            var pending=SkyVisibilityScheduler.class.getDeclaredField("pending");pending.setAccessible(true);
            var packed=SkyVisibilityScheduler.class.getDeclaredField("packed");packed.setAccessible(true);
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            Future<?> tile=null;
            while(System.nanoTime()<end){sky.update(world,1_000_000);tile=(Future<?>)pending.get(sky);if(tile!=null)break;Thread.sleep(1);}
            assertNotNull(tile);tile.get(10,TimeUnit.SECONDS);
            var entered=new CountDownLatch(1);world.prepareTerrainTask(()->{entered.countDown();release.await();return null;});assertTrue(entered.await(10,TimeUnit.SECONDS));
            while(packed.get(sky)==null&&System.nanoTime()<end)sky.update(world,1_000_000);
            assertNotNull(packed.get(sky));assertFalse(((Future<?>)packed.get(sky)).isDone());
            world.apply(new Protocol.Edit(0,64,0,0));release.countDown();
            var expected=WorldSkyVisibility.build(world);SkyVisibilityScheduler.Prepared latest=null;
            while(System.nanoTime()<end){var next=sky.update(world,1_000_000);if(next!=null){assertTrue(next.valid(world));latest=next;}if(latest!=null&&java.util.Arrays.equals(expected.data,latest.data.data))break;Thread.sleep(1);}
            assertNotNull(latest);assertArrayEquals(expected.data,latest.data.data);assertEquals(1,latest.data.sample(.5f,32,.5f));assertTrue(sky.rejected>0);
        }finally{release.countDown();}
    }
    @Test void giCeilingGateIgnoresNaturalTilesButFencesCurrentHighEdits()throws Exception {
        try(var world=new World(42);var sky=new SkyVisibilityScheduler()){
            world.addChunk(new ChunkPos(0,4,0),ChunkGenerator.generate(new ChunkPos(0,4,0),world.terrain()));
            assertTrue(SkyVisibilityScheduler.ceilingReady(null,world,0,0));assertEquals(java.util.List.of(),SkyVisibilityScheduler.ceilingKey(null,world,0,0));
            world.apply(new Protocol.Edit(0,140,0,Blocks.STONE));assertFalse(SkyVisibilityScheduler.ceilingReady(null,world,0,0));
            var roof=await(sky,world);assertTrue(SkyVisibilityScheduler.ceilingReady(roof,world,0,0));var key=SkyVisibilityScheduler.ceilingKey(roof,world,0,0);
            assertEquals(0,roof.data.sampleKnown(.5f,96,.5f,1));
            world.apply(new Protocol.Edit(0,140,0,Blocks.AIR));assertFalse(SkyVisibilityScheduler.ceilingReady(roof,world,0,0));
            var cleared=await(sky,world,p->SkyVisibilityScheduler.ceilingReady(p,world,0,0));assertTrue(SkyVisibilityScheduler.ceilingReady(cleared,world,0,0));assertNotEquals(key,SkyVisibilityScheduler.ceilingKey(cleared,world,0,0));assertEquals(1,cleared.data.sampleKnown(.5f,96,.5f,1));
        }
    }
    @Test void worldSwitchWithEqualModelRevisionUsesNewDefinitions()throws Exception {
        try(var first=new World(42);var second=new World(99);var sky=new SkyVisibilityScheduler()){
            var solid=new dev.jayms.net.model.SparseVoxelOctree(16);solid.fill(0,0,0,16,16,16,0xff40b0e0);
            var tiny=new dev.jayms.net.model.SparseVoxelOctree(16);tiny.set(0,0,0,0xff40b0e0);
            int a=first.models().register(new dev.jayms.net.model.ModelDefinition("Solid",solid),"synthetic").id();
            int b=second.models().register(new dev.jayms.net.model.ModelDefinition("Tiny",tiny),"synthetic").id();assertEquals(a,b);assertEquals(first.models().revision(),second.models().revision());
            var left=new Chunk();left.setBlock(0,0,0,a);first.addChunk(new ChunkPos(0,4,0),left);
            var right=new Chunk();right.setBlock(0,0,0,b);second.addChunk(new ChunkPos(0,4,0),right);
            assertEquals(0,await(sky,first,p->p.data.sampleKnown(.75f,32,.75f,-1)==0).data.sample(.75f,32,.75f));
            assertEquals(1,await(sky,second,p->p.data.sampleKnown(.75f,32,.75f,-1)==1).data.sample(.75f,32,.75f),"Same revision does not make another world's model snapshot current");
        }
    }
    @Test void fullGeneratedDisplaySurvivesResidencyButNotEditsOrClose()throws Exception {
        try(var world=new World(42);var sky=new SkyVisibilityScheduler()){
            world.stream(0,0,1);var ready=await(sky,world);assertTrue(world.skyTerrainEquivalent(new ChunkPos(0,0,0)));
            for(int y=-2;y<=7;y++)world.unloadChunk(new ChunkPos(0,y,0));
            assertTrue(ready.valid(world),"Complete implicit terrain remains logically current after unload");
            world.apply(new Protocol.Edit(0,140,0,Blocks.STONE));assertFalse(ready.valid(world),"Authoritative edits still fence display snapshots");
            world.close();assertFalse(ready.valid(world));
        }
    }
    @Test void partialEditedDisplayDoesNotClaimNewlyLoadedBaseGeometry()throws Exception {
        try(var world=new World(42);var sky=new SkyVisibilityScheduler()){
            world.apply(new Protocol.Edit(0,140,0,Blocks.STONE));var partial=await(sky,world,p->SkyVisibilityScheduler.ceilingReady(p,world,0,0));
            world.stream(0,0,1);assertFalse(partial.valid(world),"An edit-only snapshot must refresh after base terrain loads");
        }
    }
    @Test void staleCaptureAndPreviousWorldCannotPublish()throws Exception {
        CountDownLatch gate=new CountDownLatch(1);
        try(var world=new World(42);var other=new World(99);var sky=new SkyVisibilityScheduler()) {
            var p=new ChunkPos(0,4,0);var c=new Chunk();c.setBlock(0,0,0,Blocks.STONE);world.addChunk(p,c);
            world.prepareTerrainTask(()->{gate.await();return null;});sky.update(world,2_000_000);
            world.apply(new Protocol.Edit(0,64,0,0));gate.countDown();
            var ready=await(sky,world);assertTrue(sky.rejected>0);assertFalse(ready.valid(other));
            world.unloadChunk(p);assertFalse(ready.valid(world));assertTrue(sky.cachedBytes()<=4L*WorldSkyVisibility.MAX_INTS);
        }finally{gate.countDown();}
    }
    @Test void endingInitialAllowanceFinishesAnAlreadyLargerPartialBatch()throws Exception {
        try(var world=new World(42);var scheduler=new SkyVisibilityScheduler()){
            for(int x=0;x<17;x++)for(int y=-2;y<=7;y++)world.addChunk(new ChunkPos(x,y,0),new Chunk());
            var tickets=SkyVisibilityScheduler.class.getDeclaredField("tickets");tickets.setAccessible(true);var capture=SkyVisibilityScheduler.class.getDeclaredField("capture");capture.setAccessible(true);
            // Deterministic boundary fixture: nine completed startup inputs and one
            // partially captured tenth input when the normal eight-input limit takes over.
            var kind=Class.forName("dev.jayms.render.SkyVisibilityScheduler$Capture");var constructor=kind.getDeclaredConstructors()[0];constructor.setAccessible(true);
            var pump=kind.getDeclaredMethod("pump",World.class,long.class);pump.setAccessible(true);var input=kind.getDeclaredMethod("input");input.setAccessible(true);var ticket=kind.getDeclaredField("ticket");ticket.setAccessible(true);var inputs=SkyVisibilityScheduler.class.getDeclaredField("inputs");inputs.setAccessible(true);
            @SuppressWarnings("unchecked") var completedTickets=(java.util.List<Object>)tickets.get(scheduler);
            @SuppressWarnings("unchecked") var completedInputs=(java.util.List<Object>)inputs.get(scheduler);
            for(int x=0;x<9;x++){var item=constructor.newInstance(world,new ChunkPos(x,0,0),world.models().copy());assertEquals(true,pump.invoke(item,world,Long.MAX_VALUE));completedTickets.add(ticket.get(item));completedInputs.add(input.invoke(item));}
            capture.set(scheduler,constructor.newInstance(world,new ChunkPos(9,0,0),world.models().copy()));
            long end=System.nanoTime()+10_000_000_000L;SkyVisibilityScheduler.Prepared ready=null;
            while(System.nanoTime()<end&&(scheduler.cachedColumns()!=17||scheduler.pending()!=0)){
                var next=scheduler.update(world,0,0,1_000_000,false);if(next!=null)ready=next;Thread.sleep(1);
            }
            assertEquals(17,scheduler.cachedColumns(),"Transition to normal budget cannot strand a partially captured startup batch");assertEquals(0,scheduler.pending());assertNotNull(ready);
        }
    }

    @Test void disjointArrivalsDoNotRestartAnInProgressMergeAndNewerTilesStillPublish()throws Exception {
        try(var world=new World(42);var sky=new SkyVisibilityScheduler()){
            for(int x=0;x<2;x++)for(int y=-2;y<=7;y++){var p=new ChunkPos(x,y,0);world.addChunk(p,ChunkGenerator.generate(p,world.terrain()));}
            await(sky,world,p->sky.cachedColumns()==2&&sky.pending()==0&&p.localReady(world,0,0));
            var cache=SkyVisibilityScheduler.class.getDeclaredField("cache");cache.setAccessible(true);var values=new java.util.ArrayList<>(((java.util.Map<?,?>)cache.get(sky)).values());
            var generation=SkyVisibilityScheduler.class.getDeclaredField("generation");generation.setAccessible(true);long current=generation.getLong(sky);int[] visited={0};
            // Resume a cursor from before a second disjoint tile arrived. A weak
            // iterator is allowed to omit that arrival; the follow-up must include it.
            var iterator=java.util.List.of(values.get(0)).iterator();java.util.Iterator<Object> cursor=new java.util.Iterator<>(){public boolean hasNext(){return iterator.hasNext();}public Object next(){visited[0]++;return iterator.next();}};
            var field=SkyVisibilityScheduler.class.getDeclaredField("packingIterator");field.setAccessible(true);field.set(sky,cursor);
            field=SkyVisibilityScheduler.class.getDeclaredField("packingParts");field.setAccessible(true);field.set(sky,new java.util.ArrayList<>());
            field=SkyVisibilityScheduler.class.getDeclaredField("packingGeneration");field.setAccessible(true);field.setLong(sky,current-1);
            field=SkyVisibilityScheduler.class.getDeclaredField("dirty");field.setAccessible(true);field.setBoolean(sky,true);
            sky.update(world,0,0,10_000_000);assertEquals(1,visited[0],"Disjoint arrivals cannot discard completed cursor work");
            field=SkyVisibilityScheduler.class.getDeclaredField("submittedGeneration");field.setAccessible(true);assertEquals(current-1,field.getLong(sky),"Merge records the generation when capture began, not when it ended");
            field=SkyVisibilityScheduler.class.getDeclaredField("packed");field.setAccessible(true);((Future<?>)field.get(sky)).get(10,TimeUnit.SECONDS);
            var partial=sky.update(world,0,0,1_000_000);assertNotNull(partial);assertEquals(1,sky.partialPublications);
            var complete=await(sky,world,p->sky.pending()==0&&p.localReady(world,16,0));assertArrayEquals(WorldSkyVisibility.build(world).data,complete.data.data,"A follow-up includes the omitted newer tile");
        }
    }
    @Test void completedMergeSurvivesEquivalentGeneratedUnloadBeforeCollection()throws Exception {
        try(var world=new World(42);var sky=new SkyVisibilityScheduler()){
            for(int y=-2;y<=7;y++){var p=new ChunkPos(0,y,0);world.addChunk(p,ChunkGenerator.generate(p,world.terrain()));}
            await(sky,world,p->sky.cachedColumns()==1&&sky.pending()==0&&p.localReady(world,0,0));
            var dirty=SkyVisibilityScheduler.class.getDeclaredField("dirty");dirty.setAccessible(true);dirty.setBoolean(sky,true);sky.update(world,0,0,10_000_000);
            var field=SkyVisibilityScheduler.class.getDeclaredField("packed");field.setAccessible(true);((Future<?>)field.get(sky)).get(10,TimeUnit.SECONDS);
            for(int y=-2;y<=7;y++)world.unloadChunk(new ChunkPos(0,y,0));long rejected=sky.rejected;
            var result=sky.update(world,0,0,1_000_000);assertNotNull(result,"Detached equivalent terrain remains usable when residency changes during merge");assertTrue(result.valid(world));assertEquals(rejected,sky.rejected);
        }
    }

    @Test void continuousIteratorArrivalsCannotRetainAnUnboundedSnapshot()throws Exception {
        try(var world=new World(42);var sky=new SkyVisibilityScheduler()){
            for(int y=-2;y<=7;y++){var p=new ChunkPos(0,y,0);world.addChunk(p,ChunkGenerator.generate(p,world.terrain()));}
            await(sky,world,p->sky.cachedColumns()==1&&sky.pending()==0);
            var field=SkyVisibilityScheduler.class.getDeclaredField("cache");field.setAccessible(true);var value=((java.util.Map<?,?>)field.get(sky)).values().iterator().next();int[] visited={0};
            java.util.Iterator<Object> arrivals=new java.util.Iterator<>(){public boolean hasNext(){return true;}public Object next(){visited[0]++;return value;}};
            field=SkyVisibilityScheduler.class.getDeclaredField("packingIterator");field.setAccessible(true);field.set(sky,arrivals);
            field=SkyVisibilityScheduler.class.getDeclaredField("packingParts");field.setAccessible(true);field.set(sky,new java.util.ArrayList<>());
            field=SkyVisibilityScheduler.class.getDeclaredField("packingInts");field.setAccessible(true);field.setLong(sky,0);
            field=SkyVisibilityScheduler.class.getDeclaredField("packingLimit");field.setAccessible(true);field.setInt(sky,1);
            field=SkyVisibilityScheduler.class.getDeclaredField("dirty");field.setAccessible(true);field.setBoolean(sky,true);
            sky.update(world,0,0,10_000_000);assertEquals(1,visited[0],"A weak iterator cannot retain more columns than the capture-start capacity");
            field=SkyVisibilityScheduler.class.getDeclaredField("packed");field.setAccessible(true);((Future<?>)field.get(sky)).get(10,TimeUnit.SECONDS);
            var partial=sky.update(world,0,0,1_000_000);assertNotNull(partial);assertTrue(sky.partialPublications>0);
            await(sky,world,p->sky.pending()==0&&p.localReady(world,0,0));
        }
    }

    @Test void finishedTilesDispatchWhileTheNextTileStillNeedsCapture()throws Exception {
        try(var world=new World(42);var sky=new SkyVisibilityScheduler()){
            for(int x=0;x<2;x++)for(int y=-2;y<=7;y++){var p=new ChunkPos(x,y,0);world.addChunk(p,ChunkGenerator.generate(p,world.terrain()));}
            var kind=Class.forName("dev.jayms.render.SkyVisibilityScheduler$Capture");var constructor=kind.getDeclaredConstructors()[0];constructor.setAccessible(true);
            var first=constructor.newInstance(world,new ChunkPos(0,0,0),world.models().copy());var next=constructor.newInstance(world,new ChunkPos(1,0,0),world.models().copy());
            var pump=kind.getDeclaredMethod("pump",World.class,long.class);pump.setAccessible(true);assertEquals(true,pump.invoke(first,world,Long.MAX_VALUE));
            var ticket=kind.getDeclaredField("ticket");ticket.setAccessible(true);var input=kind.getDeclaredMethod("input");input.setAccessible(true);
            var field=SkyVisibilityScheduler.class.getDeclaredField("tickets");field.setAccessible(true);@SuppressWarnings("unchecked") var tickets=(java.util.List<Object>)field.get(sky);tickets.add(ticket.get(first));
            field=SkyVisibilityScheduler.class.getDeclaredField("inputs");field.setAccessible(true);@SuppressWarnings("unchecked") var inputs=(java.util.List<Object>)field.get(sky);inputs.add(input.invoke(first));
            field=SkyVisibilityScheduler.class.getDeclaredField("capture");field.setAccessible(true);field.set(sky,next);
            sky.update(world,0,0,0);field=SkyVisibilityScheduler.class.getDeclaredField("pending");field.setAccessible(true);var pending=(Future<?>)field.get(sky);
            assertNotNull(pending,"Finished tiles cannot sit idle behind the next partial capture or a batch-size target");pending.get(10,TimeUnit.SECONDS);
            var result=await(sky,world,p->sky.cachedColumns()==2&&sky.pending()==0&&p.localReady(world,16,0));assertArrayEquals(WorldSkyVisibility.build(world).data,result.data.data);
        }
    }

}
