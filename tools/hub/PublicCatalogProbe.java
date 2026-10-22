import dev.jayms.launcher.*;
import java.nio.file.*;
public class PublicCatalogProbe {
 public static void main(String[] args)throws Exception {
  var store=new CatalogStore(Path.of(args[0]));var online=store.refresh();var offline=new CatalogStore(Path.of(args[0])).cached();
  if(!online.catalog().equals(offline.catalog()))throw new AssertionError("Cached catalog changed");
  if(args.length==2) {
   try(var input=Files.newInputStream(Path.of(args[1]))) {
    var previous=HubCatalog.read(input);
    if(previous.equals(online.catalog()))throw new AssertionError("Published catalog has not advanced from baseline");
    for(var before:previous.entries()) {
     var after=online.catalog().entries().stream().filter(e->e.id().equals(before.id())).findFirst();
     if(after.isPresent() && before.adapter().equals("native") && after.get().adapter().equals("native")
        && !before.artifact().equals(after.get().artifact()))throw new AssertionError("Native artifact identity changed");
    }
   }
  }
  if(online.catalog().entries().size()<3)throw new AssertionError("Published entries absent");
  System.out.println("Playtest: actual public catalog refreshed and parsed; fresh store loaded same verified cached catalog; entries="+online.catalog().entries().size()+"; no second publication claim.");
 }
}
