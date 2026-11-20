package dev.jayms.combat.duel;

import dev.jayms.combat.arena.BasaltCourtyard;
import dev.jayms.net.SecureTransport;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** Dedicated opt-in practice service. Each room owns its world and simulation. No account saves. */
public final class DuelServer implements AutoCloseable {
    public static final int MAX_ROOMS=8,MAX_CONNECTIONS=32;
    private final SSLServerSocket listener;
    private final ScheduledExecutorService clock=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"practice-tick");t.setDaemon(true);return t;});
    private final Set<Peer> peers=ConcurrentHashMap.newKeySet();
    private final List<Room> rooms=new ArrayList<>();
    private final boolean drill;
    private volatile boolean closed;
    private volatile boolean healthy=true;
    private final Thread acceptor;
    private final SecureRandom random=new SecureRandom();
    private final class Room implements AutoCloseable {
        final String id=key();
        final BasaltCourtyard.Fixture arena=BasaltCourtyard.generate();
        final DuelSession session;
        final String[] tokens={key(),key()};
        final Peer[] players=new Peer[2];
        final long[] nextSequence={1,1};
        final Set<Peer> spectators=new HashSet<>();
        long idle;
        Room(DuelSession.Profile profile) {
            session=new DuelSession(profile,new CourtyardCombatWorld(arena));
            if(drill) {session.connected(1,true);session.submit(1,DuelSession.Command.operation(session.epoch(),nextSequence[1]++,DuelSession.Op.READY,0,0));}
        }
        public void close() {try{arena.close();}catch(Exception ignored){}}
    }
    public DuelServer(SSLContext tls,InetAddress bind,int port,boolean drill) throws IOException {
        this.drill=drill;
        listener=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket(port,32,bind);
        listener.setEnabledProtocols(new String[]{"TLSv1.3","TLSv1.2"});
        acceptor=new Thread(this::accept,"practice-accept");acceptor.setDaemon(true);acceptor.start();
        clock.scheduleAtFixedRate(this::step,0,50,TimeUnit.MILLISECONDS);
    }
    public int port() {return listener.getLocalPort();}
    public boolean healthy() {return healthy;}
    private String key() {byte[] bytes=new byte[24];random.nextBytes(bytes);return HexFormat.of().formatHex(bytes);}
    private void accept() {
        while(!closed) try {
            var socket=(SSLSocket)listener.accept();
            if(peers.size()>=MAX_CONNECTIONS) {socket.close();continue;}
            socket.setSoTimeout(10000);socket.setTcpNoDelay(true);
            var peer=new Peer(socket);peers.add(peer);peer.start();
        }catch(IOException e){if(!closed) healthy=false;}
    }
    private final class Peer {
        final SSLSocket socket;
        final ArrayBlockingQueue<DuelSession.Command> inbox=new ArrayBlockingQueue<>(32);
        final ArrayBlockingQueue<byte[]> output=new ArrayBlockingQueue<>(4);
        volatile boolean dead;
        Room room;int side=-1;
        Peer(SSLSocket socket){this.socket=socket;}
        void start() {
            Thread writer=new Thread(()->{
                try(var out=new DataOutputStream(socket.getOutputStream())) {
                    while(!dead) DuelWire.send(out,output.take());
                }catch(IOException|InterruptedException e){Thread.currentThread().interrupt();}finally{stop();}
            },"practice-writer");writer.setDaemon(true);writer.start();
            Thread reader=new Thread(()->{
                try(var in=new DataInputStream(socket.getInputStream())) {
                    var frame=DuelWire.read(in);if(frame.readUnsignedByte()!=DuelWire.HELLO) throw new IOException("Practice hello required");
                    var hello=DuelWire.hello(frame);DuelWire.end(frame);join(this,hello);
                    while(!dead) {
                        frame=DuelWire.read(in);if(frame.readUnsignedByte()!=DuelWire.COMMAND) throw new IOException("Practice intent required");
                        var c=DuelWire.command(frame);DuelWire.end(frame);
                        if(!inbox.offer(c)) throw new IOException("Practice input queue full");
                    }
                }catch(IOException|RuntimeException e){/* No credentials, commands or runtime logs leave this process. */}
                finally {stop();writer.interrupt();}
            },"practice-reader");reader.setDaemon(true);reader.start();
        }
        void offer(byte[] frame) {
            // Keep handshake/control frames and the latest correction. Fixture creation or
            // a slow socket may leave clock debt; stale presentation frames must not evict
            // the welcome or kill a newly connected player during that catch-up burst.
            output.removeIf(queued->queued[0]==DuelWire.STATE);
            if(!output.offer(frame)) stop();
        }
        void stop() {dead=true;try{socket.close();}catch(IOException ignored){}peers.remove(this);}
    }
    private synchronized void join(Peer peer,DuelWire.Hello hello) throws IOException {
        if(closed) throw new IOException("Practice service closed");
        Room room=null;int side=-1;
        if(hello.spectator()) {
            room=rooms.stream().filter(r->r.id.equals(hello.match())).findFirst().orElseThrow(()->new IOException("Match unavailable"));
            if(room.spectators.size()>=8 || !hello.resume().isEmpty()) throw new IOException("Spectator bound");
            room.spectators.add(peer);
        } else if(!hello.resume().isEmpty()) {
            room=rooms.stream().filter(r->r.id.equals(hello.match())).findFirst().orElseThrow(()->new IOException("Match expired"));
            for(int i=0;i<2;i++) if(java.security.MessageDigest.isEqual(room.tokens[i].getBytes(java.nio.charset.StandardCharsets.US_ASCII),hello.resume().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) side=i;
            if(side<0 || (drill && side==1) || room.session.state().profile()!=hello.profile()) throw new IOException("Reconnect unavailable");
            if(room.players[side]!=null) room.players[side].stop();
        } else {
            for(Room r:rooms) if(r.session.state().profile()==hello.profile() && r.session.phase()==DuelSession.Phase.QUEUE && r.players[1]==null && !drill) {room=r;side=1;break;}
            if(room==null) {
                if(rooms.size()>=MAX_ROOMS) throw new IOException("Practice queue full");
                room=new Room(hello.profile());rooms.add(room);side=0;
            }
        }
        peer.room=room;peer.side=side;
        if(side>=0) {room.players[side]=peer;room.session.connected(side,true);}
        peer.offer(DuelWire.welcome(new DuelWire.Welcome(room.id,side,side<0?"":room.tokens[side],side<0?1:room.nextSequence[side])));
    }
    private synchronized void step() {
        if(closed) return;
        try {
            for(Room room:List.copyOf(rooms)) {
                boolean any=false;
                for(int i=0;i<2;i++) {
                    Peer p=room.players[i];if(p==null) continue;
                    if(p.dead) {room.session.connected(i,false);room.players[i]=null;continue;}
                    any=true;
                    for(int n=0;n<4;n++) {
                        var command=p.inbox.poll();if(command==null) break;
                        room.nextSequence[i]=Math.max(room.nextSequence[i],command.sequence()+1);
                        if(command.op()==DuelSession.Op.QUEUE && room.session.phase()==DuelSession.Phase.RESULT && command.epoch()==room.session.epoch()) {
                            if(room.session.submit(i,command)) {
                                boolean waiting=rooms.stream().anyMatch(r->r!=room && r.session.state().profile()==room.session.state().profile() && r.session.phase()==DuelSession.Phase.QUEUE && r.players[1]==null && !drill);
                                if(rooms.size()>=MAX_ROOMS && !waiting) {
                                    p.offer(DuelWire.frame(DuelWire.ERROR,o->o.writeUTF("Practice queue full. Retry shortly.")));break;
                                }
                                room.session.connected(i,false);room.players[i]=null;room.tokens[i]=key();p.inbox.clear();
                                try{join(p,new DuelWire.Hello(room.session.state().profile(),false,"",""));}catch(IOException e){p.stop();}
                            }
                            break;
                        }
                        room.session.submit(i,command);
                    }
                }
                // Offline drill is a visible target using the same server state and finite kit.
                if(drill && (room.session.phase()==DuelSession.Phase.READY || room.session.phase()==DuelSession.Phase.RESULT))
                    room.session.submit(1,DuelSession.Command.operation(room.session.epoch(),room.nextSequence[1]++,DuelSession.Op.READY,0,0));
                room.session.step();
                byte[] frame=DuelWire.state(room.session.state());
                for(Peer p:room.players) if(p!=null && !p.dead) p.offer(frame);
                room.spectators.removeIf(p->p.dead);
                for(Peer p:room.spectators) {any=true;p.inbox.clear();p.offer(frame);}
                if(any) room.idle=0;else room.idle++;
            }
            rooms.removeIf(room->{if(room.idle>1200){room.close();return true;}return false;});
        }catch(Exception e){healthy=false;/* Fail closed. No continued unqualified match ticks. */close();}
    }
    @Override public synchronized void close() {
        if(closed) return;closed=true;clock.shutdownNow();
        try{listener.close();}catch(IOException ignored){}
        peers.forEach(Peer::stop);rooms.forEach(Room::close);rooms.clear();
    }
    public static void main(String[] args) throws Exception {
        int port=DuelWire.PORT;Path directory=Path.of("runtime/practice/tls");String bind="0.0.0.0";
        for(int i=0;i<args.length;i++) switch(args[i]) {
            case "--port" -> port=Integer.parseInt(args[++i]);
            case "--tls-directory" -> directory=Path.of(args[++i]);
            case "--bind" -> bind=args[++i];
            default -> throw new IllegalArgumentException("Usage: DuelServer --port PORT --bind ADDRESS --tls-directory PATH");
        }
        if(port<1 || port>65535) throw new IllegalArgumentException("Port");
        var identity=SecureTransport.server(directory);
        try(var server=new DuelServer(identity.context(),InetAddress.getByName(bind),port,false)) {
            System.out.println("Practice server port "+server.port()+"; certificate SHA-256 "+identity.fingerprint());
            while(server.healthy() && !server.closed) Thread.sleep(1000);
        }
    }
}
