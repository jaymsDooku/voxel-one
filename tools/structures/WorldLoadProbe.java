import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.security.MessageDigest;
import java.io.IOException;

/** Actual offline constructor diagnostic. Warm cache, not cold region/city qualification. */
public final class WorldLoadProbe {
    static volatile LocalGame retained;
    static List<String> identities(ModelLibrary library) {
        return library.entries().stream().map(e->e.id()+":"+e.author()+":"+e.definition().name()+":"+e.definition().fingerprint()).toList();
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("synthetic-save-path fresh-synthetic-output-directory required");
        Path file=Path.of(args[0]),folder=Path.of(args[1]);Files.createDirectory(folder);
        byte[] bytes=Files.readAllBytes(file);String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if(!List.of("76bc1e642310fb2a58487f28934e16137a95fedf1d7b1b1c4f673a109d3bb1dc","072c1aa5600d23320a0695f60cf044a7de3a5822b8162869b609e960dd82492b").contains(hash))throw new IllegalArgumentException("Expected the WorldSaveProbe synthetic fixture");
        var initial=new LocalGame(file,0);var edits=new ArrayList<>(initial.edits.entrySet());var models=identities(initial.models);
        if(edits.size()!=100000)throw new AssertionError("Fixture count");double[] times=new double[20];
        for(int run=-3;run<20;run++) {
            long start=System.nanoTime();var loaded=new LocalGame(file,0);double elapsed=(System.nanoTime()-start)/1e6;
            if(run>=0)times[run]=elapsed;
            if(loaded.seed!=42||!edits.equals(new ArrayList<>(loaded.edits.entrySet()))||!models.equals(identities(loaded.models)))throw new AssertionError("Changed reload state");
            retained=loaded;
        }
        Path truncated=folder.resolve("truncated.dat"),invalid=folder.resolve("invalid.dat");
        Files.write(truncated,Arrays.copyOf(bytes,bytes.length-1));Files.write(invalid,ByteBuffer.allocate(16).putInt(12345).array());
        for(var bad:List.of(truncated,invalid)) {
            boolean rejected=false;try{new LocalGame(bad,0);}catch(IOException expected){rejected=true;}
            if(!rejected)throw new AssertionError("Malformed world accepted");
            var retry=new LocalGame(file,0);
            if(!edits.equals(new ArrayList<>(retry.edits.entrySet()))||!models.equals(identities(retry.models)))throw new AssertionError("Retry changed state");
        }
        if(!Arrays.equals(bytes,Files.readAllBytes(file)))throw new AssertionError("Load changed file");
        Arrays.sort(times);
        System.out.printf(Locale.ROOT,"{\"edits\":100000,\"warmups\":3,\"runs\":20,\"median_load_ms\":%.6f,\"p95_load_ms\":%.6f,\"worst_load_ms\":%.6f,\"file_bytes\":%d,\"file_sha256\":\"%s\",\"malformed_rejections\":2,\"retry_state_verified\":true}%n",(times[9]+times[10])/2,times[18],times[19],bytes.length,hash);
    }
}
