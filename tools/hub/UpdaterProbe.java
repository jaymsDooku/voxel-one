import dev.jayms.launcher.*;
import java.net.URI;
import java.nio.file.*;

/** CLI for scoped synthetic package tests. No network/account data is logged. */
public final class UpdaterProbe {
    public static void main(String[] args) throws Exception {
        Updater updater=new Updater(Path.of(args[1]),URI.create(args[2]),Boolean.parseBoolean(args[3]));
        if(args[0].equals("catalog")) {
            var entry=HubCatalog.bundled().entries().stream().filter(e->e.id().equals("city")).findFirst().orElseThrow();
            var result=updater.update(Platform.current(),s->{},entry.clientSources());
            entry.requireClient(result);System.out.println(result.client());
        } else if(args[0].equals("cancel")) {
            Thread cancel=new Thread(()->{try{Thread.sleep(750);updater.cancel();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            cancel.start();boolean cancelled=false;
            try {updater.update(Platform.current(),s->{});}catch(java.io.IOException e){cancelled=e.getMessage().contains("cancel");}
            cancel.join();if(!cancelled)throw new AssertionError("Cancellation did not interrupt transfer");
        } else if(args[0].equals("installed")) {
            var result=updater.installed(Platform.current());if(result==null)throw new AssertionError("Verified installation unavailable");System.out.println(result.client());
        } else {
            try { System.out.println(updater.update(Platform.current(),s->{}).client()); }
            catch(java.io.IOException e) {
                if(e.getMessage().startsWith("Another launcher is updating")) System.exit(3);
                throw e;
            }
        }
    }
}
