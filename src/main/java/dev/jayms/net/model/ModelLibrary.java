package dev.jayms.net.model;

import dev.jayms.net.*;

import java.io.*;
import java.util.*;

/** Each world assigns stable item IDs to immutable model definitions. */
public final class ModelLibrary {
    public static final int MAX_CUSTOM = 128;

    public record Entry(int id, String author, ModelDefinition definition) {
        public void write(DataOutputStream out) throws IOException {
            out.writeByte(id);
            out.writeUTF(author);
            definition.write(out);
        }

        public static Entry read(DataInputStream in) throws IOException {
            int id = in.readUnsignedByte();
            String author = Protocol.readText(in, 16);
            return new Entry(id, author, ModelDefinition.read(in));
        }
    }

    private static final ModelDefinition POT = ModelGenerators.flowerPot();
    private long revision;
    private long structureBytes;
    private int structureCount;
    public long revision(){return revision;}
    private Map<Integer, Entry> entries = new LinkedHashMap<>();
    private Map<String, Integer> fingerprints = new HashMap<>();

    public ModelLibrary() {
        var pot = POT;
        putUnchecked(
                new Entry(
                        Blocks.FLOWER_POT,
                        "engine",
                        new ModelDefinition(pot.name(), pot.voxels().freeze())));
    }

    public Entry get(int type) {
        var item = Items.get(type);
        return item != null && item.model() != null
                ? new Entry(type, "engine", item.model()) : entries.get(type);
    }

    public boolean has(int type) {
        return (Items.valid(type)||Blocks.structureModel(type)) && (!Blocks.isModel(type) || entries.containsKey(type));
    }

    public String name(int type) {
        Entry e = get(type);
        return e == null ? Items.name(type) : e.definition.name();
    }

    public Collection<Entry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public int find(ModelDefinition definition) {
        int id=fingerprints.getOrDefault(definition.fingerprint(), -1);
        return Items.valid(id)?id:-1;
    }

    public int nextId() throws IOException {
        for (int i = 9; i < 9 + MAX_CUSTOM; i++) if (!entries.containsKey(i)) return i;
        throw new IOException("This world's model library is full");
    }

    public Entry register(ModelDefinition definition, String author) throws IOException {
        int existing = find(definition);
        if (existing >= 0) return get(existing);
        if (definition.voxels().occupied() == 0)
            throw new IOException("Add voxels before creating an item");
        if (entries.values().stream().filter(e -> e.id>=9&&e.id<137&&e.author.equals(author)).count() >= 16)
            throw new IOException("Each player can publish up to 16 unique models per world");
        ModelMesher.mesh(definition.voxels());
        int id = nextId();
        var entry =
                new Entry(
                        id,
                        author,
                        new ModelDefinition(
                                definition.name(), definition.voxels().copy().freeze()));
        putUnchecked(entry);
        return entry;
    }

    public void accept(Entry entry) throws IOException {
        if (entry.id < 9
                || entry.id >= 9 + MAX_CUSTOM
                || entries.containsKey(entry.id)
                || entry.definition.voxels().occupied() == 0)
            throw new IOException("Invalid model definition");
        ModelMesher.mesh(entry.definition.voxels());
        putUnchecked(
                new Entry(
                        entry.id,
                        entry.author,
                        new ModelDefinition(
                                entry.definition.name(),
                                entry.definition.voxels().copy().freeze())));
    }

    private void putUnchecked(Entry entry) {
        revision++;
        entries.put(entry.id, entry);
        fingerprints.put(entry.definition.fingerprint(), entry.id);
    }

    public ModelLibrary copy() {
        return new ModelLibrary(this);
    }

    private ModelLibrary(ModelLibrary source) {
        // Registration freezes definitions. A snapshot needs independent maps,
        // but must not validate and serialize every immutable model again.
        entries.putAll(source.entries);
        fingerprints.putAll(source.fingerprints);
        revision = source.revision;
        structureBytes=source.structureBytes;structureCount=source.structureCount;
    }

