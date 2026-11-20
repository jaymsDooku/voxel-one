package dev.jayms.combat.duel;

import dev.jayms.net.SecureTransport;
import java.net.InetAddress;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Production keyboard flow with real companion clients. Stage files contain synthetic state only. */
public final class DuelLifecyclePlaytest {
    private static void check(boolean value,String reason) {if(!value)throw new AssertionError(reason);}
    private static void await(BooleanSupplier condition,String reason,DuelClient... clients) throws Exception {
        long end=System.nanoTime()+20_000_000_000L,next=0,frame=0;
        while(!condition.getAsBoolean() && System.nanoTime()<end) {
            if(System.nanoTime()>=next) {
                for(var c:clients) if(c.connected())c.input(++frame,0,0,90,0,false,false,false,false,false,0);
                next=System.nanoTime()+100_000_000L;
            }
            Thread.sleep(10);
        }
        check(condition.getAsBoolean(),reason);
    }
    private static DuelClient connect(DuelServer server,String pin) throws Exception {
        return new DuelClient("127.0.0.1",server.port(),pin,DuelSession.Profile.NO_DEBUFF,false,"",0);
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=3 || !args[0].equals("--display") || !args[1].equals(System.getenv("DISPLAY")))
            throw new IllegalArgumentException("Use --display with inherited DISPLAY and ignored scratch directory");
        Path scratch=Path.of(args[2]);Files.createDirectories(scratch);
        var tls=SecureTransport.server(scratch.resolve("synthetic-tls"));
        var closed=new AtomicBoolean();var failure=new AtomicReference<Throwable>();
        try(var server=new DuelServer(tls.context(),InetAddress.getLoopbackAddress(),0,false);
            var departed=connect(server,tls.fingerprint())) {
            await(()->departed.state()!=null,"First waiter has state",departed);String abandoned=departed.match();
            try(var observer=new DuelClient("127.0.0.1",server.port(),tls.fingerprint(),DuelSession.Profile.NO_DEBUFF,true,abandoned,0)) {
                departed.close();await(()->observer.state()!=null && !observer.state().fighters().get(0).connected(),"Waiter departure observed",observer);
                try(var player=connect(server,tls.fingerprint());var opponent=connect(server,tls.fingerprint())) {
                    check(player.side()==0 && !player.match().equals(abandoned),"Newcomer is not stranded in abandoned room");
                    check(player.match().equals(opponent.match()),"Two newcomers share a fresh pairing");
                    await(()->player.state()!=null && player.state().phase()==DuelSession.Phase.READY,"Fresh pairing reaches READY",player,opponent);
                    Files.writeString(scratch.resolve("stage.txt"),"paired");
                    Thread companion=new Thread(()->{
                        try {
                            await(()->player.state().fighters().get(0).ready(),"Press R for the first pairing",player,opponent);
                            String previous=player.match();opponent.close();
                            await(()->!player.state().fighters().get(1).connected(),"Opponent disconnect reaches client",player);
                            Files.writeString(scratch.resolve("stage.txt"),"opponent-left");
                            await(()->!player.match().equals(previous) || player.state().phase()==DuelSession.Phase.RESULT,"Press N to leave READY",player);
                            check(!player.match().equals(previous),"N leaves READY before reconnect grace expires");
                            check(player.side()==0,"Requeued player waits in side A");
                            try(var replacement=connect(server,tls.fingerprint())) {
                                check(replacement.match().equals(player.match()),"Replacement joins live waiting player");
                                await(()->replacement.state()!=null && replacement.state().phase()==DuelSession.Phase.READY
                                        && player.state().phase()==DuelSession.Phase.READY
                                        && player.state().fighters().get(1).connected(),"Replacement pairing ready",player,replacement);
                                replacement.operation(DuelSession.Op.READY,0,0);
                                await(()->player.state().fighters().get(1).ready(),"Replacement ready acknowledged",player,replacement);
                                Files.writeString(scratch.resolve("stage.txt"),"replacement-ready");
                                await(()->player.state().phase()==DuelSession.Phase.LIVE && replacement.state().phase()==DuelSession.Phase.LIVE,"Press R for replacement duel",player,replacement);
                                check(player.state().fighters().get(0).body().health()==20 && player.state().fighters().get(1).body().health()==20,"Fresh round health reset");
                                check(server.healthy(),"Server remains healthy");
                                Files.writeString(scratch.resolve("receipt.json"),"{\"status\":\"passed\",\"abandonedWaiterSkipped\":true,\"readyRequeue\":true,\"replacementDuel\":\"LIVE\",\"healthA\":20,\"healthB\":20,\"serverTick\":"+player.state().tick()+"}\n");
                                Files.writeString(scratch.resolve("stage.txt"),"recovered-live");
                                long frame=0;
                                while(!closed.get()) {
                                    if(replacement.connected())replacement.input(++frame,0,0,-90,0,false,false,false,false,false,0);
                                    Thread.sleep(100);
                                }
                            }
                        } catch(Throwable e) {
                            failure.set(e);
                            try {Files.writeString(scratch.resolve("stage.txt"),"failed");}catch(Exception ignored){}
                        }
                    },"synthetic-lifecycle-companion");
                    companion.setDaemon(true);companion.start();
                    try {PracticeApp.run(player,scratch);}
                    finally {closed.set(true);companion.join(3000);}
                    if(failure.get()!=null)throw new AssertionError("Lifecycle keyboard playtest failed",failure.get());
                    check(Files.exists(scratch.resolve("receipt.json")),"Replacement round reached LIVE");
                    System.out.println("PASS: departed waiter skipped; production R/N/R recovered READY into a fresh live duel");
                }
            }
        }
    }
}
