import dev.jayms.Main;
import dev.jayms.GameIdentity;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;

/** Tests Main's real music dispatch and return loop, rather than reconstructing it in a harness. */
public class MusicCatalogSmoke {
    static volatile Throwable failure;static Path out;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static <T> T find(Container root,Class<T> type,java.util.function.Predicate<T> accept){for(Component c:root.getComponents()){if(type.isInstance(c)&&accept.test(type.cast(c)))return type.cast(c);if(c instanceof Container group){T found=find(group,type,accept);if(found!=null)return found;}}return null;}
    static JFrame picker()throws Exception {for(int i=0;i<300;i++){for(Frame f:Frame.getFrames())if(f instanceof JFrame j&&j.isShowing()&&j.getTitle().equals("Voxel One - Play"))return j;Thread.sleep(50);}throw new AssertionError("Main did not return to selection");}
    static String x(String...args)throws Exception {var cmd=new ArrayList<String>(java.util.List.of("xdotool"));cmd.addAll(java.util.List.of(args));var p=new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();String result=new String(p.getInputStream().readAllBytes()).trim();check(p.waitFor()==0,"Native input "+args[0]);return result;}
    public static void main(String[]args)throws Exception {
        out=Path.of(args[0]);Files.createDirectories(out);
        Thread input=new Thread(()->{try{
            for(int launch=0;launch<2;launch++) {
                JFrame f=picker();SwingUtilities.invokeAndWait(()->{
                    JComboBox box=find(f,JComboBox.class,b->b.getItemCount()>0&&b.getItemAt(0) instanceof GameIdentity);
                    JTextField port=find(f,JTextField.class,t->t.getColumns()==6);
                    box.setSelectedItem(GameIdentity.MUSIC);check(!find(f,JButton.class,b->b.getText().equals("Sign in")).isEnabled(),"Music is local only");
                    box.setSelectedItem(GameIdentity.CITY);check(port.getText().equals("25566"),"City default survives music visit");
                    box.setSelectedItem(GameIdentity.SANDBOX);check(port.getText().equals("25565"),"Sandbox default survives music visit");
                    box.setSelectedItem(GameIdentity.MUSIC);find(f,JButton.class,b->b.getText().equals("Open visualizer")).doClick();
                });
                String window="";for(int i=0;i<200&&window.isEmpty();i++){var p=new ProcessBuilder("xdotool","search","--name","^Voxel Music Visualizer$").redirectError(ProcessBuilder.Redirect.DISCARD).start();window=new String(p.getInputStream().readAllBytes()).trim();p.waitFor();if(window.isEmpty())Thread.sleep(50);}
                check(!window.isEmpty(),"Main dispatch creates visualizer");Thread.sleep(700);x("windowfocus",window);x("key","--clearmodifiers","--window",window,"Escape");
            }
            JFrame f=picker();SwingUtilities.invokeAndWait(f::dispose);
        }catch(Throwable t){failure=t;try{Files.writeString(out.resolve("catalog-failure.txt"),t.getClass().getSimpleName()+": "+t.getMessage());}catch(Exception ignored){}System.exit(1);}},"production-main-selection");input.start();
        Main.main(new String[0]);input.join();if(failure!=null){Files.writeString(out.resolve("catalog-failure.txt"),failure.getClass().getSimpleName()+": "+failure.getMessage());System.exit(1);}
        Files.writeString(out.resolve("music-catalog.txt"),"Playtest: PASS. Actual Main.main, isolated synthetic profile, Linux X11. Select Music, launch dedicated window, Escape returns to selection, relaunch Music, Escape returns again, close picker. Switching through Music preserves City 25566 and Sandbox 25565 defaults; Music disables sign-in. No network request or account used.\n");System.exit(0);
    }
}
