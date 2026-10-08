package dev.jayms.net.mobile;

import dev.jayms.net.*;
import dev.jayms.net.city.GameConfig;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/** Isolated test-only worlds and in-memory accounts. Never reads the deployed game profile. */
public final class MobileFixtureHost implements AutoCloseable {
    public final MultiplayerServer sandbox,city;
    public final MobileGateway gateway;
    public final String fingerprint;
    private final List<Thread> threads=new ArrayList<>();
    public MobileFixtureHost(Path root)throws Exception{
        Files.createDirectories(root);
        var identity=SecureTransport.server(root.resolve("synthetic-tls"));fingerprint=identity.fingerprint();
        var accounts=new AccountStore(null);accounts.register("ios_fixture","fixture-password-123".toCharArray());
        sandbox=new MultiplayerServer("127.0.0.1",0,root.resolve("sandbox.dat"),accounts,identity.context(),Terrain.DEFAULT_SEED,GameConfig.sandbox());
        city=new MultiplayerServer("127.0.0.1",0,root.resolve("city.dat"),accounts,identity.context(),Terrain.DEFAULT_SEED,GameConfig.cityGame());
        for(var server:List.of(sandbox,city)){
            var thread=new Thread(()->{try{server.run();}catch(java.io.IOException ignored){/* Closed by isolated test cleanup. */}},"synthetic-mobile-server");
            thread.setDaemon(true);thread.start();threads.add(thread);
        }
        gateway=new MobileGateway(0,new MobileGateway.Target("127.0.0.1",sandbox.port(),fingerprint),new MobileGateway.Target("127.0.0.1",city.port(),fingerprint),target->target.port()==city.port()?city.transportFailure():sandbox.transportFailure(),target->target.port()==city.port()?city.cityCommandStage():sandbox.cityCommandStage());gateway.start();
    }
    public String url(){return "http://127.0.0.1:"+gateway.port();}
    @Override public void close()throws Exception{gateway.close();sandbox.close();city.close();for(var thread:threads)thread.join(2000);}
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("Usage: NEW_PROFILE_DIRECTORY READY_JSON");
        Path root=Path.of(args[0]);if(Files.exists(root))throw new IllegalArgumentException("Synthetic profile must be fresh");
        var host=new MobileFixtureHost(root);Runtime.getRuntime().addShutdownHook(new Thread(()->{try{host.close();}catch(Exception ignored){}}));
        Files.writeString(Path.of(args[1]),Json.write(Map.of("gateway",host.url(),"application","voxel-one","profile","synthetic")));
        new CountDownLatch(1).await();
    }
}
