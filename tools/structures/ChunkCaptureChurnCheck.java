import dev.jayms.net.*;
import java.util.*;

/** Synthetic retained-table churn; helper diagnostic, not city qualification. */
public final class ChunkCaptureChurnCheck {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static double ms(long nanos){return nanos/1_000_000.0;}
    static String stats(double[] values){var sorted=values.clone();Arrays.sort(sorted);return String.format(Locale.ROOT,"{\"median_ms\":%.6f,\"p95_ms\":%.6f,\"worst_ms\":%.6f}",(sorted[9]+sorted[10])/2,sorted[18],sorted[19]);}
    public static void main(String[] args) {
        var world=new WorldVoxels(new Terrain(126));var history=new WorldVoxels.History();
        for(int i=0;i<8192;i++){var edit=new Protocol.Edit((i%128)*16,180,(i/128)*16,Blocks.STONE);world.apply(edit);WorldVoxels.remember(history,edit);}
        world.clear();history.clear();
        for(int i=0;i<257;i++){var edit=new Protocol.Edit((i%128)*16,180,(i/128)*16,Blocks.GLASS);world.apply(edit);WorldVoxels.remember(history,edit);}
        long revision=history.revision();double[] elapsed=new double[20],maxAdvance=new double[20];
        for(int run=-1;run<20;run++) {
            long start=System.nanoTime(),maximum=0;var builder=world.beginCellSnapshot(history,257,257);WorldVoxels.CellSnapshot snapshot=null;int advances=0;
            while(snapshot==null){long before=System.nanoTime();snapshot=builder.advance(world,history);maximum=Math.max(maximum,System.nanoTime()-before);advances++;}
            require(advances==3,"Expected three chunk batches");int count=0;
            while(true){var positions=snapshot.nextBatch(world,history);if(positions.isEmpty())break;count+=positions.size();for(var p:positions)require(snapshot.cell(p).tree().uniform(0,0,0,16)==WorldVoxels.encode(Blocks.GLASS),"Lost geometry after churn");}
            require(count==257&&snapshot.unchanged(world,history),"Lost cells or mutated source");
            if(run>=0){elapsed[run]=ms(System.nanoTime()-start);maxAdvance[run]=ms(maximum);}
        }
        require(history.size()==257&&history.revision()==revision,"Source history changed");
        var stale=world.beginCellSnapshot(history,257,257);require(stale.advance(world,history)==null,"Missing partial batch");world.clear();
        try{stale.advance(world,history);throw new AssertionError("Stale clear accepted");}catch(ConcurrentModificationException expected){}
        System.out.println("{\"status\":\"passed\",\"warmups\":1,\"runs\":20,\"prior_chunks\":8192,\"final_chunks\":257,\"advances\":3,\"geometry_checked_each_run\":true,\"stale_clear_rejected\":true,\"wall\":"+stats(elapsed)+",\"max_advance_per_run\":"+stats(maxAdvance)+",\"wall_samples_ms\":"+Arrays.toString(elapsed)+",\"max_advance_samples_ms\":"+Arrays.toString(maxAdvance)+",\"qualification\":false}");
    }
}
