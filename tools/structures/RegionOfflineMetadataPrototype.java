import dev.jayms.net.*;
import dev.jayms.net.model.ModelLibrary;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

/** Private offline survival fields only. Never a structure package or account/player database. */
public final class RegionOfflineMetadataPrototype {
    static final int MAX_DROPS=100_000,MAX_BYTES=2*1024*1024;
    private static final int MAGIC=0x56524f4d,VERSION=1;
    private final Inventory inventory;private final int health;private final List<ItemDrop> drops;
    private RegionOfflineMetadataPrototype(Inventory inventory,int health,List<ItemDrop> drops){this.inventory=inventory;this.health=health;this.drops=List.copyOf(drops);}
    private static void check(BooleanSupplier cancel){if(Thread.currentThread().isInterrupted()||cancel.getAsBoolean())throw new CancellationException();}
    public Inventory inventory(){return inventory.copy();}
    public int health(){return health;}
    public List<ItemDrop> drops(){return drops;}
    public int nextDrop(){int maximum=0;for(var drop:drops)maximum=Math.max(maximum,drop.id());return maximum;}
    void validateModels(ModelLibrary models,BooleanSupplier cancel)throws IOException {
        for(int i=0;i<Inventory.SIZE;i++){check(cancel);int id=inventory.type(i);if(Blocks.isModel(id)&&models.get(id)==null)throw new IOException("Missing inventory model");}
        for(var drop:drops){check(cancel);if(Blocks.isModel(drop.type())&&models.get(drop.type())==null)throw new IOException("Missing dropped model");}
    }
    public static RegionOfflineMetadataPrototype capture(Inventory inventory,int health,Collection<ItemDrop> drops,ModelLibrary models,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(inventory);Objects.requireNonNull(drops);Objects.requireNonNull(models);Objects.requireNonNull(cancel);check(cancel);
        if(health<0||health>20||drops.size()>MAX_DROPS)throw new IOException("Offline metadata budget/value");
        var copy=new ArrayList<ItemDrop>();var ids=new HashSet<Integer>();
        for(var drop:drops){check(cancel);
            if(drop==null||drop.type()<1||drop.type()>255||drop.count()<0||drop.count()>255)throw new IOException("Invalid drop");
            var bytes=new ByteArrayOutputStream();drop.write(new DataOutputStream(bytes));var validated=ItemDrop.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            if(!validated.equals(drop)||!ids.add(drop.id())||copy.size()>=MAX_DROPS)throw new IOException("Invalid/duplicate drop");copy.add(drop);
        }
        var result=new RegionOfflineMetadataPrototype(inventory.copy(),health,copy);result.validateModels(models,cancel);check(cancel);return result;
    }
    public byte[] encode(BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);check(cancel);var sink=new RegionHistoryPrototype.LimitedOutput(MAX_BYTES,cancel);var crc=new CRC32();var out=new DataOutputStream(new CheckedOutputStream(sink,crc));
        out.writeInt(MAGIC);out.writeInt(VERSION);inventory.write(out);out.writeByte(health);out.writeInt(drops.size());
        for(var drop:drops){check(cancel);drop.write(out);}out.flush();new DataOutputStream(sink).writeInt((int)crc.getValue());byte[] result=sink.bytes.toByteArray();check(cancel);return result;
    }
    public static RegionOfflineMetadataPrototype decode(byte[] bytes,ModelLibrary models,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(bytes);Objects.requireNonNull(models);Objects.requireNonNull(cancel);check(cancel);
        if(bytes.length<89||bytes.length>MAX_BYTES)throw new IOException("Metadata byte budget");var crc=new CRC32();
        for(int offset=0;offset<bytes.length-4;offset+=65536){check(cancel);crc.update(bytes,offset,Math.min(65536,bytes.length-4-offset));}
        var in=new DataInputStream(new ByteArrayInputStream(bytes));
        if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IOException("Metadata format/version");
        var inventory=Inventory.read(in);int health=in.readUnsignedByte();int count=in.readInt();
        if(health>20||count<0||count>MAX_DROPS||bytes.length!=89L+18L*count)throw new IOException("Metadata count/health/length");
        var drops=new ArrayList<ItemDrop>();var ids=new HashSet<Integer>();
        for(int i=0;i<count;i++){check(cancel);var drop=ItemDrop.read(in);if(!ids.add(drop.id()))throw new IOException("Duplicate drop");drops.add(drop);}
        if(in.readInt()!=(int)crc.getValue()||in.read()!=-1)throw new IOException("Metadata CRC/trailing");
        var result=new RegionOfflineMetadataPrototype(inventory,health,drops);result.validateModels(models,cancel);check(cancel);return result;
    }
}
