import java.nio.file.Path;
import java.util.List;

/** Integration controls for the bounded own-child query; no real command lines. */
public final class ProcessQueryProbeTest {
    public static void main(String[] args) throws Exception {
        if(args.length>0) {
            switch(args[0]) {
                case "eof" -> { while(System.in.read()!=-1) { } System.out.print("MATCH"); }
                case "hang" -> Thread.sleep(60000);
                case "wrong" -> System.out.print("NOT_MATCH");
                case "overflow" -> System.out.print("MATCH".repeat(100000));
                case "failed" -> { System.out.print("MATCH"); System.exit(7); }
                default -> throw new AssertionError("Unknown fixture");
            }
            return;
        }
        String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        for(String mode:List.of("eof","hang","wrong","overflow","failed")) {
            long start=System.nanoTime();
            boolean match=PackageUiProbe.boundedMatch(new ProcessBuilder(java,"-cp",System.getProperty("java.class.path"),"ProcessQueryProbeTest",mode),mode.equals("hang")?300:3000);
            long elapsed=(System.nanoTime()-start)/1_000_000;
            if(match!=mode.equals("eof"))throw new AssertionError("Unexpected match: "+mode);
            if(elapsed>4500)throw new AssertionError("Query exceeded bound: "+mode);
            System.out.println("Playtest: own-child query "+mode+" matched="+match+" elapsedMs="+elapsed);
        }
    }
}
