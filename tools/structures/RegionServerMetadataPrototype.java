import dev.jayms.net.*;
import dev.jayms.net.model.ModelLibrary;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

/** Saved-world survival state only. No passwords, authentication records or structure-package fields. */
public final class RegionServerMetadataPrototype {
    static final int MAX_PLAYERS=10_000,MAX_BYTES=4*1024*1024;
    private static final int MAGIC=0x5652534d;
    public record Player(String name,Inventory inventory,int health){
        public Player{Objects.requireNonNull(name);inventory=Objects.requireNonNull(inventory).copy();}
        @Override public Inventory inventory(){return inventory.copy();}
    }
    private final List<Player> players;private final List<ItemDrop> drops;
    private RegionServerMetadataPrototype(List<Player> players,List<ItemDrop> drops){this.players=List.copyOf(players);this.drops=List.copyOf(drops);}
    public List<Player> players(){return players;}
    public List<ItemDrop> drops(){return drops;}
    private static void check(BooleanSupplier cancel){if(Thread.currentThread().isInterrupted()||cancel.getAsBoolean())throw new CancellationException();}
    void validateModels(ModelLibrary models,BooleanSupplier cancel)throws IOException {
        for(var player:players){var inventory=player.inventory();for(int slot=0;slot<Inventory.SIZE;slot++){check(cancel);if(!models.has(inventory.type(slot)))throw new IOException("Missing server inventory model");}}
        for(var drop:drops){check(cancel);if(!models.has(drop.type()))throw new IOException("Missing server dropped model");}
    }
    public static RegionServerMetadataPrototype capture(Collection<Player> players,Collection<ItemDrop> drops,ModelLibrary models,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(players);Objects.requireNonNull(drops);Objects.requireNonNull(models);Objects.requireNonNull(cancel);check(cancel);
        if(players.size()>MAX_PLAYERS)throw new IOException("Server player budget");var copy=new ArrayList<Player>();var names=new HashSet<String>();
        for(var player:players){check(cancel);if(player==null||player.name().length()>16||player.health()<0||player.health()>20||!names.add(player.name())||copy.size()>=MAX_PLAYERS)throw new IOException("Invalid/duplicate server player");copy.add(new Player(player.name(),player.inventory(),player.health()));}
        var validated=RegionOfflineMetadataPrototype.capture(new Inventory(),20,drops,models,cancel);
        var result=new RegionServerMetadataPrototype(copy,validated.drops());result.validateModels(models,cancel);check(cancel);return result;
    }
    public byte[] encode(BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);check(cancel);var sink=new RegionHistoryPrototype.LimitedOutput(MAX_BYTES,cancel);var crc=new CRC32();var out=new DataOutputStream(new CheckedOutputStream(sink,crc));
        out.writeInt(MAGIC);out.writeInt(1);out.writeInt(players.size());
        for(var player:players){check(cancel);out.writeUTF(player.name());player.inventory().write(out);out.writeByte(player.health());}
        out.writeInt(drops.size());for(var drop:drops){check(cancel);drop.write(out);}out.flush();new DataOutputStream(sink).writeInt((int)crc.getValue());var result=sink.bytes.toByteArray();check(cancel);return result;
    }
    public static RegionServerMetadataPrototype decode(byte[] bytes,ModelLibrary models,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(bytes);Objects.requireNonNull(models);Objects.requireNonNull(cancel);check(cancel);
        if(bytes.length<20||bytes.length>MAX_BYTES)throw new IOException("Server metadata byte budget");var crc=new CRC32();for(int offset=0;offset<bytes.length-4;offset+=65536){check(cancel);crc.update(bytes,offset,Math.min(65536,bytes.length-4-offset));}
        if((int)crc.getValue()!=ByteBuffer.wrap(bytes).getInt(bytes.length-4))throw new IOException("Server metadata CRC");
        var raw=new ByteArrayInputStream(bytes,0,bytes.length-4);var in=new DataInputStream(new FilterInputStream(raw){
            @Override public int read()throws IOException{check(cancel);return super.read();}
            @Override public int read(byte[] data,int offset,int length)throws IOException{check(cancel);return this.in.read(data,offset,length);}
        });
        if(in.readInt()!=MAGIC||in.readInt()!=1)throw new IOException("Server metadata format/version");
        int count=in.readInt();if(count<0||count>MAX_PLAYERS)throw new IOException("Server player budget");var players=new ArrayList<Player>();
        for(int i=0;i<count;i++){check(cancel);String name=Protocol.readText(in,16);var inventory=Inventory.read(in);int health=in.readUnsignedByte();players.add(new Player(name,inventory,health));}
        count=in.readInt();if(count<0||count>RegionOfflineMetadataPrototype.MAX_DROPS)throw new IOException("Server drop budget");var drops=new ArrayList<ItemDrop>();
        for(int i=0;i<count;i++){check(cancel);drops.add(ItemDrop.read(in));}if(in.read()!=-1)throw new IOException("Trailing server metadata");
        return capture(players,drops,models,cancel);
    }
}
