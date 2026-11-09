import dev.jayms.net.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Component diagnostic only: no placement, renderer, network or supported-limit claim. */
public final class ModelSnapshotBenchmark {
    static Object retained;
    static long used(){var r=Runtime.getRuntime();return r.totalMemory()-r.freeMemory();}
    static void collect()throws InterruptedException {System.gc();Thread.sleep(100);System.gc();Thread.sleep(100);}
    static double percentile(double[] values,int rank){var sorted=values.clone();Arrays.sort(sorted);return sorted[rank];}
    public static void main(String[] args)throws Exception {
        if(args.length!=3||!Set.of("tree","packed").contains(args[1]))throw new IllegalArgumentException("fixture tree|packed scratch-directory");
        boolean packed=args[1].equals("packed");Path directory=Path.of(args[2]);Files.createDirectories(directory);
        try(var index=DiskPackageIndexPrototype.open(Path.of(args[0]),directory,()->false)) {
            var first=index.model(0,()->false);var encoded=new ByteArrayOutputStream();first.write(new DataOutputStream(encoded));byte[] wire=encoded.toByteArray();
            int quads=ModelMesher.count(first.voxels());String fingerprint=first.fingerprint();first=null;
            double[] elapsed=new double[20],mesh=new double[20];
            for(int i=-3;i<20;i++) {
                long start=System.nanoTime();var model=ModelDefinition.read(new DataInputStream(new ByteArrayInputStream(wire)));
                var snapshot=new ModelDefinition(model.name(),packed?model.voxels().packedModelCopy():model.voxels().copy().freeze());
                double ms=(System.nanoTime()-start)/1e6;
                if(!snapshot.fingerprint().equals(fingerprint))throw new AssertionError("Identity mismatch");
                long meshStart=System.nanoTime();int actual=ModelMesher.count(snapshot.voxels());double meshMs=(System.nanoTime()-meshStart)/1e6;
                if(actual!=quads)throw new AssertionError("Surface mismatch");
                if(i>=0){elapsed[i]=ms;mesh[i]=meshMs;}
            }
            collect();long before=used();var models=new ArrayList<ModelDefinition>();
            for(int i=0;i<index.models.size();i++) {
                var model=index.model(i,()->false);
                models.add(new ModelDefinition(model.name(),packed?model.voxels().packedModelCopy():model.voxels().copy().freeze()));
            }
            retained=models;collect();long estimate=used()-before;
            for(int i=0;i<models.size();i++)if(!models.get(i).fingerprint().equals(index.models.get(i).fingerprint()))throw new AssertionError("Retained identity mismatch");
            System.out.printf(Locale.ROOT,"{\"mode\":\"%s\",\"runs\":20,\"warmups\":3,\"models_retained\":%d,\"decode_snapshot_median_ms\":%.6f,\"decode_snapshot_p95_ms\":%.6f,\"decode_snapshot_worst_ms\":%.6f,\"post_gc_heap_delta_estimate_bytes\":%d,\"exact_identity_and_surface_verified\":true,\"qualification\":false,\"decode_snapshot_samples_ms\":%s,\"mesh_count_samples_ms\":%s,\"mesh_count_median_ms\":%.6f,\"mesh_count_p95_ms\":%.6f,\"mesh_count_worst_ms\":%.6f}%n",args[1],models.size(),(percentile(elapsed,9)+percentile(elapsed,10))/2,percentile(elapsed,18),percentile(elapsed,19),estimate,Arrays.toString(elapsed),Arrays.toString(mesh),(percentile(mesh,9)+percentile(mesh,10))/2,percentile(mesh,18),percentile(mesh,19));
        }
    }
}
