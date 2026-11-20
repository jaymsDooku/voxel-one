package dev.jayms.combat.hcf;

import dev.jayms.combat.*;
import dev.jayms.combat.KitProfile.*;
import dev.jayms.combat.KitProfile.Stack;
import dev.jayms.combat.duel.DuelSession;
import java.io.*;
import java.util.*;
import java.util.function.LongSupplier;
import static dev.jayms.combat.CombatEvent.*;

/** Single-owner faction simulation. All public mutations are trusted server calls, never wire poses. */
public final class HcfSession {
    public static final String WORLD_ID="hcf-basalt-v1";
    public enum Phase { OPENING, ACTIVE, ENDED }
    public record MemberView(String id,int team,HcfRole armor,HcfRole active,long warmupUntil,int energy,
                             long abilityReadyAt,long tagUntil,long spawnUntil,long deathbanUntil,boolean connected,
                             boolean deployed,double yaw,double pitch,CombatSimulation.Snapshot body,String notice,
                             int markedBy,long markUntil,long auraUntil) {}
    public record TeamView(int team,double dtr,long freezeUntil,long regenAt,int stash,int score,boolean claimed,boolean raidable) {}
    public record LootView(long id,Vec3 feet,long expiresAt,List<Stack> items) {public LootView {items=List.copyOf(items);}}
    public record State(long season,long tick,long now,Phase phase,List<MemberView> members,List<TeamView> teams,
                        int capturingTeam,int captureTicks,boolean contested,long kothReadyAt,long restockAt,int captureGoal,
                        List<CombatSimulation.ProjectileView> projectiles,List<CombatEvent> events,List<LootView> loot) {}
    private static final class Member {
        final String id; final int team;
        HcfRole armor,active; long warmup,ability,tag,spawn,ban,inputAt,sequence;int energy=100,received;
        boolean connected,deployed,sprintHeld; double yaw,pitch; HcfCommand input; String notice="";
        Member(HcfStore.Member s) {id=s.id();team=s.team();armor=HcfRole.values()[s.role()];ban=s.deathbanUntil();deployed=s.deployed();yaw=team==0?90:-90;}
    }
    private static final class Team {
        double dtr;long freeze,regen;int stash,score;boolean claimed;
        Team(HcfStore.Team s) {dtr=s.dtr();freeze=s.freezeUntil();regen=s.regenAt();stash=s.stash();score=s.score();claimed=s.claimed();}
    }
    private record Mark(String source,int team,long until) {}
    private record Aura(String source,long until) {}
    private final HcfRules rules;
    private final CombatWorld world;
    private final HcfStore store;
    private final LongSupplier clock;
    private final LinkedHashMap<String,Member> members=new LinkedHashMap<>();
    private final Team[] teams=new Team[2];
    private final Map<String,Mark> marks=new HashMap<>();
    private final Map<String,Aura> auras=new HashMap<>();
    private final List<LootView> loot=new ArrayList<>();
    private CombatSimulation sim;
    private long season,tick,now,intentSequence,nextLoot,kothReady;
    private Phase phase;
    private int capturing=-1,capture;
    private boolean contested,restoring=true,batching,dirty;
    private List<CombatEvent> events=List.of();
    public HcfSession(CombatWorld world,HcfStore store,HcfRules rules,LongSupplier clock) throws IOException {
        this.world=Objects.requireNonNull(world);this.store=Objects.requireNonNull(store);this.rules=Objects.requireNonNull(rules);this.clock=Objects.requireNonNull(clock);
        now=clock.getAsLong();if(now<0)throw new IllegalArgumentException("Clock");
        var saved=store.load();season=saved.season();phase=Phase.values()[saved.phase()];
        for(int i=0;i<2;i++)teams[i]=new Team(saved.teams().get(i));
        var base=CombatRules.hcf();
        sim=new CombatSimulation(baseWithCaps(base),world,new CombatSimulation.Policy() {
            public boolean damage(String source,String target,DamageKind kind) {
                Member b=members.get(target),a=members.get(source);
                if(kind==DamageKind.PEARL) return true;
                if(!vulnerable(b))return false;
                if(a==null)return true;
                return a!=b && a.team!=b.team && vulnerable(a);
            }
            public double multiplier(String source,String target,DamageKind kind) {
                Member a=members.get(source),b=members.get(target);if(a==null || b==null)return 1;
                double value=1;Mark mark=marks.get(target);
                if(mark!=null && mark.team==a.team && mark.until>tick)value*=1.25;
                if(aura(a))value*=1.15;
                if(kind==DamageKind.MELEE && backstab(a,b))value*=2;
                return value;
            }
            public void damaged(String source,String target,DamageKind kind,double amount) {
                Member a=members.get(source),b=members.get(target);if(a==null || b==null || a==b)return;
                tag(a,b);
                if(kind==DamageKind.ARROW && role(a,HcfRole.ARCHER))marks.put(target,new Mark(a.id,a.team,tick+rules.markTicks()));
                if(kind==DamageKind.MELEE && backstab(a,b))a.ability=tick+rules.rogueCooldownTicks();
            }
            public boolean harmfulEffect(String source,String target,Effect effect) {return damage(source,target,DamageKind.POISON);}
            public void affected(String source,String target,Effect effect) {tag(members.get(source),members.get(target));}
            public boolean movement(String id,Vec3 from,Vec3 to) {return entry(members.get(id),from,to);}
            public boolean teleport(String id,Vec3 to) {return entry(members.get(id),sim.snapshot(id).pose().feet(),to);}
        });
        for(var savedMember:saved.members()) {
            var m=new Member(savedMember);members.put(m.id,m);
            sim.addParticipant(m.id,pose(spawn(m.team)),m.armor.kit());m.spawn=tick+rules.spawnTicks();
            // Persisted defeat semantics: deployment is durable before leaving protected spawn.
            // A crash/restart cannot erase a combat body or bypass the deathban/DTR transaction.
            if(m.deployed) defeat(m,false);
            else if(m.ban>now) sim.defeat(m.id); // Keep banned presentation consistent across restart, without another DTR loss.
        }
        for(var drop:saved.drops()) {
            nextLoot=Math.max(nextLoot,drop.id());if(drop.expiresAt()>now)loot.add(new LootView(drop.id(),drop.feet(),drop.expiresAt(),drop.items()));
        }
        sim.drainEvents();restoring=false;persist();
    }
    private static CombatRules baseWithCaps(CombatRules b) {
        return new CombatRules(b.id(),b.harmfulEffects(),b.damage(),b.physics(),b.supplies(),new CombatRules.Limits(16,128,8,200,200));
    }
    public HcfRules rules() {return rules;}
    public CombatSimulation.Snapshot body(String id) {return sim.snapshot(id);}
    public long season() {return season;}
    public long sequence(String id) {return member(id).sequence;}
    public boolean connected(String id) {return member(id).connected;}
    CombatSimulation simulation() {return sim;}
    public static Vec3 spawn(int team) {return new Vec3(team==0?-3:3,1,team==0?-30:30);}
    public static Vec3 exit(int team) {return new Vec3(team==0?-3:3,1,team==0?-23:23);}
    private static CombatSimulation.Pose pose(Vec3 p) {return new CombatSimulation.Pose(p,Vec3.ZERO,true);}
    private boolean protectedZone(Vec3 p) {return Math.abs(p.z())>=26-.3;}
    private int claim(Vec3 p) {return Math.abs(p.z())<=12.3 && Math.abs(p.x())>=19.7 && Math.abs(p.x())<=30.3 ? (p.x()<0?0:1):-1;}
    private boolean tagged(Member m) {return m.tag>tick;}
    private boolean vulnerable(Member m) {
        return m!=null && phase==Phase.ACTIVE && m.deployed && m.spawn<=tick && sim.snapshot(m.id).alive() && !protectedZone(sim.snapshot(m.id).pose().feet());
    }
    private void tag(Member a,Member b) {if(a==null || b==null || a.team==b.team)return;a.tag=b.tag=tick+rules.combatTagTicks();}
    private boolean aura(Member m) {
        Aura a=auras.get(m.id);Member source=a==null?null:members.get(a.source);
        return a!=null && a.until>tick && source!=null && role(source,HcfRole.BARD) && source.connected && sim.snapshot(source.id).alive()
                && source.team==m.team && sim.snapshot(source.id).pose().feet().distance(sim.snapshot(m.id).pose().feet())<=8;
    }
    private boolean role(Member m,HcfRole expected) {
        return m.active==expected && HcfRole.from(body(m.id).armor()).orElse(null)==expected;
    }
    private boolean backstab(Member a,Member b) {
        if(!role(a,HcfRole.ROGUE) || a.ability>tick)return false;
        Vec3 offset=sim.snapshot(a.id).pose().feet().subtract(sim.snapshot(b.id).pose().feet());
        double length=Math.hypot(offset.x(),offset.z());if(length<.1 || length>3 || Math.abs(offset.y())>1.5)return false;
        Vec3 forward=DuelSession.direction(b.yaw,0);
        return (offset.x()*forward.x()+offset.z()*forward.z())/length<=-.65;
    }
    private boolean entry(Member m,Vec3 from,Vec3 to) {
        if(m==null)return false;
        if(protectedZone(to)) {
            boolean own=m.team==0?to.z()<0:to.z()>0;
            if(!own || tagged(m))return false;
        }
        for(int side=0;side<2;side++)if(side!=m.team && teams[side].claimed && (teams[side].dtr>0 || tagged(m))) {
            if(claim(from)==side) {
                // When DTR recovers, an intruder may retreat out; no deeper movement or trapping.
                if(claim(to)==side && claimDepth(to,side)>=claimDepth(from,side)-1e-9)return false;
            } else if(crossesClaim(from,to,side)) return false;
        }
        return true;
    }
    private static double claimDepth(Vec3 p,int side) {
        double min=side==0?-30.3:19.7,max=side==0?-19.7:30.3;
        return Math.min(Math.min(p.x()-min,max-p.x()),12.3-Math.abs(p.z()));
    }
    private static boolean crossesClaim(Vec3 from,Vec3 to,int side) {
        double[] start={from.x(),from.z()},delta={to.x()-from.x(),to.z()-from.z()};
        double[] low={side==0?-30.3:19.7,-12.3},high={side==0?-19.7:30.3,12.3};double near=0,far=1;
        for(int i=0;i<2;i++) {
            if(Math.abs(delta[i])<1e-12){if(start[i]<low[i] || start[i]>high[i])return false;}
            else {double a=(low[i]-start[i])/delta[i],b=(high[i]-start[i])/delta[i];near=Math.max(near,Math.min(a,b));far=Math.min(far,Math.max(a,b));if(near>far)return false;}
        }
        return true;
    }
    public boolean known(String id) {return members.containsKey(id);}
    public int rosterSize() {return members.size();}
    public void connect(String id,int team) throws IOException {
        if(id==null || !id.matches("[a-z0-9_]{3,16}") || team<0 || team>1)throw new IOException("HCF identity");
        Member m=members.get(id);
        if(m==null) {
            if(members.size()>=16 || members.values().stream().filter(x->x.team==team).count()>=8)throw new IOException("Team roster full");
            m=new Member(new HcfStore.Member(id,team,0,0,false));members.put(id,m);sim.addParticipant(id,pose(spawn(team)),m.armor.kit());
            m.spawn=tick+rules.spawnTicks();
        }
        m.connected=true;m.input=null;m.received=0;m.active=null;m.warmup=tick+rules.warmupTicks();m.notice="Choose armor in spawn; R deploys. Combat is opt-in.";
        persist();
    }
    public void disconnect(String id) throws IOException {
        Member m=member(id);if(!m.connected)return;
        boolean combatLogout=m.deployed && tagged(m) && sim.snapshot(id).alive();
        m.connected=false;m.input=null;
        if(combatLogout) {sim.defeat(id);processEvents();}
        cleanup(m);
        // Untagged disconnect returns to spawn without bonus supplies; deployed crash flag clears atomically.
        m.deployed=false;if(sim.snapshot(id).alive())sim.setPose(id,pose(spawn(m.team)));persist();
    }
    private Member member(String id) {Member m=members.get(id);if(m==null)throw new IllegalArgumentException("Unknown HCF member");return m;}
    private void cleanup(Member m) {
        m.active=null;m.input=null;m.energy=100;m.sprintHeld=false;sim.clearActions(m.id);
        marks.remove(m.id);marks.entrySet().removeIf(e->e.getValue().source.equals(m.id));auras.remove(m.id);auras.entrySet().removeIf(e->e.getValue().source.equals(m.id));
    }
    public boolean submit(String id,HcfCommand c) throws IOException {
        Member m=member(id);if(!m.connected || c.sequence()<=m.sequence)return false;
        m.sequence=c.sequence();if(c.season()!=season || ++m.received>4)return reject(m,"Stale season or input rate limit");
        if(c.op()==HcfCommand.Op.SAFE_LOGOUT) {
            if(tagged(m) || !protectedZone(body(id).pose().feet()))return reject(m,"Safe logout needs your spawn and no combat tag");
            disconnect(id);return true;
        }
        if(now<m.ban)return reject(m,"Deathban: "+((m.ban-now+999)/1000)+"s");
        if(!body(id).alive()) {sim.recover(id,pose(spawn(m.team)),m.armor.kit());m.spawn=tick+rules.spawnTicks();}
        switch(c.op()) {
            case EQUIP -> {
                if(m.deployed || !protectedZone(body(id).pose().feet()) || tagged(m))return reject(m,"Armor changes only in spawn");
                if(c.choice()>=HcfRole.values().length)return false;
                HcfRole role=HcfRole.values()[c.choice()];
                long count=members.values().stream().filter(x->x!=m && x.team==m.team && x.armor==role).count();
                if(count>=role.limit)return reject(m,"Team class limit: "+role.limit);
                cleanup(m);m.armor=role;m.warmup=tick+rules.warmupTicks();sim.equip(id,role.kit(),false);persist();
            }
            case DEPLOY -> {
                if(phase!=Phase.ACTIVE || tagged(m) || m.deployed)return reject(m,"Deploy needs an active season and untagged spawn");
                m.deployed=true;persist();sim.setPose(id,pose(exit(m.team)));m.notice="Deployed. Spawn protection blocks outgoing hits too.";
            }
            case HOME -> {
                if(tagged(m))return reject(m,"Combat tag blocks travel and protected entry");
                m.deployed=false;cleanup(m);sim.setPose(id,pose(spawn(m.team)));m.spawn=tick+rules.spawnTicks();m.warmup=tick+rules.warmupTicks();persist();
            }
            case RESUPPLY -> {
                if(m.deployed || tagged(m) || !protectedZone(body(id).pose().feet()) || teams[m.team].stash<1)return reject(m,"Resupply in spawn: one team stash credit");
                teams[m.team].stash--;cleanup(m);sim.equip(id,m.armor.kit(),true);m.warmup=tick+rules.warmupTicks();persist();
            }
            case CLAIM -> {
                if(m.deployed || phase==Phase.ENDED)return reject(m,"Claim from spawn before season end");
                if(!teams[m.team].claimed) {teams[m.team].claimed=true;persist();}
                m.notice="Claim at "+(m.team==0?"x -30..-20":"x 20..30")+", z -12..12. Raidable at DTR <= 0.";
            }
            case RAID -> {
                int enemy=1-m.team;
                if(phase!=Phase.ACTIVE || !m.deployed || tagged(m) || claim(body(id).pose().feet())!=enemy || !teams[enemy].claimed || teams[enemy].dtr>0 || teams[enemy].stash<1 || teams[m.team].stash>=32)
                    return reject(m,"Raid needs untagged entry into a raidable enemy claim with supplies");
                teams[enemy].stash--;teams[m.team].stash++;persist();m.notice="Raided one supply credit. Refill your kit at spawn.";
            }
            case ABILITY -> {
                if(phase!=Phase.ACTIVE || !m.deployed || m.active==null || m.ability>tick)return reject(m,"Class warming up or ability cooling down");
                if(m.active==HcfRole.BARD) {
                    if(m.energy<30)return reject(m,"Bard needs 30 energy");
                    m.energy-=30;m.ability=tick+rules.auraTicks();
                    for(Member ally:members.values())if(ally.team==m.team && ally.connected && ally.deployed && body(ally.id).alive() && body(ally.id).pose().feet().distance(body(id).pose().feet())<=8)
                        auras.put(ally.id,new Aura(id,tick+rules.auraTicks()));
                    m.notice="Ally aura: speed +15%, damage +15%, "+((rules.auraTicks()+19)/20)+"s; energy -30 once.";
                } else if(m.active==HcfRole.MINER) {
                    if(claim(body(id).pose().feet())!=m.team || !teams[m.team].claimed || tagged(m) || teams[m.team].stash>=32)return reject(m,"Miner supply work needs your untagged claim");
                    teams[m.team].stash++;m.ability=tick+1200;persist();m.notice="Miner recovered one team supply credit.";
                } else return reject(m,"Passive class: Fighter frontline, Archer marks, Rogue rear hits");
            }
            case LOOT -> {
                if(!m.deployed || phase!=Phase.ACTIVE)return false;
                for(var drop:List.copyOf(loot))if(drop.feet().distance(body(id).pose().feet())<=2) {
                    var left=sim.collect(id,drop.items());loot.remove(drop);if(!left.isEmpty())loot.add(new LootView(drop.id(),drop.feet(),drop.expiresAt(),left));persist();break;
                }
            }
            case REFILL -> {
                if(c.slot()>8 || body(id).inventory().get(c.slot()).count()!=0)return false;
                var slots=body(id).inventory();for(int i=9;i<36;i++)if(slots.get(i).count()>0){sim.swapSlots(id,c.slot(),i);return true;}return false;
            }
            case INPUT -> {
                m.yaw=c.yaw();m.pitch=c.pitch();m.inputAt=tick;m.input=c;
                if(phase!=Phase.ACTIVE || !m.deployed)return true;
                intent(id,new CombatIntent.Select(Math.min(c.slot(),8)));
                if(c.attack())intent(id,new CombatIntent.Swing(DuelSession.direction(c.yaw(),c.pitch())));
                if(c.use()) {
                    if(body(id).inventory().get(Math.min(c.slot(),8)).item()==Item.SWORD)intent(id,new CombatIntent.Guard(true));
                    else intent(id,new CombatIntent.StartUse(DuelSession.direction(c.yaw(),c.pitch())));
                }
                if(c.release()) {intent(id,new CombatIntent.ReleaseUse(DuelSession.direction(c.yaw(),c.pitch())));intent(id,new CombatIntent.Guard(false));}
            }
            default -> throw new IllegalStateException("HCF operation");
        }
        return true;
    }
    private boolean reject(Member m,String text) {m.notice=text;return false;}
    private void intent(String id,CombatIntent.Action action) {sim.submit(id,new CombatIntent(++intentSequence,action));}
    public void step() throws IOException {
        long current=clock.getAsLong();if(current<now)current=now;now=current;tick++;
        for(Member m:members.values()) {
            m.received=0;if(!m.connected || !body(m.id).alive())continue;
            Optional<HcfRole> detected=HcfRole.from(body(m.id).armor());
            if(detected.orElse(null)!=m.armor) {cleanup(m);m.warmup=tick+rules.warmupTicks();}
            if(m.active==null && detected.orElse(null)==m.armor && tick>=m.warmup)m.active=m.armor;
            if(tick%20==0)m.energy=Math.min(100,m.energy+1);
            if(phase==Phase.ACTIVE && m.deployed)move(m);
        }
        sim.step(1);processEvents();
        for(Member m:members.values())if(m.deployed && body(m.id).alive() && protectedZone(body(m.id).pose().feet()) && !tagged(m)) {
            m.deployed=false;cleanup(m);sim.setPose(m.id,pose(spawn(m.team)));persist();
        }
        marks.entrySet().removeIf(e->e.getValue().until<=tick);
        auras.entrySet().removeIf(e->e.getValue().until<=tick);
        boolean expiredLoot=loot.removeIf(l->l.expiresAt()<=now);
        boolean changed=expiredLoot;
        if(phase==Phase.ACTIVE) {
            for(Team t:teams) {
                if(now>=t.freeze && t.dtr<3 && now>=t.regen) {t.dtr=Math.min(3,t.dtr+.25);t.regen=now+rules.dtrRegenMillis();changed=true;}
                if(tick%rules.restockTicks()==0 && t.stash<32){t.stash++;changed=true;}
            }
            changed|=koth();
        }
        if(changed)persist();
    }
    private void move(Member m) {
        HcfCommand c=m.input;boolean fresh=c!=null && tick-m.inputAt<=5;
        boolean requested=fresh && c.sprint(),sprint=requested && c.forward()>0;
        if(!requested && m.sprintHeld){intent(m.id,new CombatIntent.Sprint(false));m.sprintHeld=false;}
        else if(sprint && !m.sprintHeld){intent(m.id,new CombatIntent.Sprint(true));m.sprintHeld=true;}
        if(!fresh)return;
        var b=body(m.id);double f=c.forward(),s=c.strafe(),n=Math.hypot(f,s);if(n>1){f/=n;s/=n;}
        double speed=(b.sprinting() && sprint?.28:.215)*b.movementMultiplier()*(aura(m)?1.15:1);
        if(m.active==HcfRole.MINER && claim(b.pose().feet())==m.team)speed*=1.1;
        if(b.using() || b.guarding())speed*=.4;
        double a=Math.toRadians(c.yaw());Vec3 delta=new Vec3((f*Math.cos(a)-s*Math.sin(a))*speed,0,(f*Math.sin(a)+s*Math.cos(a))*speed);
        var next=world.move(b.pose().feet(),delta,CombatSimulation.HALF_WIDTH,CombatSimulation.HEIGHT);
        if(!entry(m,b.pose().feet(),next.feet())){m.notice="Region entry blocked: combat tag, enemy spawn or protected claim";return;}
        Vec3 velocity=b.pose().velocity();if(c.jump() && b.pose().grounded())velocity=new Vec3(velocity.x(),.42,velocity.z());
        sim.setPose(m.id,new CombatSimulation.Pose(next.feet(),velocity,next.grounded() && velocity.y()<=0));sim.addExhaustion(m.id,delta.length()*(sprint?.1:.01));
    }
    private void processEvents() throws IOException {
        events=sim.drainEvents();
        for(var event:events)if(event.type()==Type.DEATH)defeat(member(event.target()),true);
        // Armor may break in this tick. Remove role bonuses before the next action/ability.
        for(Member m:members.values())if(m.active!=null && HcfRole.from(body(m.id).armor()).orElse(null)!=m.active)cleanup(m);
    }
    private void defeat(Member m,boolean drop) throws IOException {
        var b=body(m.id);if(drop && loot.size()<16)loot.add(new LootView(++nextLoot,b.pose().feet(),now+60_000,b.inventory()));
        Team t=teams[m.team];t.dtr=Math.max(-3,t.dtr-1);t.freeze=now+rules.dtrFreezeMillis();t.regen=t.freeze+rules.dtrRegenMillis();
        m.ban=now+rules.deathbanMillis();m.deployed=false;m.tag=0;cleanup(m);
        if(b.alive()) { // restart: reconstruct a dead body without re-running DTR loss
            sim.setPose(m.id,pose(exit(m.team)));
            // Policy allows world damage only on a deployed active actor; direct defeat below bypasses that seam.
        }
        sim.defeat(m.id);m.notice="Defeated. Deathban and faction DTR persisted.";persist();
    }
    private boolean koth() {
        if(tick<kothReady)return false;
        boolean[] present=new boolean[2];
        for(Member m:members.values())if(m.connected && vulnerable(m)) {
            var p=body(m.id).pose().feet();if(Math.abs(p.x())<=4 && Math.abs(p.z())<=4 && p.y()<4)present[m.team]=true;
        }
        contested=present[0] && present[1];int owner=present[0]==present[1]?-1:present[0]?0:1;
        if(owner<0){capturing=-1;capture=0;return false;}
        if(capturing!=owner){capturing=owner;capture=0;}
        if(++capture>=rules.captureTicks()) {
            teams[owner].score=Math.min(1_000_000,teams[owner].score+1);teams[owner].stash=Math.min(32,teams[owner].stash+4);
            capturing=-1;capture=0;kothReady=tick+600;return true;
        }
        return false;
    }
    public State state() {
        var views=new ArrayList<MemberView>();
        for(Member m:members.values()) {
            Mark mark=marks.get(m.id);Aura buff=auras.get(m.id);
            views.add(new MemberView(m.id,m.team,m.armor,m.active,m.warmup,m.energy,m.ability,m.tag,m.spawn,m.ban,m.connected,m.deployed,m.yaw,m.pitch,body(m.id),m.notice,
                mark==null?-1:mark.team,mark==null?0:mark.until,aura(m) && buff!=null?buff.until:0));
        }
        var ts=new ArrayList<TeamView>();for(int i=0;i<2;i++){var t=teams[i];ts.add(new TeamView(i,t.dtr,t.freeze,t.regen,t.stash,t.score,t.claimed,t.claimed && t.dtr<=0));}
        return new State(season,tick,now,phase,List.copyOf(views),List.copyOf(ts),capturing,capture,contested,kothReady,(tick/rules.restockTicks()+1)*rules.restockTicks(),rules.captureTicks(),sim.projectileViews(),events,List.copyOf(loot));
    }
    /** One server-tick transaction; reader admissions cannot interleave while the server lock is held. */
    void beginBatch() {
        if(batching)throw new IllegalStateException("Nested HCF transaction");batching=true;dirty=false;
    }
    void commitBatch() throws IOException {
        if(!batching)throw new IllegalStateException("No HCF transaction");batching=false;
        if(dirty){persist();dirty=false;}
    }
    private void persist() throws IOException {
        if(batching){dirty=true;return;}
        if(restoring)return; // Reconstruct the whole roster/loot before one restart-defeat transaction.
        var ms=members.values().stream().map(m->new HcfStore.Member(m.id,m.team,m.armor.ordinal(),m.ban,m.deployed)).toList();
        var ts=Arrays.stream(teams).map(t->new HcfStore.Team(t.dtr,t.freeze,t.regen,t.stash,t.score,t.claimed)).toList();
        var drops=loot.stream().map(l->new HcfStore.Drop(l.id(),l.feet(),l.expiresAt(),l.items())).toList();
        store.save(new HcfStore.Saved(season,phase.ordinal(),ms,ts,drops));
    }
}