    public void write(DataOutputStream out) throws IOException {
        if(hasStructureModels())throw new IOException("Structure components require versioned world model codec");
        out.writeInt(entries.size() - 1);
        for (var e : entries.values()) if (e.id != Blocks.FLOWER_POT) e.write(out);
    }

    public static ModelLibrary read(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_CUSTOM) throw new IOException("Invalid model count");
        ModelLibrary result = new ModelLibrary();
        for (int i = 0; i < count; i++) result.accept(Entry.read(in));
        return result;
    }

    public boolean hasStructureModels(){return entries.keySet().stream().anyMatch(Blocks::structureModel);}

    /** Invoked on a private staging copy. Does not consume a player's legacy publishing allowance. */
    public Entry stageStructureModel(ModelDefinition definition) throws IOException {
        int existing=fingerprints.getOrDefault(definition.fingerprint(),-1);if(existing>=0)return get(existing);
        var buffer=new ByteArrayOutputStream();definition.write(new DataOutputStream(buffer));
        if(structureCount>=2048||structureBytes+buffer.size()>128L*1024*1024)throw new IOException("Experimental world dependency budget");
        ModelMesher.mesh(definition.voxels());
        for(int id=1024;id<66560;id++)if(!entries.containsKey(id)) {
            var entry=new Entry(id,"structure",new ModelDefinition(definition.name(),definition.voxels().copy().freeze()));putUnchecked(entry);structureCount++;structureBytes+=buffer.size();return entry;
        }
        throw new IOException("Structure dependency IDs exhausted");
    }

    /** Publish a fully prepared private copy on the authoritative owner thread. */
    public void replaceWith(ModelLibrary prepared) {
        if(entries.equals(prepared.entries))return;
        entries=new LinkedHashMap<>(prepared.entries);fingerprints=new HashMap<>(prepared.fingerprints);revision++;
        structureBytes=prepared.structureBytes;structureCount=prepared.structureCount;
    }

    public void writeAll(DataOutputStream out) throws IOException {
        out.writeInt(entries.size()-1);
        for(var entry:entries.values())if(entry.id!=Blocks.FLOWER_POT) {
            out.writeInt(entry.id);out.writeUTF(entry.author);entry.definition.write(out);
        }
    }
    public void acceptWorldEntry(Entry entry)throws IOException {
        var old=get(entry.id);
        if(old!=null) {if(!old.definition.fingerprint().equals(entry.definition.fingerprint()))throw new IOException("Model ID conflict");return;}
        if(!Blocks.structureModel(entry.id)){accept(entry);return;}
        var bytes=new ByteArrayOutputStream();entry.definition.write(new DataOutputStream(bytes));
        if(structureCount>=2048||structureBytes+bytes.size()>128L*1024*1024||entry.definition.voxels().occupied()==0)throw new IOException("World dependency budget");
        ModelMesher.mesh(entry.definition.voxels());putUnchecked(new Entry(entry.id,"structure",new ModelDefinition(entry.definition.name(),entry.definition.voxels().copy().freeze())));
        structureCount++;structureBytes+=bytes.size();
    }
    public static ModelLibrary readAll(DataInputStream in) throws IOException {
        int count=in.readInt();if(count<0||count>MAX_CUSTOM+2048)throw new IOException("World model count limit");
        var result=new ModelLibrary();long bytes=0;int extended=0,custom=0;
        for(int i=0;i<count;i++) {
            int id=in.readInt();String author=Protocol.readText(in,16);var model=ModelDefinition.read(in);
            if(result.entries.containsKey(id)||!(id>=9&&id<137||Blocks.structureModel(id))||model.voxels().occupied()==0)
                throw new IOException("Invalid world model");
            if(Blocks.structureModel(id)) {extended++;var encoded=new ByteArrayOutputStream();model.write(new DataOutputStream(encoded));bytes+=encoded.size();}
            else custom++;
            if(custom>MAX_CUSTOM||extended>2048||bytes>128L*1024*1024)throw new IOException("World dependency budget");
            ModelMesher.mesh(model.voxels());result.putUnchecked(new Entry(id,author,new ModelDefinition(model.name(),model.voxels().freeze())));
        }
        result.structureBytes=bytes;result.structureCount=extended;return result;
    }
}
