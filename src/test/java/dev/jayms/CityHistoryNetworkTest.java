package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CityHistoryNetworkTest {
    @TempDir Path temp;
    void until(BooleanSupplier test)throws Exception {long deadline=System.nanoTime()+10_000_000_000L;while(!test.getAsBoolean()){if(System.nanoTime()>deadline)fail("History network timeout");Thread.sleep(15);}}
    @Test void twoAuthenticatedClientsFixedRangeStaleReplyAndReconnect()throws Exception {
        var ground=new CityTest.Ground();var clock=new GameConfig(true,true,60,0);
        var simulation=new CitySimulation(clock,ground,ground.terrain,null);simulation.collectHistory();
        for(int i=0;i<60;i++)simulation.advance(1);
        Path save=temp.resolve("world.dat");simulation.save(save.resolveSibling("world.dat.city"));
        var tls=SecureTransport.server(temp.resolve("tls"));var accounts=new AccountStore(temp.resolve("accounts"));
        accounts.register("historya","synthetic-test-a".toCharArray());accounts.register("historyb","synthetic-test-b".toCharArray());
        try(var server=new MultiplayerServer("127.0.0.1",0,save,accounts,tls.context(),Terrain.DEFAULT_SEED,clock)) {
            Thread thread=new Thread(()->{try{server.run();}catch(Exception e){throw new RuntimeException(e);}});thread.start();
            Thread.sleep(700); // The server collects while no clients or dashboards are connected.
            UUID identity;
            try(var a=new MultiplayerClient("127.0.0.1",server.port(),"historya","synthetic-test-a".toCharArray(),false,tls.fingerprint());
                var b=new MultiplayerClient("127.0.0.1",server.port(),"historyb","synthetic-test-b".toCharArray(),false,tls.fingerprint())) {
                var request=new HistoryWire.Request(null,1,List.of("population.total"),0,30,1,1000);
                assertTrue(a.historyQuery(request));assertTrue(b.historyQuery(request));
                until(()->{a.poll();b.poll();return a.historyReply!=null&&b.historyReply!=null;});
                assertEquals(a.historyReply.series(),b.historyReply.series());assertEquals(a.historyReply.city(),b.historyReply.city());identity=a.historyReply.city();
                assertEquals(simulation.history.identity(),identity);
                Thread.sleep(300);
                assertTrue(a.historyQuery(new HistoryWire.Request(identity,2,List.of("finance.treasury","finance.spending"),0,30,1,1000)));
                until(()->{a.poll();return !a.historyError.isEmpty();});assertTrue(a.historyError.contains("Incompatible"));assertTrue(a.connected());
                Thread.sleep(300);
                assertTrue(a.historyQuery(new HistoryWire.Request(identity,3,List.of("population.total"),0,30,1,1000)));
                Thread.sleep(350);
                assertTrue(a.historyQuery(new HistoryWire.Request(identity,4,List.of("population.total"),0,30,1,1000)));
                until(()->{a.poll();return a.historyReply!=null&&a.historyReply.request()==4;});
                assertEquals(4,a.historyResponseRequest);
                assertEquals(b.historyReply.series(),a.historyReply.series());
            }
            Thread.sleep(700);
            try(var again=new MultiplayerClient("127.0.0.1",server.port(),"historya","synthetic-test-a".toCharArray(),false,tls.fingerprint())) {
                assertTrue(again.historyQuery(new HistoryWire.Request(identity,5,List.of("population.total"),0,30,1,1000)));
                until(()->{again.poll();return again.historyReply!=null;});assertEquals(identity,again.historyReply.city());assertTrue(again.historyReply.latest()>60);
            }
        }
    }
}
