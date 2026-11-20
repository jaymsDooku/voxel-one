package dev.jayms.combat.hcf;

import dev.jayms.combat.arena.BasaltCourtyard;
import dev.jayms.combat.duel.*;
import dev.jayms.net.*;
import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;
import javax.net.ssl.*;

/** Dedicated opt-in HCF service. Own account namespace, file lock, world, state and port. */
public final class HcfServer implements AutoCloseable {
    private final SSLServerSocket listener;
    private final FileChannel lockChannel;private final FileLock lock;
    private final BasaltCourtyard.Fixture arena;
    private final HcfSession session;
    private final AccountStore accounts;
    private final Set<Peer> peers=ConcurrentHashMap.newKeySet();
    private final Map<String,Peer> online=new HashMap<>();
    private final ScheduledExecutorService clock=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"hcf-tick");t.setDaemon(true);return t;});
    private final Map<InetAddress,Long> attempts=new HashMap<>();
    private volatile boolean closed,healthy=true;
    public HcfServer(SSLContext tls,InetAddress bind,int port,Path directory,HcfRules rules,LongSupplier time,HcfSession.Phase phase,boolean reset) throws IOException {
        Files.createDirectories(directory);
        lockChannel=FileChannel.open(directory.resolve("hcf.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        FileLock acquired;
        try{acquired=lockChannel.tryLock();}catch(OverlappingFileLockException e){lockChannel.close();throw new IOException("HCF state already in use",e);}
        if(acquired==null){lockChannel.close();throw new IOException("HCF state already in use");}lock=acquired;
        BasaltCourtyard.Fixture created=null;SSLServerSocket socket=null;
        try {
            var store=new HcfStore(directory.resolve("season.hcf"));var saved=store.load();
            if(reset)saved=saved.reset();if(phase!=null)saved=saved.phase(phase);if(reset || phase!=null)store.save(saved);
            accounts=new AccountStore(directory.resolve("accounts.db"));
            created=BasaltCourtyard.generate();arena=created;
            session=new HcfSession(new CourtyardCombatWorld(arena),store,rules,time);
            socket=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket(port,32,bind);listener=socket;
            listener.setEnabledProtocols(new String[]{"TLSv1.3","TLSv1.2"});
        }catch(IOException|RuntimeException e){if(socket!=null)socket.close();if(created!=null)try{created.close();}catch(Exception ignored){}lock.release();lockChannel.close();throw e;}
        Thread acceptor=new Thread(this::accept,"hcf-accept");acceptor.setDaemon(true);acceptor.start();
        clock.scheduleAtFixedRate(this::step,0,50,TimeUnit.MILLISECONDS);
    }
    public int port(){return listener.getLocalPort();}
    public boolean healthy(){return healthy;}
    public synchronized HcfSession.State state(){return session.state();}
    private synchronized boolean admission(InetAddress address) {
        long now=System.nanoTime();Long next=attempts.get(address);
        if(next!=null && next>now)return false;
        attempts.entrySet().removeIf(e->e.getValue()<now);if(attempts.size()>=64)return false;
        attempts.put(address,now+200_000_000L);return true;
    }
    private void accept() {
        while(!closed)try {
            var socket=(SSLSocket)listener.accept();
            if(peers.size()>=32 || !admission(socket.getInetAddress())){socket.close();continue;}
            socket.setSoTimeout(10000);socket.setTcpNoDelay(true);Peer p=new Peer(socket);peers.add(p);p.start();
        }catch(IOException e){if(!closed){healthy=false;close();}}
    }
    private final class Peer {
        final SSLSocket socket;final ArrayBlockingQueue<HcfCommand> inbox=new ArrayBlockingQueue<>(32);
        final ArrayBlockingQueue<byte[]> output=new ArrayBlockingQueue<>(4);volatile boolean dead;String id;
        Peer(SSLSocket socket){this.socket=socket;}
        void start() {
            Thread writer=new Thread(()->{
                try(var out=new DataOutputStream(socket.getOutputStream())) {while(!dead)DuelWire.send(out,output.take());}
                catch(IOException|InterruptedException ignored){}finally{stop();}
            },"hcf-writer");writer.setDaemon(true);writer.start();
            Thread reader=new Thread(()->{
                HcfWire.Hello hello=null;
                try(var in=new DataInputStream(socket.getInputStream())) {
                    var frame=DuelWire.read(in);if(frame.readUnsignedByte()!=HcfWire.HELLO)throw new IOException("HCF hello required");
                    hello=HcfWire.hello(frame);DuelWire.end(frame);
                    String name=AccountStore.normalize(hello.name());
                    boolean authenticated=hello.register()?register(name,hello.password()):accounts.authenticate(name,hello.password());
                    Arrays.fill(hello.password(),'\0');if(!authenticated)throw new IOException("HCF sign-in rejected");join(this,name,hello.team());
                    while(!dead){frame=DuelWire.read(in);if(frame.readUnsignedByte()!=HcfWire.COMMAND)throw new IOException("HCF input required");var command=HcfWire.command(frame);DuelWire.end(frame);if(!inbox.offer(command))throw new IOException("HCF input queue full");}
                }catch(IOException|RuntimeException ignored){/* Never expose account secrets or runtime input. */}
                finally{if(hello!=null)Arrays.fill(hello.password(),'\0');stop();writer.interrupt();}
            },"hcf-reader");reader.setDaemon(true);reader.start();
        }
        void offer(byte[] frame){output.removeIf(b->b[0]==HcfWire.STATE);if(!output.offer(frame))stop();}
        void stop(){dead=true;try{socket.close();}catch(IOException ignored){}peers.remove(this);}
    }
    private boolean register(String id,char[] password) throws IOException {
        synchronized(this){if(closed || (!session.known(id) && session.rosterSize()>=16))return false;}
        // Password derivation and the account file never hold the simulation's clock lock.
        if(accounts.contains(id))return false;
        return accounts.register(id,password);
    }
    private synchronized void join(Peer p,String id,int team) throws IOException {
        if(closed)throw new IOException("HCF service closed");Peer old=online.get(id);
        if(old!=null && !old.dead)throw new IOException("HCF account already online");
        if(old!=null){session.disconnect(id);online.remove(id);}
        session.connect(id,team);p.id=id;online.put(id,p);
        p.offer(HcfWire.welcome(new HcfWire.Welcome(id,session.season(),nextSequence(id))));
    }
    private long nextSequence(String id) {return session.sequence(id)+1;}
    private synchronized void step() {
        if(closed)return;
        try {
            session.beginBatch();
            for(Peer p:List.copyOf(online.values())) {
                if(p.dead){session.disconnect(p.id);online.remove(p.id);continue;}
                for(int i=0;i<4;i++) {
                    var command=p.inbox.poll();if(command==null)break;
                    session.submit(p.id,command);
                    if(command.op()==HcfCommand.Op.SAFE_LOGOUT && !session.connected(p.id)){p.stop();online.remove(p.id);break;}
                }
            }
            session.step();session.commitBatch(); // Durable before any new authoritative frame leaves the server.
            byte[] frame=HcfWire.state(session.state());for(Peer p:online.values())p.offer(frame);
        }catch(IOException|RuntimeException e){healthy=false;close();}
    }
    @Override public synchronized void close() {
        if(closed)return;closed=true;clock.shutdownNow();try{listener.close();}catch(IOException ignored){}
        peers.forEach(Peer::stop);online.clear();try{arena.close();}catch(Exception ignored){}try{lock.release();lockChannel.close();}catch(IOException ignored){}
    }
    public static void main(String[] args) throws Exception {
        int port=HcfWire.PORT;String bind="0.0.0.0";Path directory=Path.of("runtime/hcf");HcfSession.Phase phase=null;boolean reset=false;
        long ban=HcfRules.initial().deathbanMillis();
        for(int i=0;i<args.length;i++)switch(args[i]) {
            case "--port" -> port=Integer.parseInt(args[++i]);case "--bind" -> bind=args[++i];case "--directory" -> directory=Path.of(args[++i]);
            case "--phase" -> phase=HcfSession.Phase.valueOf(args[++i].toUpperCase(Locale.ROOT));case "--reset-season" -> reset=true;
            case "--deathban-seconds" -> ban=Math.multiplyExact(Long.parseLong(args[++i]),1000);
            default -> throw new IllegalArgumentException("HcfServer --port PORT --bind ADDRESS --directory PATH [--phase opening|active|ended] [--reset-season] [--deathban-seconds SECONDS]");
        }
        if(port<1 || port>65535)throw new IllegalArgumentException("HCF port");
        var r=HcfRules.initial();var rules=new HcfRules(r.warmupTicks(),r.combatTagTicks(),r.spawnTicks(),r.rogueCooldownTicks(),r.markTicks(),r.auraTicks(),r.captureTicks(),ban,r.dtrFreezeMillis(),r.dtrRegenMillis(),r.restockTicks());
        var tls=SecureTransport.server(directory.resolve("tls"));
        try(var server=new HcfServer(tls.context(),InetAddress.getByName(bind),port,directory,rules,System::currentTimeMillis,phase,reset)) {
            System.out.println("HCF server port "+server.port()+"; certificate SHA-256 "+tls.fingerprint());
            while(server.healthy() && !server.closed)Thread.sleep(1000);
            if(!server.healthy())throw new IOException("HCF stopped: state or simulation failure");
        }
    }
}
