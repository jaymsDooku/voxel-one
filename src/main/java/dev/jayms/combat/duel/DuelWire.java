package dev.jayms.combat.duel;

import dev.jayms.combat.*;
import dev.jayms.combat.KitProfile.*;
import dev.jayms.combat.KitProfile.Stack;
import java.io.*;
import java.util.*;

/** Separate opt-in wire identity. Existing Protocol.VERSION and save schemas are unchanged. */
public final class DuelWire {
    public static final int MAGIC=0x56504431,VERSION=1,PORT=25567,MAX_FRAME=65536;
    public static final int HELLO=1,WELCOME=2,COMMAND=3,STATE=4,ERROR=5;
    public record Hello(DuelSession.Profile profile,boolean spectator,String match,String resume) {}
    public record Welcome(String match,int side,String resume,long nextSequence) {}
    public interface Write { void write(DataOutputStream out) throws IOException; }
    public static byte[] frame(int kind,Write writer) throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeByte(kind);writer.write(out);out.flush();
        if(bytes.size()>MAX_FRAME) throw new IOException("Duel frame bound");return bytes.toByteArray();
    }
    public static void send(DataOutputStream out,byte[] bytes) throws IOException {
        if(bytes.length<1 || bytes.length>MAX_FRAME) throw new IOException("Duel frame bound");
        out.writeInt(bytes.length);out.write(bytes);out.flush();
    }
    public static DataInputStream read(DataInputStream in) throws IOException {
        int n=in.readInt();if(n<1 || n>MAX_FRAME) throw new IOException("Duel frame bound");
        byte[] bytes=new byte[n];in.readFully(bytes);return new DataInputStream(new ByteArrayInputStream(bytes));
    }
    public static void end(DataInputStream in) throws IOException {if(in.available()!=0) throw new IOException("Trailing duel data");}
    private static <E> E value(E[] values,int n) throws IOException {
        if(n<0 || n>=values.length) throw new IOException("Duel enum bound");return values[n];
    }
    private static String string(DataInputStream in,int bound) throws IOException {
        String s=in.readUTF();if(s.length()>bound) throw new IOException("Duel string bound");return s;
    }
    private static String nullable(DataInputStream in) throws IOException {String s=string(in,64);return s.isEmpty()?null:s;}
    private static void text(DataOutputStream out,String s) throws IOException {out.writeUTF(s==null?"":s);}
    public static byte[] hello(Hello h) throws IOException {
        return frame(HELLO,o->{o.writeInt(MAGIC);o.writeInt(VERSION);o.writeUTF(DuelSession.WORLD_ID);
            o.writeByte(h.profile().ordinal());o.writeBoolean(h.spectator());text(o,h.match());text(o,h.resume());});
    }
    public static Hello hello(DataInputStream in) throws IOException {
        if(in.readInt()!=MAGIC || in.readInt()!=VERSION || !string(in,64).equals(DuelSession.WORLD_ID)) throw new IOException("Practice protocol mismatch");
        return new Hello(value(DuelSession.Profile.values(),in.readUnsignedByte()),in.readBoolean(),string(in,64),string(in,64));
    }
    public static byte[] welcome(Welcome w) throws IOException {
        return frame(WELCOME,o->{o.writeUTF(w.match());o.writeInt(w.side());o.writeUTF(w.resume());o.writeLong(w.nextSequence());});
    }
    public static Welcome welcome(DataInputStream in) throws IOException {
        String match=string(in,64);int side=in.readInt();String resume=string(in,64);long next=in.readLong();
        if(side < -1 || side>1 || next<1) throw new IOException("Welcome bound");return new Welcome(match,side,resume,next);
    }
    public static byte[] command(DuelSession.Command c) throws IOException {
        return frame(COMMAND,o->{o.writeLong(c.epoch());o.writeLong(c.sequence());o.writeLong(c.frame());o.writeByte(c.op().ordinal());
            o.writeDouble(c.forward());o.writeDouble(c.strafe());o.writeDouble(c.yaw());o.writeDouble(c.pitch());
            o.writeBoolean(c.jump());o.writeBoolean(c.sprint());o.writeBoolean(c.attack());o.writeBoolean(c.use());o.writeBoolean(c.release());o.writeByte(c.slot());o.writeByte(c.other());});
    }
    public static DuelSession.Command command(DataInputStream in) throws IOException {
        try { return new DuelSession.Command(in.readLong(),in.readLong(),in.readLong(),value(DuelSession.Op.values(),in.readUnsignedByte()),
                in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readBoolean(),in.readBoolean(),in.readBoolean(),in.readBoolean(),in.readBoolean(),in.readUnsignedByte(),in.readUnsignedByte());
        } catch(IllegalArgumentException e) {throw new IOException("Invalid duel input");}
    }
    private static void vector(DataOutputStream out,Vec3 v) throws IOException {out.writeDouble(v.x());out.writeDouble(v.y());out.writeDouble(v.z());}
    private static Vec3 vector(DataInputStream in) throws IOException {return new Vec3(in.readDouble(),in.readDouble(),in.readDouble());}
    private static int count(DataInputStream in,int max) throws IOException {int n=in.readUnsignedShort();if(n>max) throw new IOException("Duel list bound");return n;}
    public static byte[] state(DuelSession.State s) throws IOException {
        return frame(STATE,o->{
            o.writeLong(s.epoch());o.writeLong(s.tick());o.writeLong(s.combatTick());o.writeByte(s.phase().ordinal());o.writeByte(s.profile().ordinal());o.writeInt(s.remainingTicks());text(o,s.result());
            for(var f:s.fighters()) {
                text(o,f.id());var b=f.body();vector(o,b.pose().feet());vector(o,b.pose().velocity());o.writeBoolean(b.pose().grounded());
                o.writeDouble(b.health());o.writeBoolean(b.alive());o.writeDouble(b.hunger());o.writeDouble(b.saturation());o.writeInt(b.selectedSlot());
                o.writeShort(b.inventory().size());for(var stack:b.inventory()){o.writeByte(stack.item().ordinal());o.writeByte(stack.count());}
                o.writeShort(b.armor().size());for(var a:b.armor()){o.writeByte(a.slot().ordinal());o.writeDouble(a.protection());o.writeInt(a.durability());}
                o.writeShort(b.effects().size());for(var e:b.effects()){o.writeByte(e.effect().ordinal());o.writeLong(e.expiresAt());text(o,e.source());}
                o.writeBoolean(b.sprinting());o.writeBoolean(b.guarding());o.writeBoolean(b.using());o.writeDouble(b.movementMultiplier());o.writeLong(b.pearlReadyAt());o.writeLong(b.lastSequence());o.writeInt(b.life());
                o.writeDouble(f.yaw());o.writeDouble(f.pitch());o.writeBoolean(f.connected());o.writeBoolean(f.ready());o.writeLong(f.lastFrame());o.writeLong(f.lastInputSequence());
                var score=f.score();o.writeInt(score.hits());o.writeInt(score.longestCombo());o.writeInt(score.potsUsed());o.writeInt(score.potsRemaining());o.writeDouble(score.health());
            }
            o.writeShort(s.projectiles().size());for(var p:s.projectiles()){o.writeLong(p.id());o.writeByte(p.item().ordinal());text(o,p.source());vector(o,p.position());}
            o.writeShort(s.events().size());for(var e:s.events()){o.writeLong(e.id());o.writeLong(e.tick());o.writeByte(e.type().ordinal());text(o,e.actor());text(o,e.target());vector(o,e.position());o.writeDouble(e.amount());text(o,e.detail());}
        });
    }
    public static DuelSession.State state(DataInputStream in) throws IOException {
        try {
            long epoch=in.readLong(),tick=in.readLong(),combat=in.readLong();var phase=value(DuelSession.Phase.values(),in.readUnsignedByte());var profile=value(DuelSession.Profile.values(),in.readUnsignedByte());int remaining=in.readInt();String result=string(in,128);
            var fighters=new ArrayList<DuelSession.Fighter>();
            for(int i=0;i<2;i++) {
                String id=string(in,64);var pose=new CombatSimulation.Pose(vector(in),vector(in),in.readBoolean());
                double health=in.readDouble();boolean alive=in.readBoolean();double hunger=in.readDouble(),sat=in.readDouble();int slot=in.readInt();
                var stacks=new ArrayList<Stack>();int n=count(in,36);for(int k=0;k<n;k++) stacks.add(new Stack(value(Item.values(),in.readUnsignedByte()),in.readUnsignedByte()));
                var armor=new ArrayList<Armor>();n=count(in,4);for(int k=0;k<n;k++) armor.add(new Armor(value(ArmorSlot.values(),in.readUnsignedByte()),in.readDouble(),in.readInt()));
                var effects=new ArrayList<CombatSimulation.EffectView>();n=count(in,4);for(int k=0;k<n;k++) effects.add(new CombatSimulation.EffectView(value(CombatEvent.Effect.values(),in.readUnsignedByte()),in.readLong(),nullable(in)));
                var body=new CombatSimulation.Snapshot(id,pose,health,alive,hunger,sat,slot,List.copyOf(stacks),List.copyOf(armor),List.copyOf(effects),in.readBoolean(),in.readBoolean(),in.readBoolean(),in.readDouble(),in.readLong(),in.readLong(),in.readInt());
                double yaw=in.readDouble(),pitch=in.readDouble();boolean connected=in.readBoolean(),ready=in.readBoolean();long frame=in.readLong(),sequence=in.readLong();
                var score=new DuelSession.Score(in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readDouble());
                fighters.add(new DuelSession.Fighter(id,body,yaw,pitch,connected,ready,frame,sequence,score));
            }
            var projectiles=new ArrayList<CombatSimulation.ProjectileView>();int n=count(in,512);
            for(int k=0;k<n;k++) projectiles.add(new CombatSimulation.ProjectileView(in.readLong(),value(Item.values(),in.readUnsignedByte()),string(in,64),vector(in)));
            var events=new ArrayList<CombatEvent>();n=count(in,512);
            for(int k=0;k<n;k++) events.add(new CombatEvent(in.readLong(),in.readLong(),value(CombatEvent.Type.values(),in.readUnsignedByte()),nullable(in),nullable(in),vector(in),in.readDouble(),string(in,64)));
            return new DuelSession.State(epoch,tick,combat,phase,profile,remaining,result,List.copyOf(fighters),List.copyOf(projectiles),List.copyOf(events));
        }catch(IllegalArgumentException e){throw new IOException("Invalid duel state");}
    }
    private DuelWire() {}
}
