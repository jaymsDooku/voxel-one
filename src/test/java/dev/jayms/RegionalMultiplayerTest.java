package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Real TLS protocol test. All identities and stores are synthetic and temporary. */
class RegionalMultiplayerTest {
    @TempDir Path temp;
    static void until(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+15_000_000_000L;
        while(!condition.getAsBoolean()) {
            if(System.nanoTime()>deadline) fail("Regional network timeout");
            Thread.sleep(20);
        }
    }
    @Test void twoMillionResidentsReachLateJoinAndRestartInBoundedSnapshots() throws Exception {
        var tls=SecureTransport.server(temp.resolve("tls"));
        var accounts=new AccountStore(temp.resolve("synthetic-accounts"));
        char[] secret=UUID.randomUUID().toString().toCharArray();
        accounts.register("ScaleOne",secret);accounts.register("ScaleLate",secret);
        var ground=new CityTest.Ground();var config=GameConfig.cityGame();
        var simulation=new CitySimulation(config,ground,ground.terrain,null,ProductionCatalog.toolEra());
        simulation.command(new CityCommand(CityCommand.SETTLE_DISTRICT,1_000_000,List.of()),1,null);
        simulation.command(new CityCommand(CityCommand.SETTLE_DISTRICT,1_000_000,List.of()),1,null);
        Path save=temp.resolve("world.dat");simulation.save(save.resolveSibling("world.dat.city"));
        for(int session=0;session<2;session++) {
            try(var server=new MultiplayerServer("127.0.0.1",0,save,accounts,tls.context(),Terrain.DEFAULT_SEED,config)) {
                var failures=new java.util.concurrent.atomic.AtomicReference<Throwable>();
                var thread=new Thread(()->{ try {server.run();} catch(Throwable e){failures.set(e);} });thread.start();
                try(var first=new MultiplayerClient("127.0.0.1",server.port(),"ScaleOne",secret,false,tls.fingerprint())) {
                    assertEquals(2_000_000,first.city.population().population());
                    assertTrue(first.cityCommand(new CityCommand(CityCommand.FOCUS_DISTRICT,1,List.of())));
                    until(()->{first.poll();return first.city.population().agents().size()==64;});
                    try(var late=new MultiplayerClient("127.0.0.1",server.port(),"ScaleLate",secret,false,tls.fingerprint())) {
                        assertEquals(2_000_000,late.city.population().population());
                        assertEquals(64,late.city.population().agents().size());
                        assertEquals(first.city.population().groups().size(),late.city.population().groups().size());
                        var out=new java.io.ByteArrayOutputStream();late.city.write(new java.io.DataOutputStream(out));
                        assertTrue(out.size()<200_000,"Network frame grew with population");
                        double hunger=late.city.population().agents().get(0).hunger();
                        until(()->{first.poll();late.poll();return late.city.population().agents().get(0).hunger()!=hunger;});
                    }
                } finally {server.close();thread.join(5000);assertFalse(thread.isAlive());assertNull(failures.get());}
            }
            assertEquals(2_000_000,CitySimulation.load(save.resolveSibling("world.dat.city")).population().population());
        }
        Arrays.fill(secret,'\0');
    }
}
