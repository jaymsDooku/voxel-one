import dev.jayms.Main;
import dev.jayms.net.MultiplayerServer;
import dev.jayms.net.city.RoadSpacing;
public class RailwaySpacingCliCheck {
 public static void main(String[] args) throws Exception {
  for(boolean server:new boolean[]{false,true}) {
   String[] valid={"--pedestrian-spacing","0.8","--mounted-spacing","1.4","--recovery-cli-stop"};
   try {if(server)MultiplayerServer.main(valid);else Main.main(valid);throw new AssertionError("Expected deliberate parser stop");}
   catch(IllegalArgumentException e) {if(!e.getMessage().startsWith("Usage:"))throw e;}
   if(RoadSpacing.configured().pedestrians()!=.8f||RoadSpacing.configured().mounted()!=1.4f)throw new AssertionError("Spacing options not consumed");
   try {String[] bad={"--pedestrian-spacing","-1"};if(server)MultiplayerServer.main(bad);else Main.main(bad);throw new AssertionError("Negative spacing accepted");}
   catch(IllegalArgumentException e) {if(!e.getMessage().equals("Road spacing must be finite and between 0 and 2 blocks"))throw e;}
  }
  System.out.println("PASS: client/server accept both spacing options and reject negative spacing before runtime startup.");
 }
}
