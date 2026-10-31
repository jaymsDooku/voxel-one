import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Bounded modern server base-file migration. Synthetic/private use; no auth files or live writes. */
public final class RegionServerImportPrototype {
    static final int MAX_BYTES=16*1024*1024,MAX_SOURCE_EDITS=200_000;
    private static void check(BooleanSupplier cancel){if(Thread.currentThread().isInterrupted()||cancel.getAsBoolean())throw new CancellationException();}
    public static RegionWorldLibraryPrototype decode(byte[] source,RegionSidecarsPrototype sidecars,BooleanSupplier cancel)throws IOException {
        var candidate=decode(source,cancel).withSidecars(Objects.requireNonNull(sidecars),cancel);candidate.encode(cancel);check(cancel);return candidate;
    }
    public static RegionWorldLibraryPrototype decode(byte[] source,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(source);Objects.requireNonNull(cancel);check(cancel);
        if(source.length<28||source.length>MAX_BYTES)throw new IOException("Server source byte budget");
        byte[] bytes=source.clone();check(cancel);var raw=new ByteArrayInputStream(bytes);
        var checked=new FilterInputStream(raw){
            @Override public int read()throws IOException{check(cancel);return super.read();}
            @Override public int read(byte[] data,int offset,int length)throws IOException{check(cancel);return in.read(data,offset,length);}
        };
        var in=new DataInputStream(checked);if(in.readInt()!=Protocol.MAGIC)throw new IOException("Server source magic");int format=in.readInt();if(format> -3||format< -9)throw new IOException("Only modern server formats -3..-9 are supported");int version=-format;
        long seed=in.readLong();int terrainVersion=version>=7?in.readInt():Terrain.LEGACY_VERSION;
        try{new Terrain(seed,terrainVersion);}catch(IllegalArgumentException invalid){throw new IOException("Unsupported terrain version",invalid);}
        // Bound the library bytes while the existing production model codecs read them.
        var limited=new FilterInputStream(checked){
            int remaining=RegionWorldLibraryPrototype.MAX_LIBRARY_BYTES;
            @Override public int read()throws IOException{if(remaining==0)throw new IOException("Source library byte budget");int value=in.read();if(value>=0)remaining--;return value;}
            @Override public int read(byte[] data,int offset,int length)throws IOException{if(length>remaining)throw new IOException("Source library byte budget");int count=in.read(data,offset,length);if(count>0)remaining-=count;return count;}
        };
        var modelInput=new DataInputStream(limited);var models=version==3?new ModelLibrary():version==9?ModelLibrary.readAll(modelInput):ModelLibrary.read(modelInput);check(cancel);
        int count=in.readInt();if(count<0||count>MAX_SOURCE_EDITS)throw new IOException("Source edit budget");
        Iterable<Protocol.Edit> edits=()->new Iterator<>(){
            int remaining=count;
            public boolean hasNext(){return remaining>0;}
            public Protocol.Edit next(){if(remaining==0)throw new NoSuchElementException();check(cancel);remaining--;
                try{var edit=version>=6?Protocol.Edit.read(in):version==5?Protocol.Edit.readV7(in):Protocol.Edit.readLegacy(in);if(!edit.valid()||Blocks.isModel(edit.type())&&models.get(edit.type())==null)throw new IOException("Invalid edit or missing model");return edit;}
                catch(IOException invalid){throw new UncheckedIOException(invalid);}
            }
        };
        RegionHistoryPrototype geometry;
        try{geometry=RegionHistoryPrototype.migrate(seed,terrainVersion,edits,new RegionHistoryPrototype.Budget(MAX_SOURCE_EDITS,100_000,1024),cancel);}
        catch(UncheckedIOException invalid){throw invalid.getCause();}
        catch(IllegalArgumentException invalid){throw new IOException("Server migration budget/value",invalid);}
        int playerCount=in.readInt();if(playerCount<0||playerCount>RegionServerMetadataPrototype.MAX_PLAYERS)throw new IOException("Source player budget");
        var players=new ArrayList<RegionServerMetadataPrototype.Player>();
        for(int i=0;i<playerCount;i++){check(cancel);String name=Protocol.readText(in,16);var inventory=Inventory.read(in,version>=8);int health=in.readUnsignedByte();if(health>20)throw new IOException("Invalid health");players.add(new RegionServerMetadataPrototype.Player(name,inventory,health));}
        int drops=in.readInt();if(drops<0||drops>RegionOfflineMetadataPrototype.MAX_DROPS)throw new IOException("Source drop budget");
        var records=new ArrayList<ItemDrop>();for(int i=0;i<drops;i++){check(cancel);records.add(ItemDrop.read(in,version>=8));}
        if(in.read()!=-1)throw new IOException("Trailing server data");
        var metadata=RegionServerMetadataPrototype.capture(players,records,models,cancel);
        var result=RegionWorldLibraryPrototype.captureServer(geometry,models,metadata,cancel);
        // Admission includes the whole private encoded-size/leaf bounds before returning a candidate.
        result.encode(cancel);check(cancel);return result;
    }
}
