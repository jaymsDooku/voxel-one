import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.nio.file.*;
import java.util.*;

/** Actual server authority save phases, synthetic loopback world, no clients or tick loop. */
public final class ServerSaveStagingProbe {
    @SuppressWarnings("unchecked") static <T>T field(MultiplayerServer server,String name)throws Exception {var f=MultiplayerServer.class.getDeclaredField(name);f.setAccessible(true);return (T)f.get(server);}
    public static void main(String[] args)throws Exception {
        if(args.length!=2||Files.exists(Path.of(args[1])))throw new IllegalArgumentException("Synthetic private profile and fresh result path required");
        var folder=Path.of(args[0]);Files.createDirectories(folder);var save=folder.resolve("world.dat");
        var tls=SecureTransport.server(folder.resolve("synthetic-tls"));var accounts=new AccountStore(folder.resolve("synthetic-accounts"));
        double[] legacy=new double[20],stage=new double[20],publish=new double[20];long size=0;
        try(var server=new MultiplayerServer("127.0.0.1",0,save,accounts,tls.context(),42,GameConfig.sandbox())) {
            var method=MultiplayerServer.class.getDeclaredMethod("structureAuthority");method.setAccessible(true);var authority=(StructureEngine.Authority)method.invoke(server);
            var history=ServerSaveStagingProbe.<WorldVoxels.History>field(server,"edits");var geometry=ServerSaveStagingProbe.<WorldVoxels>field(server,"voxels");
            for(int i=0;i<131072;i++){var edit=new Protocol.Edit(1000+i%512,180,1000+i/512,Blocks.STONE);history.put(edit.key(),edit);geometry.apply(edit);}
            var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.BRICKS));var cells=Map.of(new StructurePackage.Position(320,180,320),tree.freeze());
            for(int run=-3;run<20;run++) {
                long start=System.nanoTime();synchronized(server){authority.commit(authority.models().copy(),cells);}long old=System.nanoTime()-start;byte[] expected=Files.readAllBytes(save);
                start=System.nanoTime();var staged=authority.stageCommit(authority.models().copy(),cells,authority.models().revision(),()->false);long prep=System.nanoTime()-start;
                long committed;
                try(staged){start=System.nanoTime();synchronized(server){staged.apply();}committed=System.nanoTime()-start;}
                byte[] actual=Files.readAllBytes(save);if(!Arrays.equals(expected,actual))throw new AssertionError("Save bytes differ");size=actual.length;
                if(run>=0){legacy[run]=old/1e6;stage[run]=prep/1e6;publish[run]=committed/1e6;}
            }
        }
        Files.writeString(Path.of(args[1]),"{\"scope\":\"Actual server authority, 131072 existing whole-cell edits plus one replacement cell, no dependencies/inventories/drops/clients/tick loop. Three warmups, 20 measured runs, legacy first. Exact save bytes verified outside timing. Staging includes history copy under lock; publication includes live-body validation, geometry merge, tail append, force and atomic rename. Excludes full engine validation, capture, mesh, network, rendering, memory and city/max qualification.\",\"save_bytes\":"+size+",\"legacy_guarded_commit_ms\":"+stats(legacy)+",\"staging_ms\":"+stats(stage)+",\"guarded_publication_ms\":"+stats(publish)+"}\n");
    }
    static String stats(double[] values){Arrays.sort(values);return String.format(Locale.ROOT,"{\"median\":%.6f,\"p95\":%.6f,\"worst\":%.6f}",(values[9]+values[10])/2,values[18],values[19]);}
}
