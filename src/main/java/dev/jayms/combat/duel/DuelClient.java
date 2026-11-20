package dev.jayms.combat.duel;

import dev.jayms.net.SecureTransport;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Real TLS client. Reconnect capability stays in process memory, never in a save or log. */
public final class DuelClient implements AutoCloseable {
    private final String host,pin;
    private final int port,oneWayMillis;
    private final DuelSession.Profile profile;
    private final boolean spectator;
    private final DelayQueue<Delivery> outgoing=new DelayQueue<>();
    private final ArrayBlockingQueue<DuelSession.State> history=new ArrayBlockingQueue<>(128);
    private volatile Socket socket;
    private volatile DuelWire.Welcome welcome;
    private volatile DuelSession.State state;
    private volatile boolean closed,connected;
    private volatile String notice="";
    private long sequence=1;
    public DuelClient(String host,int port,String pin,DuelSession.Profile profile,boolean spectator,String match,int oneWayMillis) throws IOException {
        this.host=host;this.port=port;this.pin=pin;this.profile=profile;this.spectator=spectator;
        if(oneWayMillis<0 || oneWayMillis>250) throw new IllegalArgumentException("Latency bound");this.oneWayMillis=oneWayMillis;
        connect(match,"");
    }
    private synchronized void connect(String match,String resume) throws IOException {
        if(closed) throw new IOException("Practice client closed");
        Socket current=SecureTransport.connect(host,port,pin);socket=current;
        var in=new DataInputStream(current.getInputStream());var out=new DataOutputStream(current.getOutputStream());
        try {
            DuelWire.send(out,DuelWire.hello(new DuelWire.Hello(profile,spectator,match,resume)));
            var frame=DuelWire.read(in);if(frame.readUnsignedByte()!=DuelWire.WELCOME) throw new IOException("Practice welcome required");
            welcome=DuelWire.welcome(frame);DuelWire.end(frame);sequence=Math.max(sequence,welcome.nextSequence());connected=true;notice="";
        }catch(IOException e){disconnect(current);throw e;}
        var delivery=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"practice-delay");t.setDaemon(true);return t;});
        var pending=new java.util.concurrent.atomic.AtomicInteger();
        Thread sender=new Thread(()->{
            try {while(!closed && !current.isClosed()) {
                DuelWire.send(out,outgoing.take().bytes);
            }}catch(IOException|InterruptedException e){}finally{disconnect(current);}
        },"practice-send");sender.setDaemon(true);sender.start();
        Thread receiver=new Thread(()->{
            try {while(!closed && !current.isClosed()) {
                var f=DuelWire.read(in);int type=f.readUnsignedByte();
                if(type==DuelWire.ERROR){String text=f.readUTF();DuelWire.end(f);if(text.length()>128)throw new IOException("Notice bound");notice=text;continue;}
                if(type==DuelWire.WELCOME) {var w=DuelWire.welcome(f);DuelWire.end(f);synchronized(this){welcome=w;sequence=Math.max(sequence,w.nextSequence());}continue;}
                if(type!=DuelWire.STATE) throw new IOException("Practice state required");
                var next=DuelWire.state(f);DuelWire.end(f);
                if(pending.incrementAndGet()>32)throw new IOException("State delivery bound");
                delivery.schedule(()->{pending.decrementAndGet();if(socket==current && connected) {
                    state=next;if(!history.offer(next)){history.poll();history.offer(next);}
                }},oneWayMillis,TimeUnit.MILLISECONDS);
            }}catch(IOException e){}finally{disconnect(current);sender.interrupt();delivery.shutdownNow();}
        },"practice-receive");receiver.setDaemon(true);receiver.start();
    }
    private record Delivery(byte[] bytes,long at) implements Delayed {
        public long getDelay(TimeUnit unit){return unit.convert(at-System.nanoTime(),TimeUnit.NANOSECONDS);}
        public int compareTo(Delayed other){return Long.compare(at,((Delivery)other).at);}
    }
    private void disconnect(Socket current) {try{current.close();}catch(IOException ignored){}if(socket==current) connected=false;}
    public synchronized void reconnect() throws IOException {
        if(connected || welcome==null) return;outgoing.clear();connect(welcome.match(),welcome.resume());
    }
    public boolean connected(){return connected;}
    public String notice(){return notice;}
    public int side(){return welcome==null?-1:welcome.side();}
    public String match(){return welcome==null?"":welcome.match();}
    public DuelSession.State state(){return state;}
    public List<DuelSession.State> drainHistory(){var copy=new ArrayList<DuelSession.State>();history.drainTo(copy);return copy;}
    public synchronized void operation(DuelSession.Op op,int slot,int other) throws IOException {
        if(state!=null) send(DuelSession.Command.operation(state.epoch(),sequence++,op,slot,other));
    }
    public synchronized void input(long frame,double forward,double strafe,double yaw,double pitch,boolean jump,boolean sprint,boolean attack,boolean use,boolean release,int slot) throws IOException {
        if(state!=null) send(new DuelSession.Command(state.epoch(),sequence++,frame,DuelSession.Op.INPUT,forward,strafe,yaw,pitch,jump,sprint,attack,use,release,slot,0));
    }
    public synchronized void send(DuelSession.Command command) throws IOException {
        sequence=Math.max(sequence,command.sequence()+1);
        if(!connected || outgoing.size()>=32) throw new IOException("Practice connection unavailable");
        outgoing.offer(new Delivery(DuelWire.command(command),System.nanoTime()+oneWayMillis*1_000_000L));
    }
    /** Test and UI reconnect path; no capability is exposed. */
    public void interruptConnection(){Socket current=socket;if(current!=null)disconnect(current);}
    @Override public void close(){closed=true;interruptConnection();}
}
