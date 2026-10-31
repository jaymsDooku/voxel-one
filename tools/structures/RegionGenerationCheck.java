import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import java.util.zip.CRC32;

/** Synthetic file/lock/fault-injection checks; not a power-loss qualification. */
public final class RegionGenerationCheck {
    private static int rejected;
    private interface Action{void run()throws Exception;}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void reject(Class<? extends Throwable> type,String message,Action action)throws Exception {
        try{action.run();}catch(Throwable failure){if(type.isInstance(failure)&&(message==null||String.valueOf(failure.getMessage()).contains(message))){rejected++;return;}throw new AssertionError(message,failure);}
        throw new AssertionError("Unexpected acceptance: "+message);
    }
    private static RegionDependencyPrototype manifest(String name,int color,boolean large)throws IOException {
        var tree=new SparseVoxelOctree(large?32:8);
        if(large){for(int y=0;y<32;y++)for(int z=0;z<32;z++)for(int x=0;x<32;x++)tree.set(x,y,z,0xff000000|x+32*z+1024*y);}
        else tree.set(1,2,3,color);
        var geometry=RegionHistoryPrototype.migrate(123,Terrain.CURRENT_VERSION,List.of(new Protocol.Edit(0,128,0,1024),new Protocol.Edit(1,Terrain.MIN_Y,0,0),new Protocol.Edit(2,128,0,Blocks.piece(Blocks.LED,4),4,1,2,3,0x112233)),new RegionHistoryPrototype.Budget(10,10,10),()->false);
        return RegionDependencyPrototype.capture(geometry,Map.of(1024,new ModelDefinition(name,tree)),()->false);
    }
    private static long temps(Path root)throws IOException {try(var paths=Files.list(root)){return paths.filter(p->p.getFileName().toString().startsWith(".region-stage-")).count();}}
    private static void unchanged(Path root,byte[] expected)throws IOException{require(Arrays.equals(expected,Files.readAllBytes(root.resolve("current.region"))),"Published file changed on pre-publication failure");require(temps(root)==0,"Private staging file leaked");}
    private static RegionGenerationPrototype.Operations fault(boolean force,boolean after,boolean unsupported){return new RegionGenerationPrototype.Operations(){
        public void force(FileChannel file)throws IOException{if(force&&!after)throw new IOException("injected force");RegionGenerationPrototype.NIO.force(file);if(force)throw new IOException("injected force");}
        public void replace(Path stage,Path current)throws IOException{if(unsupported)throw new AtomicMoveNotSupportedException(stage.toString(),current.toString(),"injected unsupported");if(after)RegionGenerationPrototype.NIO.replace(stage,current);throw new IOException("injected move");}
    };}
    private static void crc(byte[] bytes){var crc=new CRC32();crc.update(bytes,0,bytes.length-4);ByteBuffer.wrap(bytes).putInt(bytes.length-4,(int)crc.getValue());}
    private static Path copy(Path parent,String name,byte[] bytes)throws IOException{var root=parent.resolve(name);Files.createDirectories(root);Files.write(root.resolve("current.region"),bytes);return root;}
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Synthetic parent directory required");
        Path parent=Path.of(args[0]);Files.createDirectories(parent);Path work=Files.createTempDirectory(parent,"generation-");
        try{run(work);}finally{try(var paths=Files.walk(work)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
    }
    private static void run(Path work)throws Exception {
        var a=manifest("alpha",0xff123456,false);var b=manifest("beta",0xffabcdef,false);
        var root=work.resolve("normal");var store=new RegionGenerationPrototype(root);
        require(store.load(()->false).isEmpty(),"New directory has a generation");
        RegionGenerationPrototype.Staged closed;
        try(var first=store.stage(a,()->false)){closed=first;require(store.load(()->false).isEmpty(),"Private stage visible");require(first.commit(()->false).generation()==1,"First generation differs");}
        reject(IOException.class,"closed",()->closed.commit(()->false));
        require(new RegionGenerationPrototype(root).load(()->false).orElseThrow().generation()==1,"Restart load differs");
        try(var winner=store.stage(b,()->false);var stale=new RegionGenerationPrototype(root).stage(a,()->false)) {
            require(winner.commit(()->false).generation()==2,"Second generation differs");
            reject(IOException.class,"Stale",()->stale.commit(()->false));
        }
        byte[] previous=Files.readAllBytes(root.resolve("current.region"));unchanged(root,previous);
        reject(CancellationException.class,null,()->store.stage(a,()->true));unchanged(root,previous);
        for(boolean after:new boolean[]{false,true}) {
            var failed=new RegionGenerationPrototype(root,fault(true,after,false));
            reject(IOException.class,"force",()->failed.stage(a,()->false));unchanged(root,previous);
        }
        for(boolean unsupported:new boolean[]{false,true}) {
            var failed=new RegionGenerationPrototype(root,fault(false,false,unsupported));
            try(var stage=failed.stage(a,()->false)){reject(IOException.class,unsupported?"unsupported":"move",()->stage.commit(()->false));}
            unchanged(root,previous);
        }
        var cancel=new AtomicBoolean();
        var cancelAfterForce=new RegionGenerationPrototype(root,new RegionGenerationPrototype.Operations(){
            public void force(FileChannel file)throws IOException{RegionGenerationPrototype.NIO.force(file);cancel.set(true);}
            public void replace(Path stage,Path current)throws IOException{throw new AssertionError("Cancelled stage moved");}
        });
        reject(CancellationException.class,null,()->cancelAfterForce.stage(a,cancel::get));unchanged(root,previous);
        try(var stage=store.stage(a,()->false)){reject(CancellationException.class,null,()->stage.commit(()->true));}unchanged(root,previous);
        try(var stage=store.stage(a,()->false);var channel=FileChannel.open(root.resolve("commit.lock"),StandardOpenOption.READ,StandardOpenOption.WRITE);var lock=channel.lock()) {
            reject(IOException.class,"active",()->stage.commit(()->false));
        }unchanged(root,previous);
        try(var stage=store.stage(a,()->false)) {
            var damaged=Files.readAllBytes(stage.path);damaged[damaged.length-1]^=1;Files.write(stage.path,damaged);
            reject(IOException.class,"CRC",()->stage.commit(()->false));
        }unchanged(root,previous);
        try(var stage=store.stage(a,()->false)) {
            var damaged=Files.readAllBytes(stage.path);ByteBuffer.wrap(damaged).putLong(8,123);crc(damaged);Files.write(stage.path,damaged);
            reject(IOException.class,"changed",()->stage.commit(()->false));
        }unchanged(root,previous);
        try(var abandoned=store.stage(a,()->false)){require(new RegionGenerationPrototype(root).load(()->false).orElseThrow().generation()==2,"Abandoned stage replaced committed state");}unchanged(root,previous);
        var recovered=new RegionGenerationPrototype(root,fault(false,true,false));
        try(var stage=recovered.stage(a,()->false)){var receipt=stage.commit(()->false);require(receipt.generation()==3&&receipt.recoveredMoveOutcome(),"Post-rename outcome misclassified");}
        require(store.load(()->false).orElseThrow().generation()==3&&temps(root)==0,"Post-rename publication missing");
        var lateCancel=new AtomicBoolean();
        var publication=new RegionGenerationPrototype(root,new RegionGenerationPrototype.Operations(){
            public void force(FileChannel file)throws IOException{RegionGenerationPrototype.NIO.force(file);}
            public void replace(Path stage,Path current)throws IOException{RegionGenerationPrototype.NIO.replace(stage,current);lateCancel.set(true);}
        });
        try(var stage=publication.stage(a,()->false)){require(stage.commit(lateCancel::get).generation()==4,"Publication point was treated as cancellation");}
        require(lateCancel.get()&&store.load(()->false).orElseThrow().generation()==4,"Late cancellation lost commit");
        var loaded=store.load(()->false).orElseThrow().manifest().restore(new ModelLibrary(),()->false);
        require(loaded.models().get(loaded.world().type(0,128,0)).definition().fingerprint().equals(a.bindings().get(1024).fingerprint()),"Restart dependency changed");
        require(loaded.world().type(1,Terrain.MIN_Y,0)==0&&WorldVoxels.lightColor(loaded.world().cell(2,128,0).get(1,2,3))==0x112233,"Restart geometry changed");
        for(String name:List.of("crc","version","zero","inner")) {
            var damaged=previous.clone();if(name.equals("crc"))damaged[damaged.length-1]^=1;
            else{if(name.equals("version"))ByteBuffer.wrap(damaged).putInt(4,2);if(name.equals("zero"))ByteBuffer.wrap(damaged).putLong(8,0);if(name.equals("inner"))damaged[20]^=1;crc(damaged);}
            var bad=new RegionGenerationPrototype(copy(work,name,damaged));reject(IOException.class,null,()->bad.load(()->false));
        }
        var truncated=new RegionGenerationPrototype(copy(work,"truncated",new byte[23]));reject(IOException.class,"byte budget",()->truncated.load(()->false));
        var directory=work.resolve("directory");var directoryStore=new RegionGenerationPrototype(directory);Files.createDirectory(directory.resolve("current.region"));reject(IOException.class,"regular",()->directoryStore.load(()->false));
        var oversizedRoot=work.resolve("oversized");var oversized=new RegionGenerationPrototype(oversizedRoot);
        try(var file=FileChannel.open(oversizedRoot.resolve("current.region"),StandardOpenOption.CREATE,StandardOpenOption.WRITE)){file.position(RegionGenerationPrototype.MAX_BYTES);file.write(ByteBuffer.wrap(new byte[]{0}));}
        reject(IOException.class,"byte budget",()->oversized.load(()->false));
        var maximum=previous.clone();ByteBuffer.wrap(maximum).putLong(8,Long.MAX_VALUE);crc(maximum);
        var exhausted=new RegionGenerationPrototype(copy(work,"exhausted",maximum));reject(IOException.class,"exhausted",()->exhausted.stage(a,()->false));
        var large=manifest("large",0,true);var largeRoot=work.resolve("large");var forced=new AtomicBoolean();
        var largeStore=new RegionGenerationPrototype(largeRoot,new RegionGenerationPrototype.Operations(){
            public void force(FileChannel file)throws IOException{forced.set(true);RegionGenerationPrototype.NIO.force(file);}
            public void replace(Path stage,Path current)throws IOException{RegionGenerationPrototype.NIO.replace(stage,current);}
        });
        var observed=new AtomicLong();var finalLarge=large;
        reject(CancellationException.class,null,()->largeStore.stage(finalLarge,()->{
            try(var files=Files.list(largeRoot)){for(var path:files.filter(p->p.getFileName().toString().startsWith(".region-stage-")).toList()){long size=Files.size(path);if(size>=65536){observed.set(size);return true;}}return false;}catch(IOException error){throw new UncheckedIOException(error);}
        }));
        require(observed.get()>=65536&&!forced.get()&&temps(largeRoot)==0&&largeStore.load(()->false).isEmpty(),"Mid-write cancellation leaked/published data");
        try(var stage=largeStore.stage(large,()->false)){require(stage.commit(()->false).generation()==1&&forced.get(),"Large forced publication failed");}
        // Replace the path with a differently sized valid generation after the
        // metadata check but before open; loading must size the opened handle.
        var raceRoot=copy(work,"read-race",previous);var replacement=Files.readAllBytes(largeRoot.resolve("current.region"));
        ByteBuffer.wrap(replacement).putLong(8,3);crc(replacement);var raced=new AtomicBoolean();
        var raceStore=new RegionGenerationPrototype(raceRoot,new RegionGenerationPrototype.Operations(){
            public void force(FileChannel file)throws IOException{RegionGenerationPrototype.NIO.force(file);}
            public void replace(Path stage,Path current)throws IOException{RegionGenerationPrototype.NIO.replace(stage,current);}
            public void beforeReadOpen(Path path)throws IOException{if(raced.compareAndSet(false,true)){Path next=raceRoot.resolve("replacement.tmp");Files.write(next,replacement);RegionGenerationPrototype.NIO.replace(next,path);}}
        });
        require(raceStore.load(()->false).orElseThrow().generation()==3,"Atomic read observed a stale path size");
        var callbacks=new AtomicInteger();largeStore.load(()->{callbacks.incrementAndGet();return false;});int midpoint=callbacks.get()/2;callbacks.set(0);
        reject(CancellationException.class,null,()->largeStore.load(()->callbacks.incrementAndGet()>midpoint));require(largeStore.load(()->false).orElseThrow().generation()==1,"Cancelled read changed state");
        Thread.currentThread().interrupt();try{reject(CancellationException.class,null,()->store.load(()->false));require(Thread.currentThread().isInterrupted(),"Interrupt flag cleared");}finally{Thread.interrupted();}
        System.out.printf("{\"status\":\"passed\",\"rejection_cases\":%d,\"final_generation\":4,\"mid_write_cancel_bytes\":%d,\"large_generation_bytes\":%d,\"directory_fsync_qualified\":false,\"qualification\":false}%n",rejected,observed.get(),Files.size(largeRoot.resolve("current.region")));
    }
}
