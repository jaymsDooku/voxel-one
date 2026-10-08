package dev.jayms.net.mobile;

import com.sun.net.httpserver.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Loopback HTTP adapter. An HTTPS reverse proxy is required for phone access.
 * Each bearer owns one ordinary pinned-TLS multiplayer client. No new world rules or account DB. */
public final class MobileGateway implements AutoCloseable {
    public record Target(String host, int port, String fingerprint) {
        public Target { if(host==null || port<1 || port>65535 || fingerprint==null || fingerprint.isBlank())throw new IllegalArgumentException("Pinned server target required"); }
    }
    private final HttpServer server;
    private final ExecutorService executor=Executors.newFixedThreadPool(4);
    private final ScheduledExecutorService cleanup=Executors.newScheduledThreadPool(2);
    private final Map<String,Session> sessions=new ConcurrentHashMap<>();
    private final Map<String,ArrayDeque<Long>> attempts=new LinkedHashMap<>();
    private final SecureRandom random=new SecureRandom();
    private final Target sandbox,city;
    private final java.util.function.Function<Target,MultiplayerServer.TransportFailure> fixtureFailure;
    private final long idleNanos=Duration.ofMinutes(15).toNanos();
    private final java.util.concurrent.atomic.AtomicInteger connecting=new java.util.concurrent.atomic.AtomicInteger();
    public MobileGateway(int port, Target sandbox, Target city) throws IOException {
        this(port,sandbox,city,target->null);
    }
    // Only the isolated fixture supplies server diagnostics. Production returns generic safe errors.
    MobileGateway(int port, Target sandbox, Target city, java.util.function.Function<Target,MultiplayerServer.TransportFailure> fixtureFailure) throws IOException {
        this.sandbox=sandbox;this.city=city;this.fixtureFailure=fixtureFailure;
        server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),port),32);
        server.createContext("/mobile/v1/",this::handle);server.setExecutor(executor);
        cleanup.scheduleAtFixedRate(this::maintain,1,1,TimeUnit.SECONDS);
        cleanup.scheduleAtFixedRate(()->sessions.values().forEach(Session::heartbeat),250,250,TimeUnit.MILLISECONDS);
    }
    public int port(){return server.getAddress().getPort();}
    public void start(){server.start();}
    private void maintain(){
        for(var e:sessions.entrySet()){
            Session session=e.getValue();
            synchronized(session){
                if(System.nanoTime()-session.lastUsed>idleNanos){
                    if(sessions.remove(e.getKey(),session))session.close();
                }else if(session.client.connected()){
                    // World-event maintenance may wait on a snapshot; heartbeat runs independently.
                    session.poll();
                }
            }
        }
    }
    private synchronized boolean authAllowed(String ip){
        long now=System.nanoTime();var list=attempts.computeIfAbsent(ip,k->new ArrayDeque<>());
        while(!list.isEmpty() && now-list.peekFirst()>Duration.ofMinutes(1).toNanos())list.removeFirst();
        if(attempts.size()>1024){var first=attempts.keySet().iterator();first.next();first.remove();}
        if(list.size()>=10)return false;list.addLast(now);return true;
    }
    private void handle(HttpExchange exchange)throws IOException{
        int code=200;Object result;
        try{
            if(!exchange.getRequestMethod().equals("POST"))throw new Failure(405,"Use POST");
            if(exchange.getRequestHeaders().containsKey("Origin"))throw new Failure(403,"Native clients only");
            byte[] bytes=exchange.getRequestBody().readNBytes(8193);
            if(bytes.length>8192)throw new Failure(413,"Request too large");
            var request=Json.object(new String(bytes,StandardCharsets.UTF_8));
            String path=exchange.getRequestURI().getPath();
            if(path.equals("/mobile/v1/login")){
                if(!authAllowed(exchange.getRemoteAddress().getAddress().getHostAddress()))throw new Failure(429,"Too many sign-in attempts; wait a minute");
                if(connecting.incrementAndGet()>4){connecting.decrementAndGet();throw new Failure(503,"Sign-in busy");}
                try{
                    synchronized(sessions){if(sessions.size()>=32)throw new Failure(503,"Server full");}
                    String game=Json.text(request,"game");Target target=game.equals("sandbox")?sandbox:game.equals("city")?city:null;
                    if(target==null)throw new Failure(400,"Choose a game");
                    String name=Json.text(request,"username");char[] password=Json.text(request,"password").toCharArray();
                    MultiplayerClient client;
                    try{client=new MultiplayerClient(target.host(),target.port(),name,password,Boolean.TRUE.equals(request.get("register")),target.fingerprint());}
                    catch(IOException e){throw new Failure(401,"Sign-in failed. Check your account and game server.");}
                    finally{Arrays.fill(password,'\0');request.remove("password");}
                    var session=new Session(client,target);byte[] token=new byte[32];random.nextBytes(token);String key=Base64.getUrlEncoder().withoutPadding().encodeToString(token);
                    synchronized(sessions){if(sessions.size()>=32){session.close();throw new Failure(503,"Server full");}sessions.put(key,session);}
                    result=Map.of("token",key,"state",session.state(Map.of()));
                }finally{connecting.decrementAndGet();}
            }else{
                String auth=exchange.getRequestHeaders().getFirst("Authorization");
                String key=auth!=null && auth.startsWith("Bearer ")?auth.substring(7):"";
                var session=sessions.get(key);
                if(session==null)throw new Failure(401,"Session expired. Sign in again.");
                synchronized(session){
                    if(System.nanoTime()-session.lastUsed>idleNanos || !session.client.connected()){
                        String reason=session.client.connected()?"Session idle limit reached":transportReason(session.client.status());
                        var fixtureCode=fixtureFailure.apply(session.target);
                        if(reason.equals("Game server transport closed") && fixtureCode!=null)reason="Synthetic server "+fixtureCode.name();
                        sessions.remove(key,session);session.close();throw new Failure(401,reason+". Sign in again.");
                    }
                    session.lastUsed=System.nanoTime();
                    result=switch(path){
                        case "/mobile/v1/state" -> session.state(request);
                        case "/mobile/v1/move" -> {session.poll();if(!session.respawned)session.move(request);yield Map.of("status","moved");}
                        case "/mobile/v1/action" -> session.action(request);
                        case "/mobile/v1/logout" -> {sessions.remove(key,session);session.close();yield Map.of("status","signed_out");}
                        default -> throw new Failure(404,"Unknown route");
                    };
                }
            }
        }catch(Failure e){code=e.status;result=Map.of("error",e.getMessage());}
        catch(IllegalArgumentException e){code=400;result=Map.of("error","Invalid request");}
        catch(Exception e){code=503;result=Map.of("error","Game service unavailable");}
        byte[] data=Json.write(result).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control","no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        exchange.sendResponseHeaders(code,data.length);
        try(var out=exchange.getResponseBody()){out.write(data);}finally{exchange.close();}
    }
    private static final class Failure extends RuntimeException{final int status;Failure(int status,String message){super(message);this.status=status;}}
    private static String transportReason(String status){
        // Never return raw socket/exception text or account information to the phone.
        if(status!=null){
            String value=status.toLowerCase(Locale.ROOT);
            if(value.contains("incoming queue full"))return "Incoming game queue full";
            if(value.contains("outgoing queue full"))return "Outgoing game queue full";
            if(value.contains("unknown server message"))return "Unknown game protocol message";
            if(value.contains("read timed out"))return "Game socket read timed out";
        }
        return "Game server transport closed";
    }
    private static final class Session implements AutoCloseable{
        final Target target;final MultiplayerClient client;final Terrain terrain;final WorldVoxels world;volatile Protocol.Pose pose;final Object poseLock=new Object();
        volatile long lastUsed=System.nanoTime();boolean respawned;long lastSequence=-1,lastCityReceipt;
        Protocol.Pose lastSentPose;long lastPoseSend;
        Session(MultiplayerClient client,Target target){this.target=target;this.client=client;terrain=new Terrain(client.seed,client.generatorVersion);world=new WorldVoxels(terrain);client.initialEdits.forEach(world::apply);pose=client.spawn;}
        // Short transport lock never waits for world snapshots and does not extend HTTP idle expiry.
        void heartbeat(){synchronized(poseLock){
            long now=System.nanoTime();
            // Coalesce phone updates instead of filling the TCP stream while city work blocks its reader.
            // Unchanged sessions retain a1Hz keepalive; changed poses send at most4Hz.
            if(client.connected() && (!pose.equals(lastSentPose) || now-lastPoseSend>=1_000_000_000L)){
                client.move(pose);lastSentPose=pose;lastPoseSend=now;
            }
        }}
        void poll(){client.poll().forEach(world::apply);if(client.respawn!=null){synchronized(poseLock){pose=client.respawn;}client.respawn=null;respawned=true;}}
        void move(Map<String,Object> request){
            if(!request.containsKey("x"))return;
            if(request.containsKey("sequence")){
                double sequence=Json.number(request,"sequence");
                if(sequence<0 || sequence>1_000_000_000_000L || sequence!=Math.rint(sequence))throw new IllegalArgumentException("Invalid pose sequence");
                if(sequence<=lastSequence)return;lastSequence=(long)sequence;
            }
            float x=(float)Json.number(request,"x"),y=(float)Json.number(request,"y"),z=(float)Json.number(request,"z");
            float yaw=(float)Json.number(request,"yaw"),pitch=(float)Json.number(request,"pitch");
            if(Math.abs(x)>Terrain.LIMIT || Math.abs(z)>Terrain.LIMIT || y<Terrain.MIN_Y || y>Terrain.MAX_Y+32 || Math.abs(pitch)>Math.toRadians(89) || !Float.isFinite(yaw) || !Float.isFinite(pitch))throw new IllegalArgumentException("Invalid pose");
            synchronized(poseLock){pose=new Protocol.Pose(client.id,x,y,z,(float)Math.toDegrees(yaw),(float)Math.toDegrees(pitch),0,0,false,0,0,false);}
        }
        Map<String,Object> state(Map<String,Object> request){
            poll();if(!respawned)move(request);var view=pose;
            if(request.containsKey("focusX")){
                double x=Json.number(request,"focusX"),z=Json.number(request,"focusZ");
                if(Math.abs(x)>Terrain.LIMIT-24 || Math.abs(z)>Terrain.LIMIT-24)throw new IllegalArgumentException("Invalid focus");
                view=new Protocol.Pose(client.id,(float)x,terrain.surfaceHeight((int)x,(int)z)+1,(float)z,0,0,0,0,false,0,0,false);
            }
            var result=MobileSnapshot.capture(world,terrain,view,client.inventory,client.health,client.city,client.notice(),16);
            result.put("pose",List.of(pose.x(),pose.y(),pose.z(),(float)Math.toRadians(pose.yaw()),(float)Math.toRadians(pose.pitch())));
            result.put("resetPose",respawned);respawned=false;
            result.put("drops",client.drops.values().stream().map(d->Map.of("id",d.id(),"type",d.type(),"x",d.x(),"y",d.y(),"z",d.z())).toList());
            result.put("players",client.players.values().stream().map(p->Map.of("id",p.id(),"name",client.names.getOrDefault(p.id(),"Player"),"x",p.x(),"y",p.y(),"z",p.z())).toList());
            return result;
        }
        Map<String,Object> action(Map<String,Object> request)throws InterruptedException{
            poll();String kind=Json.text(request,"kind");
            boolean sent;
            switch(kind){
                case "edit" -> {
                    int x=Json.integer(request,"blockX"),y=Json.integer(request,"blockY"),z=Json.integer(request,"blockZ"),type=Json.integer(request,"type"),slot=Json.integer(request,"slot");
                    if(slot<0 || slot>=9 || type!=0 && type!=client.inventory.type(slot))throw new IllegalArgumentException("Invalid hotbar selection");
                    var edit=new Protocol.Edit(x,y,z,type);if(!edit.valid())throw new IllegalArgumentException("Invalid edit");
                    sent=client.edit(edit,pose,slot);
                    long end=System.nanoTime()+Duration.ofSeconds(2).toNanos();
                    while(client.pending(edit) && client.connected() && System.nanoTime()<end){Thread.sleep(10);poll();}
                    if(client.pending(edit))throw new Failure(504,"Edit not confirmed; refresh before trying again");
                }
                case "city" -> {
                    int command=Json.integer(request,"command"),value=Json.integer(request,"value");
                    if(!Set.of(CityCommand.ROAD,CityCommand.ZONE,CityCommand.DEMOLISH,CityCommand.SPECIAL).contains(command))throw new IllegalArgumentException("Unsupported mobile command");
                    if(!(request.get("points") instanceof List<?> list) || list.size()>32)throw new IllegalArgumentException("Invalid points");
                    var points=new ArrayList<Polygon.Point>();for(Object item:list){
                        if(!(item instanceof List<?> pair) || pair.size()!=2 || !(pair.get(0) instanceof Number a) || !(pair.get(1) instanceof Number b))throw new IllegalArgumentException("Invalid point");
                        points.add(new Polygon.Point(a.floatValue(),b.floatValue()));
                    }
                    long wait=550_000_000L-(System.nanoTime()-lastCityReceipt);
                    if(wait>0)Thread.sleep((wait+999_999)/1_000_000);
                    var completed=new java.util.concurrent.atomic.AtomicBoolean();
                    sent=client.cityCommand(new CityCommand(command,value,points),message->completed.set(true));
                    // Receipt may wait behind the server's serialized city simulation/terrain work.
                    // Stay below the phone's15second HTTP deadline; never retry an uncertain command.
                    long end=System.nanoTime()+Duration.ofSeconds(8).toNanos();
                    while(sent && !completed.get() && client.connected() && System.nanoTime()<end){Thread.sleep(10);poll();}
                    if(sent && !completed.get())throw new Failure(504,"City command not confirmed; refresh before trying again");
                    if(sent)lastCityReceipt=System.nanoTime();
                    // A command receipt precedes its next periodic city frame. Wait for that frame.
                    long received=client.cityReceived;end=System.nanoTime()+Duration.ofSeconds(2).toNanos();
                    while(sent && client.cityReceived<=received && client.connected() && System.nanoTime()<end){Thread.sleep(10);poll();}
                }
                case "swap" -> {int a=Json.integer(request,"a"),b=Json.integer(request,"b");if(a<0 || b<0 || a>=36 || b>=36)throw new IllegalArgumentException();client.swap(a,b);sent=true;Thread.sleep(100);}
                case "craft" -> {int recipe=Json.integer(request,"recipe");sent=client.craft(recipe,pose);Thread.sleep(100);}
                default -> throw new IllegalArgumentException("Unsupported action");
            }
            if(!sent)throw new Failure(409,"Action unavailable; refresh the game");
            return state(Map.of());
        }
        public void close(){client.close();}
    }
    @Override public void close(){server.stop(0);cleanup.shutdownNow();executor.shutdownNow();sessions.values().forEach(Session::close);sessions.clear();}
    public static void main(String[] args)throws Exception{
        var opts=new HashMap<String,String>();for(int i=0;i<args.length;i+=2){if(i+1>=args.length)throw new IllegalArgumentException("Each option needs a value");opts.put(args[i],args[i+1]);}
        String host=opts.getOrDefault("--server","127.0.0.1");
        var gateway=new MobileGateway(Integer.parseInt(opts.getOrDefault("--listen-port","25567")),
                new Target(host,Integer.parseInt(opts.getOrDefault("--sandbox-port","25565")),opts.get("--sandbox-pin")),
                new Target(host,Integer.parseInt(opts.getOrDefault("--city-port","25566")),opts.get("--city-pin")));
        Runtime.getRuntime().addShutdownHook(new Thread(gateway::close));gateway.start();
        System.out.println("Mobile gateway listening on loopback port "+gateway.port()+"; an HTTPS reverse proxy is required for phone access.");
    }
}
