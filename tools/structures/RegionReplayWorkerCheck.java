package dev.jayms.net;

import dev.jayms.ChunkPos;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/** Actual asynchronous worker integration; not a live paging or performance test. */
public final class RegionReplayWorkerCheck {
    static final RegionReplayWorker.Revision REV=new RegionReplayWorker.Revision(1,2,3);
    static final RegionReplayPage.CellRange RANGE=new RegionReplayPage.CellRange(0,1);
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void await(BooleanSupplier done)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(!done.getAsBoolean()&&System.nanoTime()<deadline)LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        require(done.getAsBoolean(),"Worker state deadline");
    }
    static void release(CountDownLatch latch)throws IOException {
        try{if(!latch.await(15,TimeUnit.SECONDS))throw new IOException("Synthetic latch deadline");}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException(interrupted);}
    }
    static void done(RegionReplayWorker.Request request)throws Exception {
        await(()->request.outcome().phase()!=RegionReplayWorker.Phase.LOADING&&request.outcome().phase()!=RegionReplayWorker.Phase.QUEUED);
    }
    static void close(RegionReplayWorker worker)throws Exception {
        worker.close();await(()->worker.status().state()==RegionReplayWorker.State.CLOSED||worker.status().state()==RegionReplayWorker.State.FAILED);
    }
    static RegionReplayWorker.Request request(RegionReplayWorker worker,int region)throws Exception{return worker.submit(new ChunkPos(region,0,0),RANGE,1,REV);}
    static RegionReplayWorker.Source source(Path folder,byte[] bytes,java.util.function.Predicate<Protocol.Edit> allowed,BooleanSupplier cancel,java.util.function.IntConsumer progress)throws IOException {
        var in=new DataInputStream(new ByteArrayInputStream(bytes));
        var spool=RegionReplaySpool.stage(folder,in,e->true,cancel,RegionReplaySpool.MAX_BYTES,progress);
        try{LocalGame.readRegionalTail(in,cancel);return new RegionReplayWorker.Source(spool,new Terrain(42),allowed,REV);}
        catch(IOException|RuntimeException|Error failure){try{spool.close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Fresh directory, result JSON, evidence PNG");
        var folder=Path.of(args[0]);var result=Path.of(args[1]);var png=Path.of(args[2]);
        require(!Files.exists(folder)&&!Files.exists(result)&&!Files.exists(png),"Fresh outputs required");Files.createDirectory(folder);
        var rows=List.of(new Protocol.Edit(0,0,0,Blocks.STONE),new Protocol.Edit(16,0,0,Blocks.GLASS),new Protocol.Edit(32,0,0,Blocks.LED));
        var history=new LinkedHashMap<String,Protocol.Edit>();rows.forEach(e->history.put(e.key(),e));
        var encoded=new ByteArrayOutputStream();var out=new DataOutputStream(encoded);RegionReplay.write(out,history,()->false,n->{});out.writeInt(0);
        byte[] bytes=encoded.toByteArray();var expected=new WorldVoxels(new Terrain(42));rows.forEach(expected::apply);
        var opened=new CountDownLatch(1);var openGate=new CountDownLatch(1);var reading=new CountDownLatch(1);var readGate=new CountDownLatch(1);
        var block=new AtomicBoolean(true);var allowed=new AtomicBoolean(true);Thread owner=Thread.currentThread();var workerThread=new AtomicReference<Thread>();
        var worker=new RegionReplayWorker(folder,(cancel,progress)->{
            require(Thread.currentThread()!=owner,"Factory is asynchronous");workerThread.set(Thread.currentThread());opened.countDown();release(openGate);
            return source(folder,bytes,e->{require(Thread.currentThread()==workerThread.get(),"Single resource owner");
                if(block.getAndSet(false)){reading.countDown();try{release(readGate);}catch(IOException failure){throw new UncheckedIOException(failure);}}return allowed.get();},cancel,progress);
        },2,2,56,1);
        var pages=new ArrayList<RegionReplayPage>();
        try {
            require(opened.await(15,TimeUnit.SECONDS),"Opening gate");var active=request(worker,0);var queued=request(worker,1);
            try{request(worker,2);throw new AssertionError("Queue full rejection");}catch(IOException expectedFailure){}
            require(queued.cancel(),"Queued cancellation");var next=request(worker,2);openGate.countDown();
            require(reading.await(15,TimeUnit.SECONDS),"Read gate");require(active.outcome().phase()==RegionReplayWorker.Phase.LOADING,"Observable progress");
            require(active.cancel(),"Active cancellation");readGate.countDown();done(next);
            require(active.outcome().phase()==RegionReplayWorker.Phase.CANCELLED,"Late result discarded");pages.add(next.result(REV));
            var retry=request(worker,0);done(retry);pages.add(retry.result(REV));
            var stale=worker.submit(new ChunkPos(1,0,0),RANGE,1,new RegionReplayWorker.Revision(9,2,3));done(stale);
            require(stale.outcome().phase()==RegionReplayWorker.Phase.FAILED&&stale.checkedRows()==0,"Stale source tag rejected before read");
            try{retry.result(new RegionReplayWorker.Revision(9,2,3));throw new AssertionError("Changed extraction revision");}catch(IOException expectedFailure){}
            allowed.set(false);var denied=request(worker,0);done(denied);require(denied.outcome().phase()==RegionReplayWorker.Phase.FAILED,"Read policy rejection");
            allowed.set(true);var accepted=request(worker,0);done(accepted);pages.add(accepted.result(REV));
            for(var page:pages){var edit=page.history().values().iterator().next();require(page.history().size()==1&&rows.contains(edit),"History oracle");require(page.cells().values().iterator().next().tree().sameValues(expected.cell(edit.x(),edit.y(),edit.z())),"Voxel oracle");}
        }finally{openGate.countDown();readGate.countDown();close(worker);}
        for(var page:pages)require(page.cells().size()==1,"Detached lifetime after worker close");
        try(var files=Files.list(folder)){require(files.findAny().isEmpty(),"Worker cleanup");}
        var bad=Arrays.copyOf(bytes,bytes.length+1);bad[bad.length-1]=1;
        var tailGate=new CountDownLatch(1);var tailEntered=new CountDownLatch(1);
        var invalid=new RegionReplayWorker(folder,(cancel,progress)->{tailEntered.countDown();release(tailGate);return source(folder,bad,e->true,cancel,progress);},1,1,28,1);
        try{require(tailEntered.await(15,TimeUnit.SECONDS),"Tail gate");var denied=request(invalid,0);tailGate.countDown();done(denied);
            require(denied.outcome().phase()==RegionReplayWorker.Phase.FAILED,"Invalid whole-body tail rejected");await(()->invalid.status().state()==RegionReplayWorker.State.FAILED);
        }finally{tailGate.countDown();close(invalid);}
        try(var files=Files.list(folder)){require(files.findAny().isEmpty(),"Invalid-tail owned cleanup");}Files.delete(folder);
        String json="{\"status\":\"passed\",\"qualification\":false,\"queue_capacity\":2,\"source_rows\":3,\"verified_detached_pages\":"+pages.size()+",\"queued_and_running_cancellation\":true,\"source_and_extraction_revision_rejection\":true,\"invalid_tail_cleanup\":true}";
        Files.writeString(result,json+"\n");System.out.println(json);
        String text="Voxel One: asynchronous replay worker CLI\n\nActual worker integration passed before this window.\n\nOne resource-owning worker; queue cap 2; full queue rejected.\nQueued cancellation freed capacity; running cancellation discarded late output.\nOwner polled progress without callbacks or waiting for file I/O in the API.\nSource and extraction revision tags rejected mismatches.\nPolicy failure allowed a later request to succeed.\nThree detached pages matched independent history and voxel geometry.\nShared production regional-tail parser rejected trailing data.\nWorker/source/cache cleanup passed; pages survived worker close.\n\nPrivate readback only: no live authority/rendering installation.\nFactory still owns full metadata/model validation before returning a source.\nCaller still owns actual object identity/revision fences before installation.\nNo frame, 100 ms ack/stall, peak-memory, GPU, network or city qualification.\nPublic limits and stable rollout remain gated.";
        javax.swing.SwingUtilities.invokeAndWait(()->{
            var frame=new javax.swing.JFrame("Voxel One asynchronous replay CLI");var area=new javax.swing.JTextArea(text);
            area.setEditable(false);area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,0,15));area.setMargin(new java.awt.Insets(20,20,20,20));
            frame.add(area);frame.setSize(1130,550);frame.setLocation(30,30);frame.setVisible(true);
            var timer=new javax.swing.Timer(1200,e->{try{javax.imageio.ImageIO.write(new java.awt.Robot().createScreenCapture(frame.getBounds()),"png",png.toFile());}
                catch(Exception failure){throw new RuntimeException(failure);}finally{frame.dispose();}});timer.setRepeats(false);timer.start();
        });
    }
}
