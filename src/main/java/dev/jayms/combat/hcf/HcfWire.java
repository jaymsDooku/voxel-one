package dev.jayms.combat.hcf;

import dev.jayms.combat.*;
import dev.jayms.combat.KitProfile.*;
import dev.jayms.combat.KitProfile.Stack;
import dev.jayms.combat.duel.DuelWire;
import java.io.*;
import java.util.*;

/** HCF-only TLS framing, bounded inputs and presentation. No practice/City protocol changes. */
public final class HcfWire {
    public static final int MAGIC=0x56484631,VERSION=1,PORT=25568;
    public static final int HELLO=1,WELCOME=2,COMMAND=3,STATE=4,ERROR=5;
    public record Hello(String name,char[] password,boolean register,int team) {}
    public record Welcome(String id,long season,long nextSequence) {}
    public static byte[] hello(Hello h) throws IOException {
        return DuelWire.frame(HELLO,o->{o.writeInt(MAGIC);o.writeInt(VERSION);o.writeUTF(HcfSession.WORLD_ID);o.writeUTF(h.name());
            o.writeShort(h.password().length);for(char c:h.password())o.writeChar(c);o.writeBoolean(h.register());o.writeByte(h.team());});
    }
    public static Hello hello(DataInputStream in) throws IOException {
        if(in.readInt()!=MAGIC || in.readInt()!=VERSION || !text(in,64).equals(HcfSession.WORLD_ID))throw new IOException("HCF protocol mismatch");
        String name=text(in,16);int length=in.readUnsignedShort();if(length<10 || length>128)throw new IOException("HCF password bound");
        char[] password=new char[length];try {
            for(int i=0;i<length;i++)password[i]=in.readChar();boolean register=in.readBoolean();int team=in.readUnsignedByte();
            if(!dev.jayms.net.AccountStore.validName(name) || team>1)throw new IOException("HCF identity bound");return new Hello(name,password,register,team);
        }catch(IOException e){Arrays.fill(password,'\0');throw e;}
    }
    public static byte[] welcome(Welcome w) throws IOException {return DuelWire.frame(WELCOME,o->{o.writeUTF(w.id());o.writeLong(w.season());o.writeLong(w.nextSequence());});}
    public static Welcome welcome(DataInputStream in) throws IOException {return new Welcome(text(in,16),in.readLong(),in.readLong());}
    public static byte[] command(HcfCommand c) throws IOException {
        return DuelWire.frame(COMMAND,o->{o.writeLong(c.season());o.writeLong(c.sequence());o.writeLong(c.frame());o.writeByte(c.op().ordinal());
            o.writeDouble(c.forward());o.writeDouble(c.strafe());o.writeDouble(c.yaw());o.writeDouble(c.pitch());
            o.writeBoolean(c.jump());o.writeBoolean(c.sprint());o.writeBoolean(c.attack());o.writeBoolean(c.use());o.writeBoolean(c.release());o.writeByte(c.slot());o.writeByte(c.choice());});
    }
    public static HcfCommand command(DataInputStream in) throws IOException {
        try{return new HcfCommand(in.readLong(),in.readLong(),in.readLong(),value(HcfCommand.Op.values(),in.readUnsignedByte()),
            in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readBoolean(),in.readBoolean(),in.readBoolean(),in.readBoolean(),in.readBoolean(),in.readUnsignedByte(),in.readUnsignedByte());}
        catch(IllegalArgumentException e){throw new IOException("Invalid HCF input",e);}
    }
    static String text(DataInputStream in,int max) throws IOException {String s=in.readUTF();if(s.length()>max)throw new IOException("HCF text bound");return s;}
    static <E> E value(E[] values,int n) throws IOException {if(n<0 || n>=values.length)throw new IOException("HCF enum bound");return values[n];}
    private static int count(DataInputStream in,int max) throws IOException {int n=in.readUnsignedShort();if(n>max)throw new IOException("HCF list bound");return n;}
    private static void vector(DataOutputStream o,Vec3 p) throws IOException {o.writeDouble(p.x());o.writeDouble(p.y());o.writeDouble(p.z());}
    private static Vec3 vector(DataInputStream i) throws IOException {return new Vec3(i.readDouble(),i.readDouble(),i.readDouble());}
    private static void stacks(DataOutputStream o,List<Stack> ss) throws IOException {o.writeShort(ss.size());for(var s:ss){o.writeByte(s.item().ordinal());o.writeByte(s.count());}}
    private static List<Stack> stacks(DataInputStream i) throws IOException {
        var ss=new ArrayList<Stack>();int n=count(i,36);for(int k=0;k<n;k++)ss.add(new Stack(value(Item.values(),i.readUnsignedByte()),i.readUnsignedByte()));return List.copyOf(ss);
    }
    private static void body(DataOutputStream o,CombatSimulation.Snapshot b) throws IOException {
        vector(o,b.pose().feet());vector(o,b.pose().velocity());o.writeBoolean(b.pose().grounded());o.writeDouble(b.health());o.writeBoolean(b.alive());
        o.writeDouble(b.hunger());o.writeDouble(b.saturation());o.writeInt(b.selectedSlot());stacks(o,b.inventory());
        o.writeShort(b.armor().size());for(var a:b.armor()){o.writeByte(a.slot().ordinal());o.writeDouble(a.protection());o.writeInt(a.durability());}
        o.writeShort(b.effects().size());for(var e:b.effects()){o.writeByte(e.effect().ordinal());o.writeLong(e.expiresAt());o.writeUTF(e.source());}
        o.writeBoolean(b.sprinting());o.writeBoolean(b.guarding());o.writeBoolean(b.using());o.writeDouble(b.movementMultiplier());o.writeLong(b.pearlReadyAt());o.writeLong(b.lastSequence());o.writeInt(b.life());
    }
    private static CombatSimulation.Snapshot body(DataInputStream i,String id) throws IOException {
        var pose=new CombatSimulation.Pose(vector(i),vector(i),i.readBoolean());double health=i.readDouble();boolean alive=i.readBoolean();double hunger=i.readDouble(),sat=i.readDouble();int slot=i.readInt();var slots=stacks(i);
        if(slots.size()!=36 || !Double.isFinite(health+hunger+sat) || health<0 || health>20 || hunger<0 || hunger>20 || sat<0 || sat>20 || slot<0 || slot>=36)throw new IOException("HCF body bound");
        var armor=new ArrayList<Armor>();int n=count(i,4);for(int k=0;k<n;k++)armor.add(new Armor(value(ArmorSlot.values(),i.readUnsignedByte()),i.readDouble(),i.readInt()));
        var effects=new ArrayList<CombatSimulation.EffectView>();n=count(i,4);for(int k=0;k<n;k++)effects.add(new CombatSimulation.EffectView(value(CombatEvent.Effect.values(),i.readUnsignedByte()),i.readLong(),text(i,16)));
        return new CombatSimulation.Snapshot(id,pose,health,alive,hunger,sat,slot,slots,List.copyOf(armor),List.copyOf(effects),i.readBoolean(),i.readBoolean(),i.readBoolean(),i.readDouble(),i.readLong(),i.readLong(),i.readInt());
    }
    public static byte[] state(HcfSession.State s) throws IOException {
        return DuelWire.frame(STATE,o->{
            o.writeLong(s.season());o.writeLong(s.tick());o.writeLong(s.now());o.writeByte(s.phase().ordinal());o.writeShort(s.members().size());
            for(var m:s.members()) {
                o.writeUTF(m.id());o.writeByte(m.team());o.writeByte(m.armor().ordinal());o.writeByte(m.active()==null?255:m.active().ordinal());
                o.writeLong(m.warmupUntil());o.writeInt(m.energy());o.writeLong(m.abilityReadyAt());o.writeLong(m.tagUntil());o.writeLong(m.spawnUntil());o.writeLong(m.deathbanUntil());
                o.writeBoolean(m.connected());o.writeBoolean(m.deployed());o.writeDouble(m.yaw());o.writeDouble(m.pitch());body(o,m.body());o.writeUTF(m.notice());o.writeInt(m.markedBy());o.writeLong(m.markUntil());o.writeLong(m.auraUntil());
            }
            for(var t:s.teams()){o.writeDouble(t.dtr());o.writeLong(t.freezeUntil());o.writeLong(t.regenAt());o.writeInt(t.stash());o.writeInt(t.score());o.writeBoolean(t.claimed());o.writeBoolean(t.raidable());}
            o.writeInt(s.capturingTeam());o.writeInt(s.captureTicks());o.writeBoolean(s.contested());o.writeLong(s.kothReadyAt());o.writeLong(s.restockAt());o.writeInt(s.captureGoal());
            o.writeShort(s.projectiles().size());for(var p:s.projectiles()){o.writeLong(p.id());o.writeByte(p.item().ordinal());o.writeUTF(p.source());vector(o,p.position());}
            int size=Math.min(128,s.events().size());o.writeShort(size);for(var e:s.events().subList(s.events().size()-size,s.events().size())) {
                o.writeLong(e.id());o.writeLong(e.tick());o.writeByte(e.type().ordinal());o.writeUTF(e.actor()==null?"":e.actor());o.writeUTF(e.target()==null?"":e.target());vector(o,e.position());o.writeDouble(e.amount());o.writeUTF(e.detail());
            }
            o.writeShort(s.loot().size());for(var l:s.loot()){o.writeLong(l.id());vector(o,l.feet());o.writeLong(l.expiresAt());stacks(o,l.items());}
        });
    }
    public static HcfSession.State state(DataInputStream i) throws IOException {
        try {
            long season=i.readLong(),tick=i.readLong(),now=i.readLong();var phase=value(HcfSession.Phase.values(),i.readUnsignedByte());
            var members=new ArrayList<HcfSession.MemberView>();int n=count(i,16);
            for(int k=0;k<n;k++) {
                String id=text(i,16);int team=i.readUnsignedByte();var armor=value(HcfRole.values(),i.readUnsignedByte());int ordinal=i.readUnsignedByte();var active=ordinal==255?null:value(HcfRole.values(),ordinal);
                long warmup=i.readLong();int energy=i.readInt();long ability=i.readLong(),tag=i.readLong(),spawn=i.readLong(),ban=i.readLong();
                boolean connected=i.readBoolean(),deployed=i.readBoolean();double yaw=i.readDouble(),pitch=i.readDouble();var b=body(i,id);String notice=text(i,256);
                if(team>1 || energy<0 || energy>100 || !Double.isFinite(yaw+pitch))throw new IOException("HCF member view bound");
                members.add(new HcfSession.MemberView(id,team,armor,active,warmup,energy,ability,tag,spawn,ban,connected,deployed,yaw,pitch,b,notice,i.readInt(),i.readLong(),i.readLong()));
            }
            var teams=new ArrayList<HcfSession.TeamView>();for(int k=0;k<2;k++)teams.add(new HcfSession.TeamView(k,i.readDouble(),i.readLong(),i.readLong(),i.readInt(),i.readInt(),i.readBoolean(),i.readBoolean()));
            int capturing=i.readInt(),capture=i.readInt();boolean contested=i.readBoolean();long ready=i.readLong(),restock=i.readLong();int goal=i.readInt();
            var projectiles=new ArrayList<CombatSimulation.ProjectileView>();n=count(i,128);for(int k=0;k<n;k++)projectiles.add(new CombatSimulation.ProjectileView(i.readLong(),value(Item.values(),i.readUnsignedByte()),text(i,16),vector(i)));
            var events=new ArrayList<CombatEvent>();n=count(i,128);for(int k=0;k<n;k++) {
                long id=i.readLong(),at=i.readLong();var type=value(CombatEvent.Type.values(),i.readUnsignedByte());String a=text(i,16),b=text(i,16);events.add(new CombatEvent(id,at,type,a.isEmpty()?null:a,b.isEmpty()?null:b,vector(i),i.readDouble(),text(i,64)));
            }
            var loot=new ArrayList<HcfSession.LootView>();n=count(i,16);for(int k=0;k<n;k++)loot.add(new HcfSession.LootView(i.readLong(),vector(i),i.readLong(),stacks(i)));
            return new HcfSession.State(season,tick,now,phase,List.copyOf(members),List.copyOf(teams),capturing,capture,contested,ready,restock,goal,List.copyOf(projectiles),List.copyOf(events),List.copyOf(loot));
        }catch(IllegalArgumentException e){throw new IOException("Invalid HCF state",e);}
    }
    private HcfWire() {}
}
