import dev.jayms.launcher.*;
import java.nio.file.*;
public class PublicCatalogProbe {
 public static void main(String[] args)throws Exception {
  var store=new CatalogStore(Path.of(args[0]));var online=store.refresh();var offline=new CatalogStore(Path.of(args[0])).cached();
  if(!online.catalog().version().equals(offline.catalog().version()))throw new AssertionError("Cached catalog changed");
  if(online.catalog().entries().size()<3)throw new AssertionError("Published entries absent");
  System.out.println("Playtest: actual public catalog refreshed and parsed; fresh store loaded same verified cached catalog; entries="+online.catalog().entries().size()+"; no second publication claim.");
 }
}
