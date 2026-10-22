import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;

/** Actual offline save/reload diagnostic. No fsync, cold-cache or city/system claim. */
public final class WorldSaveProbe {
    public static void main(String[] args)throws Exception {
        if(args.length!=2||!List.of("legacy","extended").contains(args[0]))throw new IllegalArgumentException("legacy|extended synthetic-save-path required");
        Path file=Path.of(args[1]);if(Files.exists(file))throw new IllegalArgumentException("Use a fresh synthetic save path");
        var game=new LocalGame(file,42);
        if(args[0].equals("extended")) {
            var tree=new SparseVoxelOctree(8);tree.set(1,2,3,0xff123456);
            game.models.stageStructureModel(new ModelDefinition("Save component",tree));
        }
        for(int cell=0;cell<40000;cell++) {
            int x=cell%500,z=cell/500;
            if(cell%2==0){var edit=new Protocol.Edit(x,128,z,Blocks.STONE);WorldVoxels.remember(game.edits,edit);}
            else for(int part=0;part<4;part++) {
                var edit=new Protocol.Edit(x,128,z,Blocks.piece(Blocks.STONE,4),4,part,0,0,0);
                if(!edit.valid())throw new AssertionError("Invalid fixture");WorldVoxels.remember(game.edits,edit);
            }
        }
        if(game.edits.size()!=100000)throw new AssertionError("Fixture count");
        var expected=new ArrayList<>(game.edits.entrySet());double[] times=new double[20];String digest=null;long length=0;
        for(int run=-3;run<20;run++) {
            long start=System.nanoTime();game.save();double elapsed=(System.nanoTime()-start)/1e6;
            if(run>=0)times[run]=elapsed;
            // Outside timed save: decode using the real loader and compare all ordered edits.
            var loaded=new LocalGame(file,0);
            if(loaded.seed!=42||!expected.equals(new ArrayList<>(loaded.edits.entrySet()))||!game.models.entries().stream().map(e->e.id()+":"+e.author()+":"+e.definition().name()+":"+e.definition().fingerprint()).toList().equals(loaded.models.entries().stream().map(e->e.id()+":"+e.author()+":"+e.definition().name()+":"+e.definition().fingerprint()).toList()))throw new AssertionError("Save changed state");
            byte[] bytes=Files.readAllBytes(file);String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if(digest!=null&&!digest.equals(hash))throw new AssertionError("Unstable file bytes");digest=hash;length=bytes.length;
        }
        Arrays.sort(times);
        System.out.printf(Locale.ROOT,"{\"format\":\"%s\",\"edits\":100000,\"cells\":40000,\"warmups\":3,\"runs\":20,\"median_save_ms\":%.6f,\"p95_save_ms\":%.6f,\"worst_save_ms\":%.6f,\"file_bytes\":%d,\"file_sha256\":\"%s\"}%n",args[0],(times[9]+times[10])/2,times[18],times[19],length,digest);
    }
}
