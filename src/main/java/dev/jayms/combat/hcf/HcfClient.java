package dev.jayms.combat.hcf;

import dev.jayms.combat.duel.DuelWire;
import dev.jayms.net.SecureTransport;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Pinned TLS client. Password lives only in process memory and is wiped on close. */
public final class HcfClient implements AutoCloseable {
    private final String host,pin,name;private final int port,team;private final char[] password;
    private final ArrayBlockingQueue<byte[]> outgoing=new ArrayBlockingQueue<>(32);
    private volatile Socket socket;private volatile boolean connected,closed;
    private volatile HcfSession.State state;private volatile HcfWire.Welcome welcome;
    private long sequence=1;
    public HcfClient(String host,int port,String pin,String name,char[] password,boolean register,int team) throws IOException {
        this.host=host;this.port=port;this.pin=pin;this.name=name;this.password=password.clone();this.team=team;
        try{connect(register);}catch(IOException e){Arrays.fill(this.password,'\0');throw e;}
    }
    private synchronized void connect(boolean register) throws IOException {
        if(closed)throw new IOException("HCF client closed");
        Socket current=SecureTransport.connect(host,port,pin);socket=current;
        var in=new DataInputStream(current.getInputStream());var out=new DataOutputStream(current.getOutputStream());
        try {
            DuelWire.send(out,HcfWire.hello(new HcfWire.Hello(name,password,register,team)));
            var frame=DuelWire.read(in);if(frame.readUnsignedByte()!=HcfWire.WELCOME)throw new IOException("HCF sign-in failed");
            welcome=HcfWire.welcome(frame);DuelWire.end(frame);sequence=Math.max(sequence,welcome.nextSequence());connected=true;state=null;
        }catch(IOException e){disconnect(current);throw e;}
        Thread sender=new Thread(()->{
            try{while(!closed && !current.isClosed())DuelWire.send(out,outgoing.take());}
            catch(IOException|InterruptedException ignored){}finally{disconnect(current);}
        },"hcf-send");sender.setDaemon(true);sender.start();
        Thread receiver=new Thread(()->{
            try{while(!closed && !current.isClosed()) {
                var frame=DuelWire.read(in);if(frame.readUnsignedByte()!=HcfWire.STATE)throw new IOException("HCF state required");
                var next=HcfWire.state(frame);DuelWire.end(frame);if(socket==current)state=next;
            }}catch(IOException ignored){}finally{disconnect(current);sender.interrupt();}
        },"hcf-receive");receiver.setDaemon(true);receiver.start();
    }
    private void disconnect(Socket current){try{current.close();}catch(IOException ignored){}if(socket==current)connected=false;}
    public synchronized void reconnect() throws IOException {if(!connected){outgoing.clear();connect(false);}}
    public HcfSession.State state(){return state;}
    public String id(){return welcome==null?"":welcome.id();}
    public boolean connected(){return connected;}
    public HcfSession.MemberView self(){var s=state;return s==null?null:s.members().stream().filter(m->m.id().equals(id())).findFirst().orElse(null);}
    public synchronized void operation(HcfCommand.Op op,int choice) throws IOException {
        if(state!=null)send(HcfCommand.operation(state.season(),sequence++,op,choice));
    }
    public synchronized void input(long frame,double forward,double strafe,double yaw,double pitch,boolean jump,boolean sprint,boolean attack,boolean use,boolean release,int slot) throws IOException {
        if(state!=null)send(new HcfCommand(state.season(),sequence++,frame,HcfCommand.Op.INPUT,forward,strafe,yaw,pitch,jump,sprint,attack,use,release,slot,0));
    }
    public synchronized void refill(int slot) throws IOException {
        if(state!=null)send(new HcfCommand(state.season(),sequence++,0,HcfCommand.Op.REFILL,0,0,0,0,false,false,false,false,false,slot,0));
    }
    public synchronized void send(HcfCommand command) throws IOException {
        sequence=Math.max(sequence,command.sequence()+1);if(!connected || !outgoing.offer(HcfWire.command(command)))throw new IOException("HCF connection unavailable");
    }
    public void interruptConnection(){Socket current=socket;if(current!=null)disconnect(current);}
    @Override public void close(){closed=true;interruptConnection();Arrays.fill(password,'\0');}
}
