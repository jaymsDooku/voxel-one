import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import java.util.zip.CRC32;

/** CLI dependency restoration, identity and malformed-container integration checks. */
public final class RegionDependencyCheck {
    private static int rejected;
    private interface Action{void run()throws Exception;}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void reject(Class<? extends Throwable> type,String message,Action action)throws Exception {
        try{action.run();}catch(Throwable failure){if(type.isInstance(failure)&&(message==null||String.valueOf(failure.getMessage()).contains(message))){rejected++;return;}throw new AssertionError(message,failure);}
        throw new AssertionError("Unexpected acceptance: "+message);
    }
    private static ModelDefinition model(String name,int color){var tree=new SparseVoxelOctree(8);tree.set(1,2,3,color);return new ModelDefinition(name,tree);}
    private static RegionHistoryPrototype geometry(List<Protocol.Edit> edits){return RegionHistoryPrototype.migrate(123,Terrain.CURRENT_VERSION,edits,new RegionHistoryPrototype.Budget(20_000,10_000,1024),()->false);}
    private static byte[] crc(byte[] data){var crc=new CRC32();crc.update(data,0,data.length-4);ByteBuffer.wrap(data).putInt(data.length-4,(int)crc.getValue());return data;}
    private static byte[] changed(byte[] source,int offset,int value){var data=source.clone();ByteBuffer.wrap(data).putInt(offset,value);return crc(data);}
    private static byte[] wire(List<ModelDefinition> definitions,List<int[]> bindings,byte[] region)throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeInt(0x56524450);out.writeInt(1);out.writeInt(definitions.size());
        for(var definition:definitions){out.write(HexFormat.of().parseHex(definition.fingerprint()));definition.write(out);}
        out.writeInt(bindings.size());for(var binding:bindings){out.writeInt(binding[0]);out.writeShort(binding[1]);}
        out.writeInt(region.length);out.write(region);out.writeInt(0);return crc(bytes.toByteArray());
    }
    private static RegionDependencyPrototype decode(byte[] data)throws IOException{return RegionDependencyPrototype.decode(data,()->false);}
    private static void bad(byte[] data,String message)throws Exception{reject(IOException.class,message,()->decode(data));}
    public static void main(String[] args)throws Exception {
        var a=model("alpha",0xff123456);var b=model("beta",0xffabcdef);var conflict=model("conflict",0xff445566);
        var base=new ModelLibrary();var definitions=new HashMap<Integer,ModelDefinition>();
        definitions.put(8,base.get(8).definition());definitions.put(9,b);definitions.put(1024,a);definitions.put(1025,a);definitions.put(17,conflict);
        var edits=List.of(new Protocol.Edit(0,128,0,1024),new Protocol.Edit(1,128,0,1025),new Protocol.Edit(2,128,0,9),new Protocol.Edit(3,128,0,8),new Protocol.Edit(4,128,0,Blocks.STONE),new Protocol.Edit(5,Terrain.MIN_Y,0,0),new Protocol.Edit(6,128,0,Blocks.piece(Blocks.LED,4),4,1,2,3,0x112233));
        var geometry=geometry(edits);var captured=RegionDependencyPrototype.capture(geometry,definitions,()->false);
        require(captured.bindings().size()==4&&!captured.bindings().containsKey(17),"Unused source dependency captured");
        require(captured.bindings().get(1024)==captured.bindings().get(1025),"Alias definitions not shared");
        String alpha=captured.bindings().get(1024).fingerprint();a.voxels().set(0,0,0,0xff111111);
        require(captured.bindings().get(1024).fingerprint().equals(alpha),"Source edit changed captured definition");
        var bytes=captured.encode(()->false);var decoded=decode(bytes);
        require(Arrays.equals(bytes,decoded.encode(()->false)),"Manifest round trip differs");
        reject(UnsupportedOperationException.class,null,()->decoded.bindings().clear());
        reject(IllegalStateException.class,null,()->decoded.bindings().get(1024).voxels().set(0,0,0,0));
        base.acceptWorldEntry(new ModelLibrary.Entry(1024,"synthetic",conflict));int entries=base.entries().size();String old=base.get(1024).definition().fingerprint();
        var restored=decoded.restore(base,()->false);
        require(base.entries().size()==entries&&base.get(1024).definition().fingerprint().equals(old),"Destination source library changed");
        int remapped=restored.world().type(0,128,0);
        require(remapped!=1024&&restored.world().type(1,128,0)==remapped,"Conflict/alias remap failed");
        require(restored.models().get(remapped).definition().fingerprint().equals(alpha),"Restored alpha differs");
        require(restored.models().get(restored.world().type(2,128,0)).definition().fingerprint().equals(b.fingerprint()),"Legacy dependency lost");
        require(restored.world().type(3,128,0)==8,"Built-in identity changed");
        var sourceWorld=geometry.replay();for(int x:new int[]{4,5,6}){int y=x==5?Terrain.MIN_Y:128;require(restored.world().cell(x,y,0).sameValues(sourceWorld.cell(x,y,0)),"Nonmodel geometry changed");}
        var fresh=decoded.restore(new ModelLibrary(),()->false);require(fresh.models().get(fresh.world().type(0,128,0)).definition().fingerprint().equals(alpha),"Empty dependency cache failed");
        var only=geometry(List.of(new Protocol.Edit(0,128,0,1024)));var tiny=model("tiny",0xff778899);
        reject(IOException.class,"Missing",()->RegionDependencyPrototype.capture(only,Map.of(),()->false));
        reject(IOException.class,"Empty",()->RegionDependencyPrototype.capture(only,Map.of(1024,new ModelDefinition("empty",new SparseVoxelOctree(8))),()->false));
        var complex=new SparseVoxelOctree(16);for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)if((x+y+z)%2==0)complex.set(x,y,z,0xff123456);
        reject(IOException.class,null,()->RegionDependencyPrototype.capture(only,Map.of(1024,new ModelDefinition("complex",complex)),()->false));
        var budget=new RegionHistoryPrototype.CodecBudget(8*1024*1024,100_000,1024,131_072);var region=only.encode(budget,()->false);
        var golden=wire(List.of(tiny),List.of(new int[]{1024,0}),region);require(Arrays.equals(golden,decode(golden).encode(()->false)),"Golden container differs");
        bad(wire(List.of(new ModelDefinition("complex",complex)),List.of(new int[]{1024,0}),region),null);
        bad(changed(golden,0,0),"format/version");bad(changed(golden,4,2),"format/version");bad(changed(golden,8,-1),"Definition budget");bad(changed(golden,8,513),"Definition budget");
        var mismatch=golden.clone();mismatch[12]^=1;bad(crc(mismatch),"identity");bad(changed(golden,44,-1),"packet size");
        bad(wire(List.of(tiny,tiny),List.of(new int[]{1024,0}),region),"identity");
        bad(wire(List.of(tiny,b),List.of(new int[]{1024,0}),region),"Unused");
        bad(wire(List.of(tiny),List.of(new int[]{137,0}),region),"binding");
        bad(wire(List.of(tiny),List.of(new int[]{1024,1}),region),"binding");
        bad(wire(List.of(tiny),List.of(new int[]{1024,0},new int[]{1024,0}),region),"binding");
        bad(wire(List.of(tiny),List.of(new int[]{1025,0},new int[]{1024,0}),region),"binding");
        bad(wire(List.of(),List.of(),region),"Missing/extra");
        var emptyRegion=geometry(List.of()).encode(budget,()->false);bad(wire(List.of(tiny),List.of(new int[]{1024,0}),emptyRegion),"Missing/extra");
        int bindingOffset=48+ByteBuffer.wrap(golden).getInt(44),regionLengthOffset=bindingOffset+4+6;
        bad(changed(golden,bindingOffset,1025),"Binding budget");bad(changed(golden,regionLengthOffset,-1),"Region byte budget");bad(changed(golden,regionLengthOffset,8*1024*1024+1),"Region byte budget");
        var inner=golden.clone();inner[regionLengthOffset+4+8]^=1;bad(crc(inner),"Diagnostic CRC");
        var corrupt=golden.clone();corrupt[corrupt.length-1]^=1;bad(corrupt,"Manifest CRC");
        var trailing=new byte[golden.length+1];System.arraycopy(golden,0,trailing,0,golden.length-4);bad(crc(trailing),"Trailing");
        for(int length:new int[]{0,1,23,44,48,golden.length/2,golden.length-1})bad(Arrays.copyOf(golden,length),null);
        bad(new byte[RegionDependencyPrototype.MAX_BYTES+1],"byte budget");
        var many=new ArrayList<Protocol.Edit>();var models=new HashMap<Integer,ModelDefinition>();
        for(int i=0;i<512;i++){many.add(new Protocol.Edit(i%32,128,i/32,1024+i));models.put(1024+i,model("d_"+i,0xff000000|i));}
        var full=RegionDependencyPrototype.capture(geometry(many),models,()->false);var fullBytes=full.encode(()->false);var fullRestored=decode(fullBytes).restore(new ModelLibrary(),()->false);
        require(full.bindings().size()==512,"512 definitions rejected");
        for(int i=0;i<512;i++)require(fullRestored.models().get(fullRestored.world().type(i%32,128,i/32)).definition().fingerprint().equals(models.get(1024+i).fingerprint()),"512 dependency mismatch");
        many.add(new Protocol.Edit(0,128,16,1536));models.put(1536,tiny);
        reject(IOException.class,"Definition budget",()->RegionDependencyPrototype.capture(geometry(many),models,()->false));
        var aliases=new ArrayList<Protocol.Edit>();var aliasDefinitions=new HashMap<Integer,ModelDefinition>();
        for(int i=0;i<1024;i++){aliases.add(new Protocol.Edit(i%64,128,i/64,1024+i));aliasDefinitions.put(1024+i,tiny);}
        var aliasManifest=RegionDependencyPrototype.capture(geometry(aliases),aliasDefinitions,()->false);require(aliasManifest.bindings().size()==1024,"1024 alias bindings rejected");
        require(decode(aliasManifest.encode(()->false)).bindings().values().stream().distinct().count()==1,"Alias table did not deduplicate");
        aliases.add(new Protocol.Edit(0,128,16,2048));aliasDefinitions.put(2048,tiny);
        reject(IOException.class,"Binding budget",()->RegionDependencyPrototype.capture(geometry(aliases),aliasDefinitions,()->false));
        reject(CancellationException.class,null,()->decoded.restore(base,()->true));
        var checks=new AtomicInteger();decoded.restore(base,()->{checks.incrementAndGet();return false;});int finalCheck=checks.get();checks.set(0);var published=new AtomicReference<RegionDependencyPrototype.Restored>();
        reject(CancellationException.class,null,()->published.set(decoded.restore(base,()->checks.incrementAndGet()>=finalCheck)));
        require(published.get()==null&&base.entries().size()==entries&&base.get(1024).definition().fingerprint().equals(old),"Cancelled restore changed source/published result");
        reject(CancellationException.class,null,()->RegionDependencyPrototype.capture(only,Map.of(1024,tiny),()->true));
        reject(CancellationException.class,null,()->RegionDependencyPrototype.decode(golden,()->true));
        reject(CancellationException.class,null,()->decoded.encode(()->true));
        checks.set(0);RegionDependencyPrototype.decode(golden,()->{checks.incrementAndGet();return false;});int decodeChecks=checks.get();
        checks.set(0);reject(CancellationException.class,null,()->RegionDependencyPrototype.decode(golden,()->checks.incrementAndGet()>decodeChecks/2));
        checks.set(0);var decodedResult=new AtomicReference<RegionDependencyPrototype>();
        reject(CancellationException.class,null,()->decodedResult.set(RegionDependencyPrototype.decode(golden,()->checks.incrementAndGet()>=decodeChecks)));
        require(decodedResult.get()==null,"Cancelled decoder returned a manifest");
        checks.set(0);decoded.encode(()->{checks.incrementAndGet();return false;});int encodeChecks=checks.get();
        checks.set(0);reject(CancellationException.class,null,()->decoded.encode(()->checks.incrementAndGet()>encodeChecks/2));
        checks.set(0);var encodedResult=new AtomicReference<byte[]>();
        reject(CancellationException.class,null,()->encodedResult.set(decoded.encode(()->checks.incrementAndGet()>=encodeChecks)));
        require(encodedResult.get()==null,"Cancelled encoder returned bytes");
        checks.set(0);RegionDependencyPrototype.capture(only,Map.of(1024,tiny),()->{checks.incrementAndGet();return false;});int captureChecks=checks.get();
        checks.set(0);reject(CancellationException.class,null,()->RegionDependencyPrototype.capture(only,Map.of(1024,tiny),()->checks.incrementAndGet()>captureChecks/2));
        Thread.currentThread().interrupt();try{reject(CancellationException.class,null,()->decode(golden));require(Thread.currentThread().isInterrupted(),"Interrupt cleared");}finally{Thread.interrupted();}
        require(Arrays.equals(bytes,decoded.encode(()->false)),"Retry changed snapshot");
        System.out.printf("{\"status\":\"passed\",\"manifest_bytes\":%d,\"full_512_definition_bytes\":%d,\"bindings\":4,\"unique_definitions\":3,\"rejection_cases\":%d,\"qualification\":false}%n",bytes.length,fullBytes.length,rejected);
    }
}
