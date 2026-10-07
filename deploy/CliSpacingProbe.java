import dev.jayms.Main;
import dev.jayms.net.city.RoadSpacing;
public class CliSpacingProbe {
 public static void main(String[] args) throws Exception {
  for (String value : new String[]{"0","2"}) {
   try { Main.main(new String[]{"--pedestrian-spacing",value,"--mounted-spacing",value,"--probe-stop"}); throw new AssertionError("Expected usage stop"); }
   catch (IllegalArgumentException e) { if (!e.getMessage().startsWith("Usage:")) throw e; }
   var gap=RoadSpacing.configured();
   if(gap.pedestrians()!=Float.parseFloat(value)||gap.mounted()!=Float.parseFloat(value))throw new AssertionError("CLI values lost");
  }
  try {Main.main(new String[]{"--pedestrian-spacing","-1","--probe-stop"});throw new AssertionError("Negative accepted");}
  catch(IllegalArgumentException e){if(e.getMessage().startsWith("Usage:"))throw new AssertionError("Negative reached usage stop");}
  System.out.println("PASS: Main CLI accepts 0 and 2 for both gaps; rejects -1 before app startup.");
 }
}
