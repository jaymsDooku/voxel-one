package dev.jayms.combat.hcf;

import dev.jayms.combat.*;
import dev.jayms.combat.duel.*;
import dev.jayms.net.SecureTransport;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HcfTransportTest {
    @TempDir Path directory;
    static char[] password(){byte[] b=new byte[16];new SecureRandom().nextBytes(b);return HexFormat.of().formatHex(b).toCharArray();}
    static void await(BooleanSupplier condition,long millis) throws Exception {
        long end=System.nanoTime()+millis*1_000_000;while(!condition.getAsBoolean() && System.nanoTime()<end)Thread.sleep(15);assertTrue(condition.getAsBoolean(),"HCF authoritative state timeout");
    }
    @Test void wireVersionInputBoundsStateRoundTripAndPracticeFence() throws Exception {
        var c=HcfCommand.operation(1,1,HcfCommand.Op.DEPLOY,0);byte[] frame=HcfWire.command(c);var in=new DataInputStream(new ByteArrayInputStream(frame));in.readByte();assertEquals(c,HcfWire.command(in));DuelWire.end(in);
        frame[25]=(byte)255;var bad=new DataInputStream(new ByteArrayInputStream(frame));bad.readByte();assertThrows(IOException.class,()->HcfWire.command(bad));
        assertThrows(IllegalArgumentException.class,()->new HcfCommand(1,1,0,HcfCommand.Op.INPUT,Double.NaN,0,0,0,false,false,false,false,false,0,0));
        char[] secret=password();byte[] hello=HcfWire.hello(new HcfWire.Hello("player",secret,false,0));hello[8]=99;
        var h=new DataInputStream(new ByteArrayInputStream(hello));h.readByte();assertThrows(IOException.class,()->HcfWire.hello(h));Arrays.fill(secret,'\0');
        var s=new HcfSession(new HcfSessionTest.Floor(),new HcfStore(null),HcfRules.initial(),()->1000);s.connect("player",0);
        var encoded=HcfWire.state(s.state());var state=new DataInputStream(new ByteArrayInputStream(encoded));state.readByte();assertEquals(s.state(),HcfWire.state(state));DuelWire.end(state);
        var practice=new DataInputStream(new ByteArrayInputStream(DuelWire.hello(new DuelWire.Hello(DuelSession.Profile.NO_DEBUFF,false,"",""))));practice.readByte();assertThrows(IOException.class,()->HcfWire.hello(practice));
    }
    @Test void realTlsAccountReconnectDeathbanWrongPasswordAndRestart() throws Exception {
        var tls=SecureTransport.server(directory.resolve("synthetic-tls"));char[] aSecret=password(),bSecret=password();Path data=directory.resolve("server");
        int port;
        try(var server=new HcfServer(tls.context(),InetAddress.getLoopbackAddress(),0,data,HcfRules.initial(),System::currentTimeMillis,HcfSession.Phase.ACTIVE,false);
            var a=new HcfClient("127.0.0.1",server.port(),tls.fingerprint(),"blueone",aSecret,true,0)) {
            port=server.port();Thread.sleep(220);
            try(var b=new HcfClient("127.0.0.1",port,tls.fingerprint(),"goldone",bSecret,true,1)) {
                await(()->a.self()!=null && b.self()!=null,3000);assertEquals(2,a.state().members().size());
                assertThrows(IOException.class,()->new HcfServer(tls.context(),InetAddress.getLoopbackAddress(),0,data,HcfRules.initial(),System::currentTimeMillis,null,false));
                a.operation(HcfCommand.Op.DEPLOY,0);b.operation(HcfCommand.Op.DEPLOY,0);await(()->a.self().deployed() && b.self().deployed(),3000);
                // Move through the normal input surface along clear central lane.
                long frame=0;
                while(a.self().body().pose().feet().distance(b.self().body().pose().feet())>2.8 && frame<240) {
                    Vec3 ap=a.self().body().pose().feet(),bp=b.self().body().pose().feet();double yaw=Math.toDegrees(Math.atan2(bp.z()-ap.z(),bp.x()-ap.x()));
                    a.input(++frame,1,0,yaw,0,false,false,false,false,false,0);b.input(frame,1,0,yaw+180,0,false,false,false,false,false,0);Thread.sleep(50);
                }
                a.input(++frame,0,0,90,0,false,false,false,false,false,0);b.input(frame,0,0,-90,0,false,false,false,false,false,0);Thread.sleep(300);
                Vec3 ap=a.self().body().pose().feet(),bp=b.self().body().pose().feet();double yaw=Math.toDegrees(Math.atan2(bp.z()-ap.z(),bp.x()-ap.x()));
                a.input(++frame,0,0,yaw,0,false,false,true,false,false,0);await(()->b.self().tagUntil()>b.state().tick(),3000);
                b.operation(HcfCommand.Op.HOME,0);Thread.sleep(120);assertTrue(b.self().deployed());
                b.interruptConnection();Thread.sleep(300);b.reconnect();await(()->b.self()!=null && b.self().deathbanUntil()>b.state().now(),3000);
                assertFalse(b.self().body().alive());assertEquals(2,b.state().teams().get(1).dtr());b.operation(HcfCommand.Op.DEPLOY,0);Thread.sleep(120);assertFalse(b.self().deployed());
                Thread.sleep(220);char[] wrong=password();assertThrows(IOException.class,()->new HcfClient("127.0.0.1",port,tls.fingerprint(),"goldone",wrong,false,1));Arrays.fill(wrong,'\0');assertTrue(server.healthy());
            }
        }
        try(var restarted=new HcfServer(tls.context(),InetAddress.getLoopbackAddress(),0,data,HcfRules.initial(),System::currentTimeMillis,null,false);
            var b=new HcfClient("127.0.0.1",restarted.port(),tls.fingerprint(),"goldone",bSecret,false,0)) {
            await(()->b.self()!=null,3000);assertEquals(1,b.self().team());assertTrue(b.self().deathbanUntil()>b.state().now());assertFalse(b.self().deployed());assertEquals(2,b.state().teams().get(1).dtr());assertTrue(restarted.healthy());
        }finally{Arrays.fill(aSecret,'\0');Arrays.fill(bSecret,'\0');}
    }
    private void cli(Path data,String... flags) throws Exception {
        int port;try(var reservation=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){port=reservation.getLocalPort();}
        String javaBinary=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        var args=new ArrayList<String>(List.of(javaBinary,"-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),HcfServer.class.getName(),"--bind","127.0.0.1","--port",Integer.toString(port),"--directory",data.toString()));args.addAll(List.of(flags));
        var process=new ProcessBuilder(args).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            final int endpoint=port;
            await(()->{try(var socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",endpoint),100);return true;}catch(IOException e){return false;}},15000);
            assertTrue(process.isAlive(),"HCF CLI remains running after admission");
        }finally {process.destroy();if(!process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();assertTrue(process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS));}}
    }
    @Test void actualAdminCliPersistsPhasesAndExplicitSeasonReset() throws Exception {
        Path data=directory.resolve("cli-server");cli(data,"--phase","active","--deathban-seconds","7");var store=new HcfStore(data.resolve("season.hcf"));assertEquals(1,store.load().phase());
        cli(data,"--phase","ended");assertEquals(2,store.load().phase());assertEquals(1,store.load().season());
        cli(data,"--reset-season");assertEquals(2,store.load().season());assertEquals(0,store.load().phase());assertTrue(store.load().members().isEmpty());assertTrue(store.load().drops().isEmpty());
    }

}
