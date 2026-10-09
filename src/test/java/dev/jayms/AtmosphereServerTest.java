package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.net.atmosphere.AtmosphereConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereServerTest {
    @TempDir Path temp;
    @Test void restartAndLateJoinKeepWorldProfileAndFixedClock()throws Exception {
        var identity=SecureTransport.server(temp.resolve("synthetic-tls"));
        var accounts=new AccountStore(temp.resolve("synthetic-accounts"));
        Path save=temp.resolve("synthetic-world.dat");
        var expected=new GameConfig(false,false,700,19,AtmosphereConfig.hazy());
        for(int pass=0;pass<2;pass++) {
            var server=new MultiplayerServer("127.0.0.1",0,save,accounts,identity.context(),42,pass==0?expected:GameConfig.sandbox());
            Thread worker=new Thread(()->{try{server.run();}catch(Exception e){throw new RuntimeException(e);}});worker.start();
            try {
                for(int join=0;join<2;join++)try(var client=new MultiplayerClient("127.0.0.1",server.port(),"atm_"+pass+"_"+join,"synthetic-test-only".toCharArray(),true,identity.fingerprint())) {
                    assertEquals(expected,client.city.config());
                }
            } finally{server.close();worker.join(5000);assertFalse(worker.isAlive());}
        }
    }
}
