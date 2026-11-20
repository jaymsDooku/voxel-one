package dev.jayms.combat.duel;

import dev.jayms.net.SecureTransport;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Real pinned-TLS coverage for departure before a round starts. No privileged packets. */
class DuelLifecycleTest {
    @TempDir Path temporary;
    static void await(BooleanSupplier condition,long millis,DuelClient... clients) throws Exception {
        long until=System.nanoTime()+millis*1_000_000L,nextHeartbeat=0,frame=0;
        while(!condition.getAsBoolean() && System.nanoTime()<until) {
            if(System.nanoTime()>=nextHeartbeat) {
                // READY input is rejected for play but keeps the real socket alive during grace.
                for(var c:clients) if(c.connected()) c.input(++frame,0,0,90,0,false,false,false,false,false,0);
                nextHeartbeat=System.nanoTime()+100_000_000L;
            }
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(),"Timed out awaiting lifecycle recovery");
    }
    static DuelClient connect(DuelServer server,String pin) throws Exception {
        return new DuelClient("127.0.0.1",server.port(),pin,DuelSession.Profile.NO_DEBUFF,false,"",0);
    }
    static void readyAndStart(DuelClient a,DuelClient b) throws Exception {
        await(()->a.state()!=null && b.state()!=null && a.state().phase()==DuelSession.Phase.READY
                && b.state().phase()==DuelSession.Phase.READY,3000,a,b);
        a.operation(DuelSession.Op.READY,0,0);b.operation(DuelSession.Op.READY,0,0);
        await(()->a.state().phase()==DuelSession.Phase.LIVE && b.state().phase()==DuelSession.Phase.LIVE,5000,a,b);
    }
    @Test void newcomersMatchAfterFirstWaiterLeaves() throws Exception {
        var tls=SecureTransport.server(temporary.resolve("abandoned-waiter-tls"));
        try(var server=new DuelServer(tls.context(),InetAddress.getLoopbackAddress(),0,false);
            var departed=connect(server,tls.fingerprint())) {
            await(()->departed.state()!=null,3000,departed);String abandoned=departed.match();
            try(var spectator=new DuelClient("127.0.0.1",server.port(),tls.fingerprint(),DuelSession.Profile.NO_DEBUFF,true,abandoned,0)) {
                departed.close();
                await(()->spectator.state()!=null && !spectator.state().fighters().get(0).connected(),3000,spectator);
                try(var b=connect(server,tls.fingerprint());var c=connect(server,tls.fingerprint())) {
                    assertNotEquals(abandoned,b.match(),"A newcomer must not become B of an abandoned room");
                    assertEquals(0,b.side());assertEquals(b.match(),c.match());assertEquals(1,c.side());
                    readyAndStart(b,c);assertTrue(server.healthy());
                    assertFalse(spectator.state().fighters().get(0).connected());
                    assertFalse(spectator.state().fighters().get(1).connected());
                }
            }
        }
    }
    @ParameterizedTest @ValueSource(ints={0,1})
    void readySurvivorCanRequeue(int side) throws Exception {
        var tls=SecureTransport.server(temporary.resolve("ready-requeue-tls"));
        try(var server=new DuelServer(tls.context(),InetAddress.getLoopbackAddress(),0,false);
            var a=connect(server,tls.fingerprint());var b=connect(server,tls.fingerprint())) {
            var survivor=side==0?a:b;var departed=side==0?b:a;
            await(()->survivor.state()!=null && survivor.state().phase()==DuelSession.Phase.READY,3000,a,b);
            survivor.operation(DuelSession.Op.READY,0,0);
            await(()->survivor.state().fighters().get(side).ready(),2000,a,b);
            String old=survivor.match();departed.close();
            await(()->!survivor.state().fighters().get(1-side).connected(),3000,survivor);
            assertEquals(DuelSession.Phase.READY,survivor.state().phase());
            survivor.operation(DuelSession.Op.QUEUE,0,0);
            await(()->!survivor.match().equals(old),3000,survivor);
            assertEquals(0,survivor.side());
            try(var newcomer=connect(server,tls.fingerprint())) {
                assertEquals(survivor.match(),newcomer.match());readyAndStart(survivor,newcomer);
                assertEquals(20,survivor.state().fighters().get(0).body().health());assertTrue(server.healthy());
            }
        }
    }
    @Test void readyDisconnectGraceExpiresAndSurvivorCanFindNewOpponent() throws Exception {
        var tls=SecureTransport.server(temporary.resolve("ready-expiry-tls"));
        try(var server=new DuelServer(tls.context(),InetAddress.getLoopbackAddress(),0,false);
            var a=connect(server,tls.fingerprint());var b=connect(server,tls.fingerprint())) {
            await(()->a.state()!=null && a.state().phase()==DuelSession.Phase.READY,3000,a,b);
            b.close();await(()->!a.state().fighters().get(1).connected(),3000,a);
            assertEquals(DuelSession.Phase.READY,a.state().phase());
            await(()->a.state().phase()==DuelSession.Phase.RESULT,12000,a);
            assertEquals("Ready cancelled: B disconnected",a.state().result());
            assertEquals(0,a.state().fighters().get(0).score().hits());
            String old=a.match();a.operation(DuelSession.Op.QUEUE,0,0);await(()->!a.match().equals(old),3000,a);
            try(var c=connect(server,tls.fingerprint())) {assertEquals(a.match(),c.match());readyAndStart(a,c);}
        }
    }
    @Test void readyReconnectWithinGracePreservesRoomAndRequiresReturningPlayerReady() throws Exception {
        var tls=SecureTransport.server(temporary.resolve("ready-reconnect-tls"));
        try(var server=new DuelServer(tls.context(),InetAddress.getLoopbackAddress(),0,false);
            var a=connect(server,tls.fingerprint());var b=connect(server,tls.fingerprint())) {
            await(()->a.state()!=null && b.state()!=null && a.state().phase()==DuelSession.Phase.READY,3000,a,b);
            String match=a.match();a.operation(DuelSession.Op.READY,0,0);
            await(()->a.state().fighters().get(0).ready(),2000,a,b);
            b.interruptConnection();await(()->!a.state().fighters().get(1).connected(),3000,a);
            b.reconnect();await(()->a.state().fighters().get(1).connected(),3000,a,b);
            assertEquals(match,b.match());assertEquals(DuelSession.Phase.READY,a.state().phase());
            assertTrue(a.state().fighters().get(0).ready());assertFalse(a.state().fighters().get(1).ready());
            b.operation(DuelSession.Op.READY,0,0);
            await(()->a.state().phase()==DuelSession.Phase.LIVE && b.state().phase()==DuelSession.Phase.LIVE,5000,a,b);
            assertTrue(server.healthy());
        }
    }
}
