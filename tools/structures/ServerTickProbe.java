import dev.jayms.net.*;
import dev.jayms.net.city.GameConfig;
import java.nio.file.*;
import java.util.Locale;

/** Real loopback server diagnostic. One synthetic client; no city/system qualification. */
public final class ServerTickProbe {
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Fresh synthetic profile and sanitized result paths required");
        Path report=Path.of(args[1]);if(Files.exists(report))throw new IllegalArgumentException("Result must not exist");
        Path folder=Path.of(args[0]);if(Files.exists(folder))throw new IllegalArgumentException("Profile must not exist");Files.createDirectories(folder);
        var tls=SecureTransport.server(folder.resolve("tls"));var accounts=new AccountStore(folder.resolve("accounts"));
        try(var server=new MultiplayerServer("127.0.0.1",0,folder.resolve("world.dat"),accounts,tls.context(),42,GameConfig.sandbox())) {
            var thread=new Thread(()->{try{server.run();}catch(Exception ignored){}});thread.setDaemon(true);thread.start();
            try(var client=new MultiplayerClient("127.0.0.1",server.port(),"TickProbe","synthetic-password".toCharArray(),true,tls.fingerprint())) {
                long end=System.nanoTime()+5_000_000_000L;
                while(System.nanoTime()<end){client.move(client.spawn);client.poll();if(!client.connected())throw new AssertionError("Synthetic client disconnected");Thread.sleep(50);}
                var idle=server.tickTiming();if(idle.totalTicks()<20||idle.failedTicks()!=0)throw new AssertionError("Missing successful real ticks");
                synchronized(server){Thread.sleep(350);} // Deliberate fixture contention, not production work.
                long before=server.tickTiming().totalTicks();end=System.nanoTime()+5_000_000_000L;
                while(server.tickTiming().totalTicks()<before+3&&System.nanoTime()<end){client.move(client.spawn);client.poll();Thread.sleep(50);}
                var contended=server.tickTiming();if(contended.totalTicks()<before+3||contended.lockWait().worstMs()<100)throw new AssertionError("Contention not observed");
                Files.writeString(report,"{\"scope\":\"One real TLS loopback client; 5 s normal observation then deliberate 350 ms authority-lock hold. Not a city/max or 20-run qualification.\",\"normal\":"+json(idle)+",\"after_synthetic_contention\":"+json(contended)+"}");
            }finally{server.close();thread.join(5000);if(thread.isAlive())throw new AssertionError("Server did not stop");}
        }
    }
    static String json(MultiplayerServer.TickTiming t){return String.format(Locale.ROOT,"{\"total_ticks\":%d,\"failed_ticks\":%d,\"window_ticks\":%d,\"window_span_ms\":%.6f,\"starts_per_second\":%.6f,\"intervals\":%s,\"lock_wait\":%s,\"work\":%s}",t.totalTicks(),t.failedTicks(),t.windowTicks(),t.windowSpanMs(),t.startsPerSecond(),json(t.intervals()),json(t.lockWait()),json(t.work()));}
    static String json(MultiplayerServer.TickDistribution d){return String.format(Locale.ROOT,"{\"samples\":%d,\"median_ms\":%.6f,\"p95_ms\":%.6f,\"worst_ms\":%.6f}",d.samples(),d.medianMs(),d.p95Ms(),d.worstMs());}
}
