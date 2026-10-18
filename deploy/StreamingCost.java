import dev.jayms.*;
import dev.jayms.net.*;
/** Isolate CPU cost of the unchanged baseline's one-column request. No renderer or profile data. */
public class StreamingCost {
    public static void main(String[] args)throws Exception {
        try(var world=new World(748291)) {
            long start=System.nanoTime();world.stream(8,8,1);
            System.out.println("baseline_one_column_ms="+(System.nanoTime()-start)/1e6+"; chunks="+world.getLoadedChunks().size());
        }
    }
}
