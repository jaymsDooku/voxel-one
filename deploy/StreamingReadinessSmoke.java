import dev.jayms.World;
import java.nio.file.*;

/** CLI regression for the playtest's condition wait; no GL or live profiles. */
public class StreamingReadinessSmoke {
    public static void main(String[] args)throws Exception {
        int[] calls={0};int attempts;
        try(var world=new World(748291)) {
            attempts=StreamingWorkflow.awaitReady(5_000_000_000L,()->{
                world.streamAsync(8,110,8,++calls[0]<=3?0:500_000L);
                return world.terrainPending()>0;
            },"Terrain admission timeout");
            if(attempts<4||world.terrainPending()<=0)throw new AssertionError("Load assertion was lost");
        }
        long start=System.nanoTime();
        try{StreamingWorkflow.awaitReady(10_000_000L,()->false,"Expected bounded timeout");throw new IllegalStateException("Missing timeout");}
        catch(AssertionError expected){if(!expected.getMessage().equals("Expected bounded timeout"))throw expected;}
        double timeoutMs=(System.nanoTime()-start)/1e6;
        Thread.currentThread().interrupt();
        try{StreamingWorkflow.awaitReady(1_000_000_000L,()->false,"Interrupt wait");throw new AssertionError("Missing interruption");}
        catch(InterruptedException expected){}finally{Thread.interrupted();}
        Files.writeString(Path.of(args[0]),"Playtest: PASS. CLI readiness integration; synthetic World; no GL. First three admission pumps deliberately have zero budget. Pending terrain then becomes observable after "+attempts+" pumps; load assertion preserved. Always-false condition times out (observed "+timeoutMs+"ms); interruption propagates. Browser playtesting does not apply to this harness helper.\n");
    }
}
