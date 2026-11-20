package dev.jayms.combat.duel;

import dev.jayms.combat.*;
import dev.jayms.combat.KitProfile.*;
import dev.jayms.combat.KitProfile.Stack;
import java.util.*;

/** Single-owner fixed-tick match. Wire input has no position, target, damage or effect fields. */
public final class DuelSession {
    public static final String WORLD_ID="practice-basalt-v1";
    public static final int WARMUP_TICKS=60, ROUND_TICKS=6000, GRACE_TICKS=200;
    public enum Profile { NO_DEBUFF, DEBUFF }
    public enum Phase { QUEUE, READY, WARMUP, LIVE, RESULT }
    public enum Op { INPUT, READY, FORFEIT, REMATCH, QUEUE, SWAP, REFILL }
    public record Command(long epoch,long sequence,long frame,Op op,double forward,double strafe,
                          double yaw,double pitch,boolean jump,boolean sprint,boolean attack,
                          boolean use,boolean release,int slot,int other) {
        public Command {
            Objects.requireNonNull(op);
            if(epoch<0 || sequence<1 || sequence>1_000_000_000_000L || frame<0 || frame>1_000_000_000_000L || !Double.isFinite(forward+strafe+yaw+pitch)
                    || Math.abs(forward)>1 || Math.abs(strafe)>1 || Math.abs(yaw)>36000 || Math.abs(pitch)>89
                    || slot<0 || slot>=36 || other<0 || other>=36) throw new IllegalArgumentException("Input bound");
        }
        public static Command operation(long epoch,long sequence,Op op,int slot,int other) {
            return new Command(epoch,sequence,0,op,0,0,0,0,false,false,false,false,false,slot,other);
        }
    }
    public record Score(int hits,int longestCombo,int potsUsed,int potsRemaining,double health) {}
    public record Fighter(String id,CombatSimulation.Snapshot body,double yaw,double pitch,boolean connected,
                          boolean ready,long lastFrame,long lastInputSequence,Score score) {}
    public record State(long epoch,long tick,long combatTick,Phase phase,Profile profile,int remainingTicks,String result,
                        List<Fighter> fighters,List<CombatSimulation.ProjectileView> projectiles,List<CombatEvent> events) {}
    private static final String[] IDS={"A","B"};
    private final CombatWorld world;
    private final Profile profile;
    private final int warmupTicks,roundTicks,graceTicks;
    private CombatSimulation sim;
    private Phase phase=Phase.QUEUE;
    private long epoch=1,tick,phaseAt;
    private String result="";
    private List<CombatEvent> events=List.of();
    private final boolean[] connected=new boolean[2],ready=new boolean[2];
    private final long[] sequence=new long[2],leftAt=new long[2],inputAt=new long[2],frames=new long[2];
    private final int[] received=new int[2],hits=new int[2],combo=new int[2],longest=new int[2],pots=new int[2];
    private final double[] yaw={90,-90},pitch=new double[2];
    private final Command[] input=new Command[2];
    private final KitProfile[] kits=new KitProfile[2];
    private long internalSequence;
    public DuelSession(Profile profile,CombatWorld world) { this(profile,world,WARMUP_TICKS,ROUND_TICKS,GRACE_TICKS); }
    public DuelSession(Profile profile,CombatWorld world,int warmup,int duration,int grace) {
        this.profile=Objects.requireNonNull(profile); this.world=Objects.requireNonNull(world);
        if(warmup<1 || warmup>1200 || duration<1 || duration>72000 || grace<1 || grace>1200)
            throw new IllegalArgumentException("Match timing");
        warmupTicks=warmup;roundTicks=duration;graceTicks=grace;
        kits[0]=kit(profile);kits[1]=kit(profile);
    }
    public static KitProfile kit(Profile profile) {
        var slots=new ArrayList<Stack>(Collections.nCopies(36,Stack.EMPTY));
        slots.set(0,new Stack(Item.SWORD,1));slots.set(1,new Stack(Item.HEAL_SPLASH,1));
        slots.set(2,new Stack(Item.PEARL,4));slots.set(3,new Stack(Item.SPEED_DRINK,2));
        slots.set(4,new Stack(Item.FIRE_RESIST_DRINK,1));slots.set(5,new Stack(Item.FOOD,8));
        slots.set(6,new Stack(Item.HEAL_SPLASH,1));slots.set(7,new Stack(Item.HEAL_SPLASH,1));
        slots.set(8,new Stack(Item.HEAL_SPLASH,1));
        for(int i=9;i<29;i++) slots.set(i,new Stack(Item.HEAL_SPLASH,1));
        if(profile==Profile.DEBUFF) { slots.set(7,new Stack(Item.POISON_SPLASH,1));slots.set(8,new Stack(Item.SLOWNESS_SPLASH,1)); }
        return new KitProfile("practice-"+profile,slots,List.of(new Armor(ArmorSlot.HEAD,.08,160),
                new Armor(ArmorSlot.CHEST,.24,240),new Armor(ArmorSlot.LEGS,.20,220),new Armor(ArmorSlot.FEET,.08,160)));
    }
    public long epoch() { return epoch; }
    public Phase phase() { return phase; }
    public void connected(int side,boolean value) {
        side(side);connected[side]=value;input[side]=null;leftAt[side]=tick;
        if(!value) ready[side]=false;
        if(phase==Phase.QUEUE && connected[0] && connected[1]) { phase=Phase.READY;phaseAt=tick; }
    }
    public boolean submit(int side,Command c) {
        side(side);
        if(!connected[side] || c.sequence()<=sequence[side]) return false;
        sequence[side]=c.sequence(); // all rejections spend the high-water mark
        if(c.epoch()!=epoch || ++received[side]>4) return false;
        switch(c.op()) {
            case READY,REMATCH -> {
                if(phase!=Phase.READY && phase!=Phase.RESULT) return false;
                ready[side]=true;
                if(ready[0] && ready[1] && connected[0] && connected[1]) {
                    phase=Phase.WARMUP;phaseAt=tick;result="";epoch++;
                    sim=null;Arrays.fill(hits,0);Arrays.fill(combo,0);Arrays.fill(longest,0);Arrays.fill(pots,0);
                    Arrays.fill(ready,false);Arrays.fill(input,null);
                }
            }
            case QUEUE -> {
                if(phase!=Phase.RESULT) return false;
                ready[side]=false;input[side]=null; // transport moves this participant into a fresh room
            }
            case FORFEIT -> {
                if(phase!=Phase.LIVE && phase!=Phase.WARMUP) return false;
                finish("Forfeit: "+IDS[1-side]+" wins");
            }
            case SWAP -> {
                if(phase!=Phase.READY && phase!=Phase.QUEUE && phase!=Phase.RESULT) return false;
                var slots=new ArrayList<>(kits[side].slots());Collections.swap(slots,c.slot(),c.other());
                kits[side]=new KitProfile(kits[side].id(),slots,kits[side].armor());
            }
            case REFILL -> {
                if(phase!=Phase.LIVE || c.slot()>8 || sim.snapshot(IDS[side]).inventory().get(c.slot()).count()!=0) return false;
                var slots=sim.snapshot(IDS[side]).inventory();
                for(int n=9;n<slots.size();n++) if(slots.get(n).count()>0) {sim.swapSlots(IDS[side],c.slot(),n);return true;}
                return false;
            }
            case INPUT -> {
                if(phase!=Phase.LIVE) return false;
                input[side]=c;inputAt[side]=tick;frames[side]=c.frame();yaw[side]=c.yaw();pitch[side]=c.pitch();
                intent(side,new CombatIntent.Select(Math.min(8,c.slot())));
                if(c.attack()) intent(side,new CombatIntent.Swing(direction(c.yaw(),c.pitch())));
                if(c.use()) {
                    if(sim.snapshot(IDS[side]).inventory().get(Math.min(8,c.slot())).item()==Item.SWORD)
                        intent(side,new CombatIntent.Guard(true));
                    else intent(side,new CombatIntent.StartUse(direction(c.yaw(),c.pitch())));
                }
                if(c.release()) {intent(side,new CombatIntent.ReleaseUse(direction(c.yaw(),c.pitch())));intent(side,new CombatIntent.Guard(false));}
            }
        }
        return true;
    }
    private void intent(int side,CombatIntent.Action action) { sim.submit(IDS[side],new CombatIntent(++internalSequence,action)); }
    public static Vec3 direction(double yaw,double pitch) {
        double a=Math.toRadians(yaw),b=Math.toRadians(pitch);
        return new Vec3(Math.cos(a)*Math.cos(b),Math.sin(b),Math.sin(a)*Math.cos(b));
    }
    public void step() {
        tick++;Arrays.fill(received,0);events=List.of();
        if(phase==Phase.WARMUP && tick-phaseAt>=warmupTicks) start();
        if(phase==Phase.LIVE || phase==Phase.WARMUP) {
            boolean a=!connected[0] && tick-leftAt[0]>=graceTicks,b=!connected[1] && tick-leftAt[1]>=graceTicks;
            if(a || b) { finish(a && b?"Draw: both disconnected":"Disconnect: "+IDS[a?1:0]+" wins");return; }
        }
        if(phase!=Phase.LIVE) return;
        for(int i=0;i<2;i++) move(i);
        sim.step(1);events=sim.drainEvents();
        for(var e:events) {
            int actor="A".equals(e.actor())?0:"B".equals(e.actor())?1:-1;
            if(actor>=0 && e.type()==CombatEvent.Type.HIT) {
                hits[actor]++;combo[actor]++;combo[1-actor]=0;longest[actor]=Math.max(longest[actor],combo[actor]);
            }
            if(actor>=0 && e.type()==CombatEvent.Type.CONSUME && e.detail().endsWith("SPLASH")) pots[actor]++;
        }
        boolean a=sim.snapshot("A").alive(),b=sim.snapshot("B").alive();
        if(!a || !b) finish(!a && !b?"Draw: double knockout":IDS[a?0:1]+" wins by knockout");
        else if(tick-phaseAt>=roundTicks) finish("Draw: time limit");
    }
    private void start() {
        sim=new CombatSimulation(profile==Profile.NO_DEBUFF?CombatRules.practice():CombatRules.debuff(),world);
        for(int i=0;i<2;i++) sim.addParticipant(IDS[i],new CombatSimulation.Pose(new Vec3(0,1,i==0?-28:28),Vec3.ZERO,true),kits[i]);
        internalSequence=0;Arrays.fill(hits,0);Arrays.fill(combo,0);Arrays.fill(longest,0);Arrays.fill(pots,0);
        Arrays.fill(input,null);Arrays.fill(sprintHeld,false);yaw[0]=90;yaw[1]=-90;Arrays.fill(pitch,0);phase=Phase.LIVE;phaseAt=tick;
    }
    private void move(int side) {
        var body=sim.snapshot(IDS[side]);if(!body.alive()) return;
        Command c=input[side];boolean fresh=c!=null && connected[side] && tick-inputAt[side]<=5;
        boolean sprint=fresh && c.sprint() && c.forward()>0;
        // Submit transitions only; an attack stopping sprint requires a real release/repress.
        boolean requested=fresh && c.sprint();
        if(requested!=sprintHeld[side]) {intent(side,new CombatIntent.Sprint(sprint));sprintHeld[side]=requested;}
        if(!fresh) return;
        double f=c.forward(),s=c.strafe(),length=Math.hypot(f,s);if(length>1){f/=length;s/=length;}
        double angle=Math.toRadians(c.yaw()),speed=(body.sprinting() && sprint?.28:.215)*body.movementMultiplier();
        if(body.using() || body.guarding()) speed*=.4;
        Vec3 delta=new Vec3((f*Math.cos(angle)-s*Math.sin(angle))*speed,0,(f*Math.sin(angle)+s*Math.cos(angle))*speed);
        // Move input through collision separately, preserving the simulation's knockback velocity.
        var moved=world.move(body.pose().feet(),delta,CombatSimulation.HALF_WIDTH,CombatSimulation.HEIGHT);
        Vec3 v=body.pose().velocity();
        if(c.jump() && body.pose().grounded()) v=new Vec3(v.x(),.42,v.z());
        sim.setPose(IDS[side],new CombatSimulation.Pose(moved.feet(),v,moved.grounded() && v.y()<=0));
        sim.addExhaustion(IDS[side],delta.length()*(sprint?.1:.01));
    }
    private final boolean[] sprintHeld=new boolean[2];
    private void finish(String text) {phase=Phase.RESULT;result=text;phaseAt=tick;Arrays.fill(input,null);Arrays.fill(ready,false);}
    public State state() {
        var fighters=new ArrayList<Fighter>();
        for(int i=0;i<2;i++) {
            var body=sim==null || phase==Phase.READY || phase==Phase.QUEUE || phase==Phase.WARMUP ? preview(i) : sim.snapshot(IDS[i]);
            int remaining=body.inventory().stream().filter(s->s.item().name().endsWith("SPLASH")).mapToInt(Stack::count).sum();
            fighters.add(new Fighter(IDS[i],body,yaw[i],pitch[i],connected[i],ready[i],frames[i],sequence[i],new Score(hits[i],longest[i],pots[i],remaining,body.health())));
        }
        int remain=phase==Phase.WARMUP?Math.max(0,warmupTicks-(int)(tick-phaseAt)):phase==Phase.LIVE?Math.max(0,roundTicks-(int)(tick-phaseAt)):0;
        return new State(epoch,tick,sim==null?0:sim.tick(),phase,profile,remain,result,List.copyOf(fighters),
                sim!=null && phase==Phase.LIVE?sim.projectileViews():List.of(),events);
    }
    private CombatSimulation.Snapshot preview(int side) {
        return new CombatSimulation.Snapshot(IDS[side],new CombatSimulation.Pose(new Vec3(0,11,side==0?-44:44),Vec3.ZERO,true),20,true,20,5,0,
                kits[side].slots(),kits[side].armor(),List.of(),false,false,false,1,0,sequence[side],1);
    }
    private static void side(int side) {if(side<0 || side>1) throw new IllegalArgumentException("Participant side");}
}
