package dev.jayms.combat.duel;

import dev.jayms.combat.*;
import dev.jayms.net.SecureTransport;
import dev.jayms.recording.ScreenRecorder;
import org.lwjgl.opengl.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Synthetic input into two real TLS clients, two native engine views, one authoritative server.
 * No pose injection or privileged test messages. All fixtures and TLS identities are isolated.
 */
public final class PracticePlaytest {
    static final int W=800,H=520;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void waitFor(BooleanSupplier done,long millis) throws Exception {
        long until=System.nanoTime()+millis*1_000_000L;while(!done.getAsBoolean() && System.nanoTime()<until)Thread.sleep(10);
        check(done.getAsBoolean(),"Timed out awaiting native workflow state");
    }
    static Vec3 feet(DuelClient c,int side){return c.state().fighters().get(side).body().pose().feet();}
    static double yaw(DuelClient c,int side){Vec3 delta=feet(c,1-side).subtract(feet(c,side));return Math.toDegrees(Math.atan2(delta.z(),delta.x()));}
    static double pitch(DuelClient c,int side){Vec3 delta=feet(c,1-side).subtract(feet(c,side));return Math.toDegrees(Math.atan2(delta.y()-.32,Math.hypot(delta.x(),delta.z())));}
    static double gap(DuelClient c){return feet(c,0).distance(feet(c,1));}
    static final class Driver {
        final DuelClient a,b;final AtomicLong frame;final AtomicReference<String> stage;long input;
        Driver(DuelClient a,DuelClient b,AtomicLong frame,AtomicReference<String> stage){this.a=a;this.b=b;this.frame=frame;this.stage=stage;}
        void send(DuelClient c,double forward,double yaw,double pitch,boolean sprint,boolean attack,boolean use,boolean release,int slot) throws Exception {
            c.input(frame.get(),forward,0,yaw,pitch,false,sprint,attack,use,release,slot);
        }
        void pause(int ticks) throws Exception {for(int t=0;t<ticks;t++){send(a,0,yaw(a,0),0,false,false,false,false,0);send(b,0,yaw(b,1),0,false,false,false,false,0);Thread.sleep(50);input++;}}
        void walkTo(DuelClient c,int side,double x,double z) throws Exception {
            for(int t=0;t<400;t++) {
                Vec3 p=feet(c,side);double dx=x-p.x(),dz=z-p.z(),distance=Math.hypot(dx,dz);if(distance<.08)break;
                send(c,Math.min(.8,distance/.215*.18),Math.toDegrees(Math.atan2(dz,dx)),0,false,false,false,false,0);Thread.sleep(50);input++;
                send(c==a?b:a,0,yaw(c==a?b:a,1-side),0,false,false,false,false,0);
            }
            send(c,0,c.state().fighters().get(side).yaw(),0,false,false,false,false,0);Thread.sleep(300);
            check(Math.hypot(feet(c,side).x()-x,feet(c,side).z()-z)<.4,"Input-driven route reached waypoint");
        }
        void run() throws Exception {
            waitFor(()->a.state()!=null && b.state()!=null,4000);check(a.match().equals(b.match()),"Both clients joined same match");
            stage.set("ready-kit");a.operation(DuelSession.Op.SWAP,1,6);Thread.sleep(250);a.operation(DuelSession.Op.SWAP,1,6);Thread.sleep(250);
            a.operation(DuelSession.Op.READY,0,0);b.operation(DuelSession.Op.READY,0,0);
            waitFor(()->a.state().phase()==DuelSession.Phase.LIVE && b.state().phase()==DuelSession.Phase.LIVE,6000);
            stage.set("spacing");
            for(int t=0;t<180 && gap(a)>4.5;t++){send(a,1,90,0,false,false,false,false,0);send(b,1,-90,0,false,false,false,false,0);Thread.sleep(50);input++;}
            pause(8);
            for(int t=0;t<80 && gap(a)>2.4;t++){send(a,.2,yaw(a,0),0,false,false,false,false,0);Thread.sleep(50);input++;}
            pause(8);check(gap(a)<3,"Spacing enters sword reach");
            stage.set("melee-sprint-counter");
            for(int t=0;t<110;t++) {
                double distance=gap(a),move=distance>2.4?.5:0;
                send(a,move,yaw(a,0),pitch(a,0),t%16>0,t%16==3,false,false,0);
                send(b,distance>2.5?.3:0,yaw(b,1),pitch(b,1),t%19>0,t%19==9,false,false,0);
                Thread.sleep(50);input++;
            }
            pause(7);
            check(a.state().fighters().get(0).score().hits()>0 && b.state().fighters().get(1).score().hits()>0,"Both clients landed authoritative hits");
            stage.set("splash-prepare");
            for(int t=0;t<100 && gap(a)>2.3;t++){send(a,.4,yaw(a,0),0,false,false,false,false,0);send(b,.4,yaw(b,1),0,false,false,false,false,0);Thread.sleep(50);input++;}
            pause(6);
            double ah=a.state().fighters().get(0).body().health(),bh=b.state().fighters().get(1).body().health();
            send(a,0,yaw(a,0),-89,false,false,true,false,1);send(b,0,yaw(b,1),-89,false,false,true,false,1);Thread.sleep(650);
            check(a.state().fighters().get(0).body().health()>ah && b.state().fighters().get(1).body().health()>bh,"Feet/opponent splash heals both");
            stage.set("feet-and-opponent-splash");pause(12);
            a.operation(DuelSession.Op.REFILL,1,0);Thread.sleep(300);check(a.state().fighters().get(0).body().inventory().get(1).count()==1,"Empty slot refills from finite reserve");
            stage.set("forward-kite-and-missed-pot");send(a,-1,yaw(a,0),-25,false,false,true,false,6);Thread.sleep(500);send(a,0,0,0,false,false,true,false,1);Thread.sleep(800);
            check(a.state().fighters().get(0).score().potsUsed()>=3,"Forward and missed pot spend finite supplies");
            stage.set("speed-and-fire");
            for(int slot:new int[]{3,4}) {
                send(a,0,yaw(a,0),0,false,false,true,false,slot);
                for(int t=0;t<38;t++){send(a,0,yaw(a,0),0,false,false,false,false,slot);Thread.sleep(50);input++;}
            }
            check(a.state().fighters().get(0).body().effects().stream().anyMatch(e->e.effect()==CombatEvent.Effect.SPEED),"Speed drink completes");
            check(a.state().fighters().get(0).body().effects().stream().anyMatch(e->e.effect()==CombatEvent.Effect.FIRE_RESISTANCE),"Fire resistance drink completes");
            if(a.state().profile()==DuelSession.Profile.DEBUFF) {
                stage.set("debuff-profile");send(b,0,yaw(b,1),-75,false,false,true,false,7);Thread.sleep(700);send(b,0,yaw(b,1),-75,false,false,true,false,8);Thread.sleep(700);
                check(b.state().fighters().get(1).body().effects().stream().anyMatch(e->e.effect()==CombatEvent.Effect.POISON),"Debuff poison applied");
            }
            stage.set("pearl-escape");Vec3 before=feet(a,0);send(a,0,0,-30,false,false,true,false,2);Thread.sleep(1500);
            check(feet(a,0).distance(before)>2,"Pearl escape changes authoritative position");
            int pearls=a.state().fighters().get(0).body().inventory().get(2).count();send(a,0,0,-30,false,false,true,false,2);Thread.sleep(300);
            check(a.state().fighters().get(0).body().inventory().get(2).count()==pearls,"Pearl cooldown rejects extra consumption");
            stage.set("reconnect");b.interruptConnection();Thread.sleep(300);b.reconnect();pause(5);check(b.state().phase()==DuelSession.Phase.LIVE,"Reconnect retains live match");
            stage.set("forfeit-rematch-stale");long epoch=a.state().epoch();a.operation(DuelSession.Op.FORFEIT,0,0);
            waitFor(()->b.state().phase()==DuelSession.Phase.RESULT,3000);check(b.state().result().contains("B wins"),"Forfeit result agrees");
            a.operation(DuelSession.Op.READY,0,0);b.operation(DuelSession.Op.READY,0,0);
            waitFor(()->a.state().phase()==DuelSession.Phase.LIVE && a.state().epoch()>epoch,6000);
            check(a.state().fighters().get(0).body().health()==20 && a.state().fighters().get(0).body().effects().isEmpty(),"Rematch resets health and effects");
            // Old-epoch intent cannot end a fresh round, even with a fresh positive sequence.
            a.send(DuelSession.Command.operation(epoch,1_000_000,DuelSession.Op.FORFEIT,0,0));pause(6);check(a.state().phase()==DuelSession.Phase.LIVE,"Stale round rejected");
            stage.set("cover-rejection");
            walkTo(a,0,9.4,-28);walkTo(a,0,9.4,11);walkTo(b,1,9.4,28);walkTo(b,1,9.4,9.4);walkTo(b,1,11,9.4);
            double health=b.state().fighters().get(1).body().health();
            send(a,0,yaw(a,0),-40,false,true,false,false,0);pause(8);
            check(b.state().fighters().get(1).body().health()==health,"Low diagonal melee ray through cover rejected");
            stage.set("wall-pearl");walkTo(a,0,9.4,8);walkTo(a,0,30.65,8);walkTo(a,0,30.65,11);send(a,0,0,0,false,false,true,false,2);pause(18);
            check(feet(a,0).x()<=30.7 && feet(a,0).x()>=29,"Pearl cannot cross outer wall");
            stage.set("knockout");walkTo(a,0,29.9,8);walkTo(a,0,11,8);
            for(int t=0;t<300 && a.state().phase()==DuelSession.Phase.LIVE;t++) {
                send(a,gap(a)>2.2?.6:0,yaw(a,0),pitch(a,0),false,t%12==0,false,false,0);
                send(b,0,yaw(b,1),0,false,false,false,false,0);Thread.sleep(50);input++;
            }
            waitFor(()->b.state().phase()==DuelSession.Phase.RESULT,3000);
            check(!b.state().fighters().get(1).body().alive() && b.state().fighters().get(1).body().health()==0,"Real client knockout ends round");
            var inventory=b.state().fighters().get(1).body().inventory();send(b,1,-90,0,false,true,true,false,1);Thread.sleep(250);
            check(b.state().fighters().get(1).body().inventory().equals(inventory),"Dead/result actions cannot spend supplies");
            stage.set("knockout-result");pause(16);stage.set("complete");
        }
    }
    public static void main(String[] args) throws Exception {
        if((args.length!=4 && args.length!=5) || !args[0].equals("--display") || !args[1].equals(System.getenv("DISPLAY")))throw new IllegalArgumentException("Use --display with inherited DISPLAY, evidence, ignored scratch directory, optional RTT 0/80/150");
        int[] delays={0,40,75};if(args.length==5){int rtt=Integer.parseInt(args[4]);if(rtt!=0 && rtt!=80 && rtt!=150)throw new IllegalArgumentException("RTT");delays=new int[]{rtt/2};}
        Path evidence=Path.of(args[2]),scratch=Path.of(args[3]);Files.createDirectories(evidence);Files.createDirectories(scratch);
        var tls=SecureTransport.server(scratch.resolve("synthetic-tls"));
        check(glfwInit(),"GLFW");glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long wa=glfwCreateWindow(W,H,"Practice playtest - client A",0,0),wb=glfwCreateWindow(W,H,"Practice playtest - client B",0,0);check(wa!=0 && wb!=0,"Two native client windows");
        glfwSetWindowPos(wa,0,0);glfwSetWindowPos(wb,40,40);
        glfwMakeContextCurrent(wa);GLCapabilities ca=GL.createCapabilities();glfwSwapInterval(0);String renderer=glGetString(GL_RENDERER);
        glfwMakeContextCurrent(wb);GLCapabilities cb=GL.createCapabilities();glfwSwapInterval(0);
        var lines=new ArrayList<String>();lines.add("configured_rtt_ms,stage,view_frame,server_tick,combat_tick,input_frame_A,input_frame_B,input_sequence_A,input_sequence_B,health_A,health_B,hits_A,hits_B,x_A,y_A,z_A,vx_A,vy_A,vz_A,x_B,y_B,z_B,vx_B,vy_B,vz_B");
        try {
            for(int delay:delays) {
                var profile=delay==75?DuelSession.Profile.DEBUFF:DuelSession.Profile.NO_DEBUFF;
                try(var server=new DuelServer(tls.context(),InetAddress.getLoopbackAddress(),0,false);
                    var a=new DuelClient("127.0.0.1",server.port(),tls.fingerprint(),profile,false,"",delay);
                    var b=new DuelClient("127.0.0.1",server.port(),tls.fingerprint(),profile,false,"",delay)) {
                    glfwMakeContextCurrent(wa);GL.setCapabilities(ca);var va=new PracticeView();var recorder=new ScreenRecorder(scratch.resolve("recording-"+delay));
                    glfwMakeContextCurrent(wb);GL.setCapabilities(cb);var vb=new PracticeView();
                    var frame=new AtomicLong();var stage=new AtomicReference<>("connecting");var error=new AtomicReference<Throwable>();var done=new AtomicBoolean();
                    Thread driver=new Thread(()->{try{new Driver(a,b,frame,stage).run();}catch(Throwable e){error.set(e);}finally{done.set(true);}},"synthetic-input");driver.setDaemon(true);driver.start();
                    var statesA=new HashMap<Long,DuelSession.State>();var statesB=new HashMap<Long,DuelSession.State>();int matched=0,recorded=0;boolean saved=false;String previousStage="";
                    try {
                        long until=System.nanoTime()+240_000_000_000L;
                        while(!done.get() && System.nanoTime()<until) {
                            glfwPollEvents();String step=stage.get();
                            for(int side=0;side<2;side++) {
                                glfwMakeContextCurrent(side==0?wa:wb);GL.setCapabilities(side==0?ca:cb);
                                var client=side==0?a:b;var view=side==0?va:vb;
                                if(client.state()!=null){var f=client.state().fighters().get(side);view.camera.setYaw((float)f.yaw());view.camera.setPitch((float)f.pitch());}
                                view.render(client,W,H,false,-1,0,"Synthetic input | configured RTT "+delay*2+" ms | "+step);
                                if(side==0 && delay==0 && a.state()!=null && a.state().phase()==DuelSession.Phase.LIVE && gap(a)<12 && !step.equals("spacing") && recorded<120) {
                                    if(!recorder.active())recorder.toggle(W,H);recorder.capture(W,H);recorded++;
                                } else if(side==0 && recorder.active()) recorder.toggle(W,H);
                                if(!saved && step.equals("feet-and-opponent-splash"))PracticeView.capture(evidence.resolve("practice-"+delay*2+"ms-"+(side==0?"a":"b")+".png"),W,H);
                                if(step.equals("knockout-result") && side==0)PracticeView.capture(evidence.resolve("practice-"+delay*2+"ms-result.png"),W,H);
                                glfwSwapBuffers(side==0?wa:wb);
                            }
                            if(step.equals("feet-and-opponent-splash"))saved=true;
                            for(var s:a.drainHistory())statesA.put(s.tick(),s);for(var s:b.drainHistory())statesB.put(s.tick(),s);
                            for(long tick:List.copyOf(statesA.keySet())) if(statesB.containsKey(tick)) {
                                var s=statesA.remove(tick);var other=statesB.remove(tick);check(s.equals(other),"Two clients agree on exact tick, hit, damage, impulse and inventory");matched++;
                                boolean event=s.events().stream().anyMatch(e->e.type()==CombatEvent.Type.HIT || e.type()==CombatEvent.Type.HEAL || e.type()==CombatEvent.Type.TELEPORT || e.type()==CombatEvent.Type.PEARL_REJECT);
                                if(event || !step.equals(previousStage)) {
                                    var x=s.fighters().get(0);var y=s.fighters().get(1);var xp=x.body().pose();var yp=y.body().pose();
                                    lines.add(String.format(Locale.ROOT,"%d,%s,%d,%d,%d,%d,%d,%d,%d,%.3f,%.3f,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f",delay*2,step,frame.get(),s.tick(),s.combatTick(),x.lastFrame(),y.lastFrame(),x.lastInputSequence(),y.lastInputSequence(),x.body().health(),y.body().health(),x.score().hits(),y.score().hits(),xp.feet().x(),xp.feet().y(),xp.feet().z(),xp.velocity().x(),xp.velocity().y(),xp.velocity().z(),yp.feet().x(),yp.feet().y(),yp.feet().z(),yp.velocity().x(),yp.velocity().y(),yp.velocity().z()));
                                }
                                previousStage=step;
                            }
                            frame.incrementAndGet();Thread.sleep(10);
                        }
                        check(done.get(),"Native workflow bounded completion");if(error.get()!=null)throw new AssertionError("Native workflow failed at "+stage.get(),error.get());
                        check(matched>100,"At least 100 exact-tick state comparisons");check(server.healthy(),"Server healthy");
                        System.out.printf(Locale.ROOT,"PASS configured RTT %d ms: %d matched authoritative ticks, %d rendered view pairs, workflow %s%n",delay*2,matched,frame.get(),stage.get());
                    }finally {
                        glfwMakeContextCurrent(wa);GL.setCapabilities(ca);recorder.close();va.close();glfwMakeContextCurrent(wb);GL.setCapabilities(cb);vb.close();
                        Files.write(scratch.resolve("observations.csv"),lines);
                    }
                    if(delay==0)try(var files=Files.list(scratch.resolve("recording-0"))) {
                        var mp4=files.filter(p->p.toString().endsWith(".mp4")).findFirst().orElseThrow();check(Files.size(mp4)<6_000_000,"MP4 below 6 MB");Files.copy(mp4,evidence.resolve("practice-real-duel.mp4"),StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            Files.writeString(scratch.resolve("renderer.txt"),renderer+"\n");
        }finally{glfwDestroyWindow(wa);glfwDestroyWindow(wb);glfwTerminate();}
    }
}
