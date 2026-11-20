package dev.jayms.combat.duel;

import dev.jayms.combat.KitProfile.Item;
import dev.jayms.net.SecureTransport;
import java.net.InetAddress;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Observes production keyboard input over real TLS; never injects combat state or input. */
public final class DuelInputKitPlaytest {
    private static void check(boolean value,String reason) {if(!value)throw new AssertionError(reason);}
    private static void await(BooleanSupplier condition,String reason) throws Exception {
        long end=System.nanoTime()+30_000_000_000L;
        while(!condition.getAsBoolean() && System.nanoTime()<end)Thread.sleep(10);
        check(condition.getAsBoolean(),reason);
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=3 || !args[0].equals("--display") || !args[1].equals(System.getenv("DISPLAY")))
            throw new IllegalArgumentException("Use --display with inherited DISPLAY and ignored scratch directory");
        Path scratch=Path.of(args[2]);Files.createDirectories(scratch);
        var tls=SecureTransport.server(scratch.resolve("synthetic-tls"));var failure=new AtomicReference<Throwable>();
        try(var server=new DuelServer(tls.context(),InetAddress.getLoopbackAddress(),0,true);
            var player=new DuelClient("127.0.0.1",server.port(),tls.fingerprint(),DuelSession.Profile.NO_DEBUFF,false,"",0)) {
            Thread observer=new Thread(()->{
                try {
                    await(()->player.state()!=null,"Initial state");Files.writeString(scratch.resolve("stage.txt"),"ready");
                    await(()->player.state().phase()==DuelSession.Phase.LIVE,"Production R starts round");
                    Files.writeString(scratch.resolve("stage.txt"),"live");
                    await(()->Files.exists(scratch.resolve("shift-held")),"Shift before W signal");
                    check(!player.state().fighters().get(0).body().sprinting(),"Stationary Shift does not sprint");
                    await(()->player.state().fighters().get(0).body().sprinting(),"W while Shift held starts sprint");
                    var begin=player.state();Files.writeString(scratch.resolve("stage.txt"),"sprinting");
                    await(()->!player.state().fighters().get(0).body().sprinting(),"Release stops sprint");
                    var end=player.state();double distance=begin.fighters().get(0).body().pose().feet().distance(end.fighters().get(0).body().pose().feet());
                    check(distance>5,"Sustained production sprint moves over five blocks");
                    await(()->player.state().fighters().get(0).score().potsUsed()==1,"Right use spends one pot");
                    Files.writeString(scratch.resolve("stage.txt"),"pot-used");
                    await(()->player.state().phase()==DuelSession.Phase.RESULT,"Q ends round");
                    var finished=player.state().fighters().get(0);check(finished.body().inventory().get(1).count()==0,"Finished round spent slot stays empty");
                    Files.writeString(scratch.resolve("stage.txt"),"result");
                    await(()->player.state().fighters().get(0).nextKit().get(1).item()==Item.PEARL,"Result editor immediately exposes swap");
                    var edited=player.state().fighters().get(0);
                    check(edited.nextKit().get(2).item()==Item.HEAL_SPLASH,"Next kit contains swapped heal");
                    check(finished.body().equals(edited.body()) && finished.score().equals(edited.score()),"Result body and all scores stay frozen");
                    Files.writeString(scratch.resolve("stage.txt"),"edited");
                    await(()->player.state().phase()==DuelSession.Phase.LIVE,"R starts edited rematch");
                    check(player.state().fighters().get(0).body().inventory().equals(edited.nextKit()),"Rematch applies exactly the displayed kit");
                    check(player.state().fighters().get(0).score().potsUsed()==0,"Rematch resets pot stats");
                    check(server.healthy(),"Server healthy");
                    Files.writeString(scratch.resolve("receipt.json"),"{\"status\":\"passed\",\"shiftBeforeW\":true,\"sprintDistance\":"+distance+",\"startTick\":"+begin.tick()+",\"endTick\":"+end.tick()+",\"startInputFrame\":"+begin.fighters().get(0).lastFrame()+",\"endInputFrame\":"+end.fighters().get(0).lastFrame()+",\"resultStatsPreserved\":true,\"nextKitVisible\":true,\"rematchKitApplied\":true}\n");
                    Files.writeString(scratch.resolve("stage.txt"),"passed");
                } catch(Throwable e) {
                    failure.set(e);try{Files.writeString(scratch.resolve("stage.txt"),"failed");}catch(Exception ignored){}
                }
            },"synthetic-input-kit-observer");
            observer.setDaemon(true);observer.start();PracticeApp.run(player,scratch);observer.join(1000);
            if(failure.get()!=null)throw new AssertionError("Input/kit keyboard playtest failed",failure.get());
            check(Files.exists(scratch.resolve("receipt.json")),"Keyboard workflow completed");
            System.out.println("PASS: production Shift then W; RESULT next-kit swap; frozen results; edited rematch");
        }
    }
}
