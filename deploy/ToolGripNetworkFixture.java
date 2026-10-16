import dev.jayms.net.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.net.model.ModelLibrary;
import java.io.*;
import java.nio.file.*;

/** Two synthetic loopback TLS clients. No account or TLS files leave target/. */
final class ToolGripNetworkFixture implements AutoCloseable {
    final MultiplayerServer server;
    final Thread thread;
    final MultiplayerClient subject,observer;
    volatile Protocol.Pose latest;
    volatile boolean stopped;
    ToolGripNetworkFixture() throws Exception {
        Path dir=Files.createTempDirectory(Path.of("target/tmp"),"tool-network-");
        var tls=SecureTransport.server(dir.resolve("tls"));
        var accounts=new AccountStore(dir.resolve("accounts.db"));
        accounts.register("gripsubject","synthetic-grip-subject".toCharArray());
        accounts.register("gripobserver","synthetic-grip-observer".toCharArray());
        Path save=dir.resolve("world.dat");
        try(var data=new DataOutputStream(Files.newOutputStream(save))) {
            data.writeInt(Protocol.MAGIC);data.writeInt(-8);data.writeLong(Terrain.DEFAULT_SEED);
            data.writeInt(Terrain.CURRENT_VERSION);new ModelLibrary().write(data);data.writeInt(0);data.writeInt(2);
            for(String name:new String[]{"gripsubject","gripobserver"}) {
                var inventory=new Inventory();for(int id=191;id<=198;id++)inventory.add(id,1);
                data.writeUTF(name);inventory.write(data);data.writeByte(20);
            }data.writeInt(0);
        }
        server=new MultiplayerServer("127.0.0.1",0,save,accounts,tls.context(),Terrain.DEFAULT_SEED,GameConfig.sandbox());
        thread=new Thread(()->{try{server.run();}catch(IOException ignored){}});thread.setDaemon(true);thread.start();
        subject=new MultiplayerClient("127.0.0.1",server.port(),"gripsubject","synthetic-grip-subject".toCharArray(),false,tls.fingerprint());
        observer=new MultiplayerClient("127.0.0.1",server.port(),"gripobserver","synthetic-grip-observer".toCharArray(),false,tls.fingerprint());
        latest=subject.spawn;
        Thread keepalive=new Thread(()->{while(!stopped){subject.move(latest);observer.move(observer.spawn);
            try{Thread.sleep(500);}catch(InterruptedException e){return;}}});keepalive.setDaemon(true);keepalive.start();
    }
    Protocol.Pose pose(int type,float progress,float phase,float gait,float yaw,float pitch,float lift,boolean placing) throws Exception {
        // Keep the render sweep below the production 120 messages/second limit.
        Thread.sleep(25);
        var s=subject.spawn;
        latest=new Protocol.Pose(subject.id,s.x(),s.y()+lift,s.z(),yaw,pitch,phase,gait,lift>0,progress,type,placing);
        subject.move(latest);
        long limit=System.nanoTime()+10_000_000_000L;
        while(System.nanoTime()<limit) {
            subject.poll();observer.poll();
            var p=observer.players.get(subject.id);
            if(p!=null&&p.heldItem()==type&&p.placingSwing()==placing&&java.lang.Math.abs(p.swingProgress()-progress)<.01f) {
                var remote=observer.remotePlayers.get(subject.id);
                if(remote!=null) return remote.sample(System.nanoTime()+100_000_000L);
            }
            Thread.sleep(5);
        }
        throw new AssertionError("Observer did not receive held/swing state: tool="+type+" progress="+progress+" placing="+placing);
    }
    public void close() throws Exception {stopped=true;subject.close();observer.close();server.close();thread.join(3000);}
}
