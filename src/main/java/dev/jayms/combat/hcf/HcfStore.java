package dev.jayms.combat.hcf;

import java.io.*;
import dev.jayms.combat.Vec3;
import dev.jayms.combat.KitProfile.Item;
import dev.jayms.combat.KitProfile.Stack;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

/** Private, bounded, versioned atomic state. Corrupt/future versions fail closed. No credentials here. */
public final class HcfStore {
    private static final int MAGIC=0x56484346,VERSION=1,MAX_BYTES=16384;
    public record Member(String id,int team,int role,long deathbanUntil,boolean deployed) {
        public Member {
            if(id==null || !id.matches("[a-z0-9_]{3,16}") || team<0 || team>1 || role<0 || role>4 || deathbanUntil<0)
                throw new IllegalArgumentException("HCF member state");
        }
    }
    public record Team(double dtr,long freezeUntil,long regenAt,int stash,int score,boolean claimed) {
        public Team {
            if(!Double.isFinite(dtr) || dtr< -3 || dtr>3 || freezeUntil<0 || regenAt<0 || stash<0 || stash>32 || score<0 || score>1_000_000)
                throw new IllegalArgumentException("HCF team state");
        }
    }
    public record Drop(long id,Vec3 feet,long expiresAt,List<Stack> items) {
        public Drop {
            items=List.copyOf(items);Objects.requireNonNull(feet);
            if(id<1 || expiresAt<0 || items.size()>36 || Math.abs(feet.x())>32 || Math.abs(feet.z())>40 || feet.y()<0 || feet.y()>24)
                throw new IllegalArgumentException("HCF drop bound");
        }
    }
    public record Saved(long season,int phase,List<Member> members,List<Team> teams,List<Drop> drops) {
        public Saved(long season,int phase,List<Member> members,List<Team> teams) {this(season,phase,members,teams,List.of());}
        public Saved {
            members=List.copyOf(members);teams=List.copyOf(teams);drops=List.copyOf(drops);
            if(drops.size()>16 || drops.stream().map(Drop::id).distinct().count()!=drops.size())throw new IllegalArgumentException("HCF drop count");
            if(season<1 || phase<0 || phase>2 || members.size()>16 || teams.size()!=2 || members.stream().map(Member::id).distinct().count()!=members.size())
                throw new IllegalArgumentException("HCF saved state");
            for(int i=0;i<2;i++) {final int side=i;
                if(members.stream().filter(m->m.team()==side).count()>8)throw new IllegalArgumentException("HCF roster bound");
                for(HcfRole role:HcfRole.values())if(members.stream().filter(m->m.team()==side && m.role()==role.ordinal()).count()>role.limit)
                    throw new IllegalArgumentException("HCF class bound");
            }
        }
        public static Saved initial() {return new Saved(1,0,List.of(),List.of(new Team(3,0,0,16,0,false),new Team(3,0,0,16,0,false)));}
        public Saved phase(HcfSession.Phase next) {return new Saved(season,next.ordinal(),members,teams,drops);}
        public Saved reset() {
            var fresh=initial();return new Saved(Math.addExact(season,1),0,List.of(),fresh.teams());
        }
    }
    private final Path file;
    public HcfStore(Path file) {this.file=file;}
    public Saved load() throws IOException {
        if(file==null || !Files.exists(file))return Saved.initial();
        if(Files.size(file)>MAX_BYTES)throw new IOException("HCF save size bound");
        try(var in=new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(file)))) {
            if(in.readInt()!=MAGIC || in.readInt()!=VERSION)throw new IOException("Unsupported HCF save version");
            long season=in.readLong();int phase=in.readUnsignedByte(),count=in.readUnsignedByte();
            if(count>16)throw new IOException("HCF member bound");
            var ms=new ArrayList<Member>();for(int i=0;i<count;i++)ms.add(new Member(in.readUTF(),in.readUnsignedByte(),in.readUnsignedByte(),in.readLong(),in.readBoolean()));
            var ts=new ArrayList<Team>();for(int i=0;i<2;i++)ts.add(new Team(in.readDouble(),in.readLong(),in.readLong(),in.readUnsignedByte(),in.readInt(),in.readBoolean()));
            int dropCount=in.readUnsignedByte();if(dropCount>16)throw new IOException("HCF drop bound");
            var drops=new ArrayList<Drop>();for(int k=0;k<dropCount;k++) {
                long id=in.readLong();var feet=new Vec3(in.readDouble(),in.readDouble(),in.readDouble());long expiry=in.readLong();int size=in.readUnsignedByte();
                if(size>36)throw new IOException("HCF drop slots");var items=new ArrayList<Stack>();
                for(int j=0;j<size;j++){int item=in.readUnsignedByte(),amount=in.readUnsignedByte();if(item>=Item.values().length)throw new IOException("HCF item bound");items.add(new Stack(Item.values()[item],amount));}
                drops.add(new Drop(id,feet,expiry,items));
            }
            if(in.available()!=0)throw new IOException("Trailing HCF save data");return new Saved(season,phase,ms,ts,drops);
        }catch(IllegalArgumentException e){throw new IOException("Invalid HCF save",e);}
    }
    public void save(Saved saved) throws IOException {
        Objects.requireNonNull(saved);if(file==null)return;
        var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);out.writeInt(VERSION);out.writeLong(saved.season());out.writeByte(saved.phase());out.writeByte(saved.members().size());
            for(var m:saved.members()){out.writeUTF(m.id());out.writeByte(m.team());out.writeByte(m.role());out.writeLong(m.deathbanUntil());out.writeBoolean(m.deployed());}
            for(var t:saved.teams()){out.writeDouble(t.dtr());out.writeLong(t.freezeUntil());out.writeLong(t.regenAt());out.writeByte(t.stash());out.writeInt(t.score());out.writeBoolean(t.claimed());}
            out.writeByte(saved.drops().size());for(var drop:saved.drops()) {
                out.writeLong(drop.id());out.writeDouble(drop.feet().x());out.writeDouble(drop.feet().y());out.writeDouble(drop.feet().z());out.writeLong(drop.expiresAt());out.writeByte(drop.items().size());
                for(var item:drop.items()){out.writeByte(item.item().ordinal());out.writeByte(item.count());}
            }
        }
        Path absolute=file.toAbsolutePath();Files.createDirectories(absolute.getParent());Path temp=Files.createTempFile(absolute.getParent(),"hcf-",".tmp");
        try {
            try{Files.setPosixFilePermissions(temp,PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException ignored){}
            try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE)) {
                var buffer=java.nio.ByteBuffer.wrap(bytes.toByteArray());while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
            }
            Files.move(temp,absolute,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            // Directory fsync where available. Atomic rename is required on every platform.
            try(var directory=FileChannel.open(absolute.getParent(),StandardOpenOption.READ)){directory.force(true);}
            catch(AccessDeniedException|UnsupportedOperationException ignored){}
        }finally{Files.deleteIfExists(temp);}
    }
}
