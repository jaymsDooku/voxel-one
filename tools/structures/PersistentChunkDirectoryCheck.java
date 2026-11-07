import dev.jayms.ChunkPos;
import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Private directory checks and admitted-scale component measurements. No game root wiring. */
public final class PersistentChunkDirectoryCheck {
    private static int rejects;
    private static long sink;
    private static final Comparator<ChunkPos> ORDER=Comparator.comparingInt(ChunkPos::chunkX).thenComparingInt(ChunkPos::chunkY).thenComparingInt(ChunkPos::chunkZ);
    private static void require(boolean good){if(!good)throw new AssertionError("Directory check");}
    private static void reject(Class<? extends Throwable> type,Runnable action){try{action.run();}catch(Throwable failure){if(type.isInstance(failure)){rejects++;return;}throw new AssertionError(failure);}throw new AssertionError("Missing rejection "+type);}
    private static <V> void equal(PersistentChunkDirectoryPrototype<V> actual,Map<ChunkPos,V> expected) {
        actual.validate();require(actual.size()==expected.size());var entries=new LinkedHashMap<ChunkPos,V>();for(var e:actual){require(entries.put(e.getKey(),e.getValue())==null);require(actual.get(e.getKey())==e.getValue());}
        require(entries.equals(expected));var sorted=new ArrayList<>(entries.keySet());var copy=new ArrayList<>(sorted);copy.sort(ORDER);require(sorted.equals(copy));
    }
    private static void checks()throws Exception {
        reject(IllegalArgumentException.class,()->new PersistentChunkDirectoryPrototype<>(0));
        reject(IllegalArgumentException.class,()->new PersistentChunkDirectoryPrototype<>(1_048_577));
        var empty=new PersistentChunkDirectoryPrototype<Integer>(4096);require(empty.size()==0&&empty.height()==0&&empty.cleared()==empty);require(empty.without(new ChunkPos(1,2,3))==empty);
        reject(NoSuchElementException.class,()->empty.iterator().next());reject(NullPointerException.class,()->empty.get(null));reject(NullPointerException.class,()->empty.with(null,1));reject(NullPointerException.class,()->empty.with(new ChunkPos(0,0,0),null));reject(NullPointerException.class,()->empty.without(null));
        var ordered=empty;var oracle=new TreeMap<ChunkPos,Integer>(ORDER);
        for(int i=0;i<4096;i++){var k=new ChunkPos(i-2048,0,-i);var value=Integer.valueOf(i);ordered=ordered.with(k,value);oracle.put(k,value);if(i%127==0)equal(ordered,oracle);}
        equal(ordered,oracle);require(ordered.height()<=18);var full=ordered;var key=new ChunkPos(-2048,0,0);
        reject(IllegalArgumentException.class,()->full.with(new ChunkPos(Integer.MAX_VALUE,0,0),1));require(full.with(key,full.get(key))==full);
        var entry=full.iterator().next();reject(UnsupportedOperationException.class,()->entry.setValue(42));var iterator=full.iterator();reject(UnsupportedOperationException.class,iterator::remove);
        var stable=full.iterator();var emptied=full.cleared();require(emptied.size()==0&&full.size()==4096);int count=0;while(stable.hasNext()){stable.next();count++;}require(count==4096);
        for(int i=4095;i>=0;i--){var k=new ChunkPos(i-2048,0,-i);ordered=ordered.without(k);oracle.remove(k);if(i%127==0)equal(ordered,oracle);}equal(ordered,oracle);equal(full,toMap(full));
        // Deliberate ChunkPos hash collisions plus all extreme coordinate signs.
        var collisions=new PersistentChunkDirectoryPrototype<Integer>(4096);oracle.clear();
        for(int i=-1000;i<=1000;i++){var k=new ChunkPos(i,0,-961*i);Integer v=i;collisions=collisions.with(k,v);oracle.put(k,v);}
        for(var k:List.of(new ChunkPos(Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE),new ChunkPos(Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE))){collisions=collisions.with(k,7);oracle.put(k,7);}equal(collisions,oracle);
        // Mixed replacement/insertion/deletion with bounded retained roots and an independent oracle.
        var random=new Random(725881);var current=new PersistentChunkDirectoryPrototype<Integer>(8192);oracle.clear();
        var snapshots=new ArrayDeque<PersistentChunkDirectoryPrototype<Integer>>();var copies=new ArrayDeque<Map<ChunkPos,Integer>>();
        for(int i=0;i<25000;i++) {
            var k=new ChunkPos(random.nextInt(61)-30,random.nextInt(3)-1,random.nextInt(61)-30);
            if(random.nextBoolean()){Integer value=random.nextInt();current=current.with(k,value);oracle.put(k,value);}else{current=current.without(k);oracle.remove(k);}
            if(i%251==0){if(snapshots.size()==8){equal(snapshots.remove(),copies.remove());}snapshots.add(current);copies.add(new TreeMap<>(oracle));}
            if(i%127==0)equal(current,oracle);
        }
        equal(current,oracle);while(!snapshots.isEmpty())equal(snapshots.remove(),copies.remove());
        var shared=current;var worker=Executors.newSingleThreadExecutor();
        try {
            var result=worker.submit(()->{var fork=shared;for(int i=0;i<1000;i++)fork=fork.with(new ChunkPos(-10000-i,50,i),i);fork.validate();return fork;}).get(10,TimeUnit.SECONDS);
            equal(current,oracle);require(result.size()==shared.size()+1000);
        }finally{worker.shutdownNow();require(worker.awaitTermination(10,TimeUnit.SECONDS));}
        // Actual frozen world images remain independent across directory roots and later world edits.
        var tree=new SparseVoxelOctree(16);tree.fill(1,2,3,7,8,9,WorldVoxels.encode(Blocks.GLASS));var image=WorldVoxels.prepareCellImage(tree);
        var images=new PersistentChunkDirectoryPrototype<WorldVoxels.CellImage>(256);var world=new WorldVoxels(new Terrain(42));
        for(int z=-8;z<8;z++)for(int x=-8;x<8;x++){var k=new ChunkPos(x,8,z);images=images.with(k,image);world.installCellImage(x*16,128,z*16,image);}
        var original=images;var replaced=new SparseVoxelOctree(16);replaced.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));images=images.with(new ChunkPos(-8,8,-8),WorldVoxels.prepareCellImage(replaced));
        for(var e:original){var k=e.getKey();require(world.cell(k.chunkX()*16,128,k.chunkZ()*16).leaves().equals(e.getValue().tree().leaves()));}
        tree.set(0,0,0,WorldVoxels.encode(Blocks.BRICKS));world.apply(new Protocol.Edit(-128,128,-128,Blocks.BRICKS));require(original.get(new ChunkPos(-8,8,-8))==image);require(original.get(new ChunkPos(-8,8,-8)).tree().get(0,0,0)==0);require(images.get(new ChunkPos(-8,8,-8)).tree().occupied()==4096);
        reject(IllegalStateException.class,()->image.tree().set(0,0,0,1));original.validate();images.validate();
    }
    private static <V> Map<ChunkPos,V> toMap(PersistentChunkDirectoryPrototype<V> root){var map=new TreeMap<ChunkPos,V>(ORDER);for(var e:root)map.put(e.getKey(),e.getValue());return map;}
    private static String stats(double[] values){var sorted=values.clone();Arrays.sort(sorted);return String.format(Locale.ROOT,"{\"median\":%.6f,\"p95\":%.6f,\"worst\":%.6f,\"samples\":%s}",(sorted[9]+sorted[10])/2,sorted[18],sorted[19],Arrays.toString(values));}
    private static String benchmark() {
        var hash=new HashMap<ChunkPos,Integer>();var root=new PersistentChunkDirectoryPrototype<Integer>(16384);var keys=new ArrayList<ChunkPos>();
        for(int z=0;z<64;z++)for(int x=0;x<128;x++){var k=new ChunkPos(x-64,8,z-32);Integer value=z*128+x;keys.add(k);hash.put(k,value);root=root.with(k,value);}root.validate();
        var queries=new ArrayList<ChunkPos>();var r=new Random(89);for(int i=0;i<65536;i++)queries.add(i%4==0?new ChunkPos(2000+i,8,2000):keys.get(r.nextInt(keys.size())));
        double[] hc=new double[20],hu=new double[20],hr=new double[20],tu=new double[20],tr=new double[20];
        for(int run=-3;run<20;run++) {
            // Rotate read/update ordering. No timing result is checked against a hardware budget.
            HashMap<ChunkPos,Integer> copy;long begin=System.nanoTime();copy=new HashMap<>(hash);long cloneTime=System.nanoTime()-begin;
            var changed=root;long hashWrite=0,treeWrite=0,hashRead=0,treeRead=0;long a=0,b=0;
            for(int mode=0;mode<2;mode++) {
                boolean treeMode=((mode+run)&1)==0;begin=System.nanoTime();
                if(treeMode){for(int i=0;i<256;i++)changed=changed.with(keys.get(i*31),-i);treeWrite=System.nanoTime()-begin;}
                else{for(int i=0;i<256;i++)copy.put(keys.get(i*31),-i);hashWrite=System.nanoTime()-begin;}
            }
            for(int mode=0;mode<2;mode++) {
                boolean treeMode=((mode+run)&1)==0;begin=System.nanoTime();
                if(treeMode){for(var k:queries){var value=changed.get(k);if(value!=null)a+=value;}treeRead=System.nanoTime()-begin;}
                else{for(var k:queries){var value=copy.get(k);if(value!=null)b+=value;}hashRead=System.nanoTime()-begin;}
            }
            require(a==b);sink^=a;equal(changed,copy);require(root.get(keys.get(31))==hash.get(keys.get(31)));require(root.size()==8192&&changed.height()<=20);
            if(run>=0){hc[run]=cloneTime/1e6;hu[run]=hashWrite/1e6;hr[run]=hashRead/1e6;tu[run]=treeWrite/1e6;tr[run]=treeRead/1e6;}
        }
        return "{\"entries\":8192,\"height\":"+root.height()+",\"writes_per_run\":256,\"queries_per_run\":65536,\"runs\":20,\"hash_snapshot_ms\":"+stats(hc)+",\"hash_update_ms\":"+stats(hu)+",\"tree_update_ms\":"+stats(tu)+",\"hash_read_ms\":"+stats(hr)+",\"tree_read_ms\":"+stats(tr)+"}";
    }
    public static void main(String[] args)throws Exception {
        if(args.length>2)throw new IllegalArgumentException("Result path and optional --evidence");checks();var result="{\"status\":\"passed\",\"rejection_cases\":"+rejects+",\"random_operations\":25000,\"retained_roots\":8,\"frozen_world_images\":true,\"benchmark\":"+benchmark()+",\"qualification\":false}";
        if(args.length>0){var p=Path.of(args[0]);if(Files.exists(p))throw new IllegalArgumentException("Fresh result path required");Files.writeString(p,result+"\n");}System.out.println(result);
        if(args.length==2&&args[1].equals("--evidence"))javax.swing.SwingUtilities.invokeAndWait(()->{
            var frame=new javax.swing.JFrame("Voxel One persistent chunk directory CLI evidence");var text=new javax.swing.JTextArea("Voxel One: persistent chunk directory\n\nActual CLI assertions and 20-run component measurements completed.\n25,000 mixed operations matched an independent sorted-map oracle.\nEight retained roots stayed unchanged through later writes/deletes.\nSorted insert/delete, collisions, extreme coordinates and worker forks passed.\nFrozen production world images survived caller/world mutations.\nInvalid capacity/null/mutation requests rejected.\n\n8,192-entry benchmark: 256 updates and 65,536 reads per run.\nNo whole-map copy to retain an immutable root.\nTree queries add cost: inspect the saved raw measurements.\n\nPrivate experiment. Production HashMap and all game caps unchanged.\nNot city/max, renderer, persistence, network or memory qualification.");text.setEditable(false);text.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,0,16));text.setMargin(new java.awt.Insets(18,18,18,18));frame.add(text);frame.setSize(1040,540);frame.setLocation(60,60);frame.setVisible(true);var timer=new javax.swing.Timer(15000,e->frame.dispose());timer.setRepeats(false);timer.start();
        });
    }
}
