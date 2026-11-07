import dev.jayms.ChunkPos;
import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Private copy-on-write directory checks. No authority, renderer or live backend wiring. */
public final class SharedHashChunkDirectoryCheck {
    private static int rejects;
    private static long sink;
    private static void require(boolean condition){if(!condition)throw new AssertionError("Shared directory check");}
    private static void reject(Class<? extends Throwable> kind,Runnable action){try{action.run();}catch(Throwable failure){if(kind.isInstance(failure)){rejects++;return;}throw new AssertionError(failure);}throw new AssertionError("Missing rejection");}
    private static <V> Map<ChunkPos,V> map(SharedHashChunkDirectoryPrototype<V> directory){var result=new HashMap<ChunkPos,V>();for(var e:directory){require(result.put(e.getKey(),e.getValue())==null);require(directory.get(e.getKey())==e.getValue());}require(result.size()==directory.size());return result;}
    private static void checks()throws Exception {
        reject(IllegalArgumentException.class,()->new SharedHashChunkDirectoryPrototype<>(0));reject(IllegalArgumentException.class,()->new SharedHashChunkDirectoryPrototype<>(1_048_577));
        var tiny=new SharedHashChunkDirectoryPrototype<Integer>(1);var key=new ChunkPos(0,0,0);reject(NoSuchElementException.class,()->tiny.iterator().next());
        reject(NullPointerException.class,()->tiny.get(null));reject(NullPointerException.class,()->tiny.put(null,1));reject(NullPointerException.class,()->tiny.put(key,null));reject(NullPointerException.class,()->tiny.remove(null));
        tiny.put(key,1);var before=tiny.snapshot();tiny.put(key,2);require(before.get(key)==1&&tiny.get(key)==2);
        reject(IllegalArgumentException.class,()->tiny.put(new ChunkPos(1,0,0),3));require(tiny.size()==1);var entry=tiny.iterator().next();reject(UnsupportedOperationException.class,()->entry.setValue(3));var iterator=tiny.iterator();reject(UnsupportedOperationException.class,iterator::remove);
        tiny.remove(key);require(tiny.size()==0&&before.get(key)==1);tiny.put(new ChunkPos(Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE),7);tiny.clear();require(tiny.size()==0&&before.size()==1);
        var current=new SharedHashChunkDirectoryPrototype<Integer>(8192);var oracle=new HashMap<ChunkPos,Integer>();var roots=new ArrayDeque<SharedHashChunkDirectoryPrototype<Integer>>();var copies=new ArrayDeque<Map<ChunkPos,Integer>>();var random=new Random(81549);
        for(int i=0;i<25000;i++) {
            var k=new ChunkPos(random.nextInt(61)-30,random.nextInt(3)-1,random.nextInt(61)-30);
            if(random.nextBoolean()){Integer v=random.nextInt();current.put(k,v);oracle.put(k,v);}else{current.remove(k);oracle.remove(k);}
            if(i%251==0){if(roots.size()==8)require(map(roots.remove()).equals(copies.remove()));roots.add(current.snapshot());copies.add(new HashMap<>(oracle));}
            if(i%127==0)require(map(current).equals(oracle));
        }
        require(map(current).equals(oracle));while(!roots.isEmpty())require(map(roots.remove()).equals(copies.remove()));
        // A stable iterator survives writes/clear; both sides of a snapshot can keep changing.
        var retained=current.snapshot();var stable=current.iterator();current.clear();var seen=new HashMap<ChunkPos,Integer>();while(stable.hasNext()){var e=stable.next();require(seen.put(e.getKey(),e.getValue())==null);}require(seen.equals(oracle));require(map(retained).equals(oracle));
        var workerCopy=retained.snapshot();var worker=Executors.newSingleThreadExecutor();
        try{worker.submit(()->{for(int i=0;i<1000;i++)workerCopy.put(new ChunkPos(-10000-i,50,i),i);}).get(10,TimeUnit.SECONDS);require(map(retained).equals(oracle));require(workerCopy.size()==retained.size()+1000);}
        finally{worker.shutdownNow();require(worker.awaitTermination(10,TimeUnit.SECONDS));}
        var collisions=new SharedHashChunkDirectoryPrototype<Integer>(4096);var collisionMap=new HashMap<ChunkPos,Integer>();
        for(int i=-1000;i<=1000;i++){var k=new ChunkPos(i,0,-961*i);collisions.put(k,i);collisionMap.put(k,i);}
        for(var k:List.of(new ChunkPos(Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE),new ChunkPos(Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE))){collisions.put(k,7);collisionMap.put(k,7);}
        require(map(collisions).equals(collisionMap));var held=collisions.snapshot();for(var k:collisionMap.keySet())collisions.remove(k);require(collisions.size()==0&&map(held).equals(collisionMap));
        // Deliberately concentrate distinct keys in one shard. This verifies the
        // documented absence of a worst-case copy bound, not a timing pass.
        var clustered=new SharedHashChunkDirectoryPrototype<Integer>(256);var clusterMap=new HashMap<ChunkPos,Integer>();
        for(int i=0;clustered.size()<256&&i<100000;i++) {
            var k=new ChunkPos(i,23,-i);clustered.put(k,i);
            if(clustered.maxShardSize()!=clustered.size())clustered.remove(k);else clusterMap.put(k,i);
        }
        require(clustered.size()==256&&clustered.maxShardSize()==256);var clusterOld=clustered.snapshot();var clusterKey=clusterMap.keySet().iterator().next();clustered.put(clusterKey,-1);
        require(clustered.copiedEntries()==256&&map(clusterOld).equals(clusterMap));
        // The directory itself cannot protect mutable payloads. Use the production frozen image contract.
        var tree=new SparseVoxelOctree(16);tree.fill(1,2,3,7,8,9,WorldVoxels.encode(Blocks.GLASS));var image=WorldVoxels.prepareCellImage(tree);var images=new SharedHashChunkDirectoryPrototype<WorldVoxels.CellImage>(256);var world=new WorldVoxels(new Terrain(42));
        for(int z=-8;z<8;z++)for(int x=-8;x<8;x++){var k=new ChunkPos(x,8,z);images.put(k,image);world.installCellImage(x*16,128,z*16,image);}var original=images.snapshot();
        var replacement=new SparseVoxelOctree(16);replacement.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));images.put(new ChunkPos(-8,8,-8),WorldVoxels.prepareCellImage(replacement));
        for(var e:original){var k=e.getKey();require(e.getValue().tree().leaves().equals(world.cell(k.chunkX()*16,128,k.chunkZ()*16).leaves()));}
        tree.set(0,0,0,WorldVoxels.encode(Blocks.BRICKS));world.apply(new Protocol.Edit(-128,128,-128,Blocks.BRICKS));require(original.get(new ChunkPos(-8,8,-8))==image);require(image.tree().get(0,0,0)==0&&images.get(new ChunkPos(-8,8,-8)).tree().occupied()==4096);reject(IllegalStateException.class,()->image.tree().set(0,0,0,1));
    }
    private static String stats(double[] values){var sorted=values.clone();Arrays.sort(sorted);return String.format(Locale.ROOT,"{\"median\":%.6f,\"p95\":%.6f,\"worst\":%.6f,\"samples\":%s}",(sorted[9]+sorted[10])/2,sorted[18],sorted[19],Arrays.toString(values));}
    private static String benchmark() {
        var linked=new LinkedHashMap<ChunkPos,Integer>();var root=new SharedHashChunkDirectoryPrototype<Integer>(16384);var keys=new ArrayList<ChunkPos>();for(int z=0;z<64;z++)for(int x=0;x<128;x++){var k=new ChunkPos(x-64,8,z-32);Integer v=z*128+x;keys.add(k);linked.put(k,v);root.put(k,v);}
        var queries=new ArrayList<ChunkPos>();var random=new Random(89);for(int i=0;i<65536;i++)queries.add(i%4==0?new ChunkPos(2000+i,8,2000):keys.get(random.nextInt(keys.size())));
        double[][] times=new double[6][20];long copied=0;
        for(int run=-3;run<20;run++) {
            LinkedHashMap<ChunkPos,Integer> copy=null;SharedHashChunkDirectoryPrototype<Integer> changed=null;long lc=0,sc=0,lw=0,sw=0,lr=0,sr=0;long a=0,b=0;
            for(int mode=0;mode<2;mode++){boolean shared=((mode+run)&1)==0;long begin=System.nanoTime();if(shared){changed=root.snapshot();sc=System.nanoTime()-begin;}else{copy=new LinkedHashMap<>(linked);lc=System.nanoTime()-begin;}}
            for(int mode=0;mode<2;mode++){boolean shared=((mode+run)&1)==0;long begin=System.nanoTime();if(shared){for(int i=0;i<256;i++)changed.put(keys.get(i*31),-i);sw=System.nanoTime()-begin;}else{for(int i=0;i<256;i++)copy.put(keys.get(i*31),-i);lw=System.nanoTime()-begin;}}
            for(int mode=0;mode<2;mode++){boolean shared=((mode+run)&1)==0;long begin=System.nanoTime();if(shared){for(var k:queries){var v=changed.get(k);if(v!=null)a+=v;}sr=System.nanoTime()-begin;}else{for(var k:queries){var v=copy.get(k);if(v!=null)b+=v;}lr=System.nanoTime()-begin;}}
            require(a==b);sink^=a;copied=changed.copiedEntries();require(map(changed).equals(copy));require(root.get(keys.get(31))==linked.get(keys.get(31)));
            if(run>=0){long[] row={lc,sc,lw,sw,lr,sr};for(int i=0;i<row.length;i++)times[i][run]=row[i]/1e6;}
        }
        String[] names={"linked_snapshot_ms","shared_snapshot_ms","linked_update_ms","shared_update_ms","linked_read_ms","shared_read_ms"};var text=new StringBuilder("{\"entries\":8192,\"shards\":64,\"copied_entries_after_updates\":"+copied+",\"max_shard_entries\":"+root.maxShardSize()+",\"runs\":20,\"writes_per_run\":256,\"queries_per_run\":65536");for(int i=0;i<names.length;i++)text.append(",\"").append(names[i]).append("\":").append(stats(times[i]));return text.append('}').toString();
    }
    public static void main(String[] args)throws Exception {
        if(args.length>2||args.length==2&&!args[1].equals("--evidence"))throw new IllegalArgumentException("Result path and optional --evidence");checks();String result="{\"status\":\"passed\",\"rejection_cases\":"+rejects+",\"random_operations\":25000,\"retained_roots\":8,\"frozen_world_images\":true,\"benchmark\":"+benchmark()+",\"qualification\":false}";
        if(args.length>0){var p=Path.of(args[0]);if(Files.exists(p))throw new IllegalArgumentException("Fresh result path required");Files.writeString(p,result+"\n");}System.out.println(result);
        if(args.length==2)javax.swing.SwingUtilities.invokeAndWait(()->{var f=new javax.swing.JFrame("Voxel One shared hash directory CLI evidence");var t=new javax.swing.JTextArea("Voxel One: shared hash chunk directory\n\nActual CLI checks and 20-run component benchmark completed.\n25,000 mixed operations matched the independent map oracle.\nEight retained roots, both-side writes, stable iterators and clear passed.\nRecord-hash collisions, extreme keys, worker fork and frozen world images passed.\nInvalid capacity/null/mutation requests rejected.\n\n8,192 entries; snapshots copy 64 references.\nFirst writes after sharing copy the affected linked hash shard.\nNo worst-case shard size or main-thread latency guarantee.\n\nPrivate experiment only; production backend/caps unchanged.\nFull city/max, GPU, 32-client, memory/frame/tick/traffic qualification pending.");t.setEditable(false);t.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,0,16));t.setMargin(new java.awt.Insets(18,18,18,18));f.add(t);f.setSize(1060,480);f.setLocation(60,60);f.setVisible(true);var timer=new javax.swing.Timer(15000,e->f.dispose());timer.setRepeats(false);timer.start();});
    }
}
