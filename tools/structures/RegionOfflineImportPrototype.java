import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Bounded base-file v8/v9 migration into a detached private envelope. No sidecars or live-world writes. */
public final class RegionOfflineImportPrototype {
    static final int MAX_BYTES=16*1024*1024,MAX_SOURCE_EDITS=200_000;
    private static void check(BooleanSupplier cancel){if(Thread.currentThread().isInterrupted()||cancel.getAsBoolean())throw new CancellationException();}
    public static RegionWorldLibraryPrototype decode(byte[] source,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(source);Objects.requireNonNull(cancel);check(cancel);
        if(source.length<101||source.length>MAX_BYTES)throw new IOException("Offline source byte budget");
        byte[] bytes=source.clone();check(cancel);var raw=new ByteArrayInputStream(bytes);
        var checked=new FilterInputStream(raw){
            @Override public int read()throws IOException{check(cancel);return super.read();}
            @Override public int read(byte[] data,int offset,int length)throws IOException{check(cancel);return in.read(data,offset,length);}
        };
        var in=new DataInputStream(checked);int version=in.readInt();if(version!=8&&version!=9)throw new IOException("Only offline base files v8/v9 are supported");
        long seed=in.readLong();int terrainVersion=in.readInt();
        try{new Terrain(seed,terrainVersion);}catch(IllegalArgumentException invalid){throw new IOException("Unsupported terrain version",invalid);}
        // Bound the library bytes while the existing production model codecs read them.
        var limited=new FilterInputStream(checked){
            int remaining=RegionWorldLibraryPrototype.MAX_LIBRARY_BYTES;
            @Override public int read()throws IOException{if(remaining==0)throw new IOException("Source library byte budget");int value=in.read();if(value>=0)remaining--;return value;}
            @Override public int read(byte[] data,int offset,int length)throws IOException{if(length>remaining)throw new IOException("Source library byte budget");int count=in.read(data,offset,length);if(count>0)remaining-=count;return count;}
        };
        var modelInput=new DataInputStream(limited);var models=version==9?ModelLibrary.readAll(modelInput):ModelLibrary.read(modelInput);check(cancel);
        var inventory=Inventory.read(in);int health=in.readUnsignedByte();if(health>20)throw new IOException("Invalid health");
        int count=in.readInt();if(count<0||count>MAX_SOURCE_EDITS)throw new IOException("Source edit budget");
        Iterable<Protocol.Edit> edits=()->new Iterator<>(){
            int remaining=count;
            public boolean hasNext(){return remaining>0;}
            public Protocol.Edit next(){if(remaining==0)throw new NoSuchElementException();check(cancel);remaining--;
                try{var edit=Protocol.Edit.read(in);if(!edit.valid()||Blocks.isModel(edit.type())&&models.get(edit.type())==null)throw new IOException("Invalid edit or missing model");return edit;}
                catch(IOException invalid){throw new UncheckedIOException(invalid);}
            }
        };
        RegionHistoryPrototype geometry;
        try{geometry=RegionHistoryPrototype.migrate(seed,terrainVersion,edits,new RegionHistoryPrototype.Budget(MAX_SOURCE_EDITS,100_000,1024),cancel);}
        catch(UncheckedIOException invalid){throw invalid.getCause();}
        catch(IllegalArgumentException invalid){throw new IOException("Offline migration budget/value",invalid);}
        int drops=in.readInt();if(drops<0||drops>RegionOfflineMetadataPrototype.MAX_DROPS)throw new IOException("Source drop budget");
        var records=new ArrayList<ItemDrop>();for(int i=0;i<drops;i++){check(cancel);records.add(ItemDrop.read(in));}
        if(in.read()!=-1)throw new IOException("Trailing offline data");
        var metadata=RegionOfflineMetadataPrototype.capture(inventory,health,records,models,cancel);
        var result=RegionWorldLibraryPrototype.capture(geometry,models,metadata,cancel);
        // Admission includes the whole private encoded-size/leaf bounds before returning a candidate.
        result.encode(cancel);check(cancel);return result;
    }
}
