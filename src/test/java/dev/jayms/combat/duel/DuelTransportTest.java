package dev.jayms.combat.duel;

import dev.jayms.net.SecureTransport;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DuelTransportTest {
    @TempDir Path temporary;
    static void waitFor(BooleanSupplier condition,long millis) throws Exception {
        long until=System.nanoTime()+millis*1_000_000;while(!condition.getAsBoolean() && System.nanoTime()<until)Thread.sleep(10);
        assertTrue(condition.getAsBoolean(),"Timed out awaiting authoritative duel state");
    }
    @Test void boundedCodecRejectsUnknownVersionEnumsAndTrailingData() throws Exception {
        var bytes=new ByteArrayOutputStream();new DataOutputStream(bytes).writeInt(DuelWire.MAX_FRAME+1);
        assertThrows(IOException.class,()->DuelWire.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        var command=DuelSession.Command.operation(1,1,DuelSession.Op.READY,0,0);byte[] frame=DuelWire.command(command);
        var in=new DataInputStream(new ByteArrayInputStream(frame));assertEquals(DuelWire.COMMAND,in.readUnsignedByte());assertEquals(command,DuelWire.command(in));DuelWire.end(in);
        frame[25]=(byte)255;var invalid=new DataInputStream(new ByteArrayInputStream(frame));invalid.readUnsignedByte();assertThrows(IOException.class,()->DuelWire.command(invalid));
        var hello=DuelWire.hello(new DuelWire.Hello(DuelSession.Profile.NO_DEBUFF,false,"",""));hello[8]=(byte)(DuelWire.VERSION+1);
        var h=new DataInputStream(new ByteArrayInputStream(hello));h.readUnsignedByte();assertThrows(IOException.class,()->DuelWire.hello(h));
        assertThrows(IOException.class,()->DuelWire.end(new DataInputStream(new ByteArrayInputStream(new byte[]{1}))));
    }
    @Test void twoRealTlsClientsAuthoritativeAgreementReconnectRematchAndIsolation() throws Exception {
        var tls=SecureTransport.server(temporary.resolve("synthetic-tls"));
        try(var server=new DuelServer(tls.context(),InetAddress.getLoopbackAddress(),0,false);
            var a=new DuelClient("127.0.0.1",server.port(),tls.fingerprint(),DuelSession.Profile.NO_DEBUFF,false,"",0);
            var b=new DuelClient("127.0.0.1",server.port(),tls.fingerprint(),DuelSession.Profile.NO_DEBUFF,false,"",0);
            var c=new DuelClient("127.0.0.1",server.port(),tls.fingerprint(),DuelSession.Profile.DEBUFF,false,"",0);
            var spectator=new DuelClient("127.0.0.1",server.port(),tls.fingerprint(),DuelSession.Profile.NO_DEBUFF,true,a.match(),0)) {
            waitFor(()->a.state()!=null && b.state()!=null && c.state()!=null && spectator.state()!=null,3000);
            assertEquals(a.match(),b.match());assertNotEquals(a.match(),c.match());assertEquals(-1,spectator.side());
            spectator.operation(DuelSession.Op.READY,0,0);Thread.sleep(100);assertEquals(DuelSession.Phase.READY,a.state().phase());
            a.operation(DuelSession.Op.READY,0,0);b.operation(DuelSession.Op.READY,0,0);
            waitFor(()->a.state().phase()==DuelSession.Phase.LIVE && b.state().phase()==DuelSession.Phase.LIVE,5000);
            long frame=0;
            while(a.state().fighters().get(0).body().pose().feet().distance(a.state().fighters().get(1).body().pose().feet())>3.6 && frame<160) {
                a.input(frame,1,0,90,0,false,false,false,false,false,0);b.input(frame,1,0,-90,0,false,false,false,false,false,0);frame++;Thread.sleep(50);
            }
            a.input(frame,0,0,90,0,false,false,false,false,false,0);b.input(frame,0,0,-90,0,false,false,false,false,false,0);Thread.sleep(150);
            for(int i=0;i<5;i++){a.input(++frame,.25,0,90,0,false,false,false,false,false,0);Thread.sleep(50);}
            a.input(++frame,0,0,90,0,false,false,true,false,false,0);b.input(frame,0,0,-90,0,false,false,true,false,false,0);
            waitFor(()->a.state().fighters().get(0).score().hits()>0,2000);
            assertEquals(20,c.state().fighters().get(0).body().health());assertEquals(DuelSession.Phase.QUEUE,c.state().phase());
            var as=new HashMap<Long,DuelSession.State>();a.drainHistory().forEach(s->as.put(s.tick(),s));var bs=b.drainHistory();int matched=0;
            for(var s:bs) if(as.containsKey(s.tick())){assertEquals(as.get(s.tick()),s);matched++;}assertTrue(matched>20);
            a.input(++frame,0,0,90,-89,false,false,false,true,false,1);b.input(frame,0,0,-90,-89,false,false,false,true,false,1);
            waitFor(()->a.state().fighters().get(0).score().potsUsed()>0 && b.state().fighters().get(1).score().potsUsed()>0,2000);
            a.operation(DuelSession.Op.REFILL,1,0);waitFor(()->a.state().fighters().get(0).body().inventory().get(1).count()==1,2000);
            b.interruptConnection();Thread.sleep(200);b.reconnect();waitFor(()->b.connected() && b.state().phase()==DuelSession.Phase.LIVE,3000);
            assertEquals(DuelSession.Phase.LIVE,a.state().phase());
            a.operation(DuelSession.Op.FORFEIT,0,0);waitFor(()->b.state().phase()==DuelSession.Phase.RESULT,2000);
            waitFor(()->a.state().phase()==DuelSession.Phase.RESULT,2000);
            var finished=a.state().fighters().get(0);
            a.operation(DuelSession.Op.SWAP,1,2);
            waitFor(()->a.state().fighters().get(0).nextKit().get(1).item()==dev.jayms.combat.KitProfile.Item.PEARL
                    && b.state().fighters().get(0).nextKit().get(1).item()==dev.jayms.combat.KitProfile.Item.PEARL,2000);
            assertEquals(finished.body(),a.state().fighters().get(0).body());assertEquals(finished.score(),a.state().fighters().get(0).score());
            assertEquals(a.state().fighters().get(0).nextKit(),b.state().fighters().get(0).nextKit());
            long oldEpoch=b.state().epoch();a.operation(DuelSession.Op.READY,0,0);b.operation(DuelSession.Op.READY,0,0);
            waitFor(()->a.state().phase()==DuelSession.Phase.LIVE && a.state().epoch()>oldEpoch,5000);
            assertEquals(20,a.state().fighters().get(0).body().health());assertTrue(a.state().fighters().get(0).body().effects().isEmpty());
            assertEquals(dev.jayms.combat.KitProfile.Item.PEARL,a.state().fighters().get(0).body().inventory().get(1).item());
            a.operation(DuelSession.Op.FORFEIT,0,0);waitFor(()->b.state().phase()==DuelSession.Phase.RESULT,2000);
            String old=a.match();a.operation(DuelSession.Op.QUEUE,0,0);waitFor(()->!a.match().equals(old),3000);assertTrue(server.healthy());
        }
    }
    @Test void fullQueueRejectsOneRequestWithoutStoppingOtherMatches() throws Exception {
        var tls=SecureTransport.server(temporary.resolve("capacity-tls"));var clients=new ArrayList<DuelClient>();
        try(var server=new DuelServer(tls.context(),InetAddress.getLoopbackAddress(),0,false)) {
            try {
                for(int i=0;i<16;i++)clients.add(new DuelClient("127.0.0.1",server.port(),tls.fingerprint(),DuelSession.Profile.NO_DEBUFF,false,"",0));
                DuelClient a=clients.get(0),b=clients.get(1);waitFor(()->a.state()!=null && b.state()!=null,3000);
                a.operation(DuelSession.Op.READY,0,0);b.operation(DuelSession.Op.READY,0,0);waitFor(()->a.state().phase()==DuelSession.Phase.WARMUP,3000);
                a.operation(DuelSession.Op.FORFEIT,0,0);waitFor(()->a.state().phase()==DuelSession.Phase.RESULT,3000);
                String match=a.match();a.operation(DuelSession.Op.QUEUE,0,0);waitFor(()->a.notice().contains("queue full"),3000);
                assertEquals(match,a.match());assertTrue(server.healthy());assertTrue(b.connected());assertEquals(DuelSession.Phase.RESULT,b.state().phase());
            }finally{clients.forEach(DuelClient::close);}
        }
    }
    @Test void missedSplashSpendsThePotWithoutHealingARealClient() throws Exception {
        var tls=SecureTransport.server(temporary.resolve("missed-pot-tls"));
        try(var server=new DuelServer(tls.context(),InetAddress.getLoopbackAddress(),0,false);
            var a=new DuelClient("127.0.0.1",server.port(),tls.fingerprint(),DuelSession.Profile.NO_DEBUFF,false,"",0);
            var b=new DuelClient("127.0.0.1",server.port(),tls.fingerprint(),DuelSession.Profile.NO_DEBUFF,false,"",0)) {
            waitFor(()->a.state()!=null && b.state()!=null,2000);a.operation(DuelSession.Op.READY,0,0);b.operation(DuelSession.Op.READY,0,0);
            waitFor(()->a.state().phase()==DuelSession.Phase.LIVE && b.state().phase()==DuelSession.Phase.LIVE,5000);
            a.input(1,0,0,0,-89,false,false,false,true,false,2);
            waitFor(()->a.state().fighters().get(0).body().health()==18 && a.state().projectiles().isEmpty(),1500);
            a.input(2,0,0,0,0,false,false,false,true,false,1);
            waitFor(()->a.state().fighters().get(0).score().potsUsed()==1 && a.state().projectiles().isEmpty(),2000);
            assertEquals(18,a.state().fighters().get(0).body().health());assertEquals(20,b.state().fighters().get(1).body().health());
            assertEquals(0,a.state().fighters().get(0).body().inventory().get(1).count());assertTrue(server.healthy());
        }
    }
    @Test void offlineTargetUsesRealReadyResultAndRematchFlow() throws Exception {
        var tls=SecureTransport.server(temporary.resolve("offline-target-tls"));
        try(var server=new DuelServer(tls.context(),InetAddress.getLoopbackAddress(),0,true);
            var a=new DuelClient("127.0.0.1",server.port(),tls.fingerprint(),DuelSession.Profile.DEBUFF,false,"",0)) {
            waitFor(()->a.state()!=null && a.state().fighters().get(1).ready(),3000);
            assertEquals(DuelSession.Phase.READY,a.state().phase());long epoch=a.state().epoch();a.operation(DuelSession.Op.READY,0,0);
            waitFor(()->a.state().phase()==DuelSession.Phase.LIVE,5000);
            assertEquals(new dev.jayms.combat.Vec3(0,1,-28),a.state().fighters().get(0).body().pose().feet());
            a.operation(DuelSession.Op.FORFEIT,0,0);waitFor(()->a.state().phase()==DuelSession.Phase.RESULT,2000);
            assertTrue(a.state().result().contains("B wins"));a.operation(DuelSession.Op.READY,0,0);
            waitFor(()->a.state().phase()==DuelSession.Phase.LIVE && a.state().epoch()>epoch+1,5000);assertTrue(server.healthy());
            assertEquals(20,a.state().fighters().get(0).body().health());assertTrue(a.state().fighters().get(0).body().effects().isEmpty());
        }
    }
}
