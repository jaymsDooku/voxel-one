import dev.jayms.GameIdentity;
import dev.jayms.ui.LoginDialog;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;

/** Exercises the real desktop selection UI with a clean synthetic user home. No network calls. */
public final class SandboxSelectionPlaytest {
    static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    static <T> T find(Container parent, Class<T> type, java.util.function.Predicate<T> match) {
        for(var c:parent.getComponents()) {
            if(type.isInstance(c) && match.test(type.cast(c)))return type.cast(c);
            if(c instanceof Container child) { T result=find(child,type,match); if(result!=null)return result; }
        }
        return null;
    }
    static void scenario(boolean city,boolean explicit,int port,boolean custom,Path out) throws Exception {
        LoginDialog.citySelected=city;
        var finished=new java.util.concurrent.CompletableFuture<Void>();
        Thread caller=new Thread(() -> {try {check(LoginDialog.open("127.0.0.1",port,null,explicit,custom)==null,"Offline uses no account/server");finished.complete(null);} catch(Throwable e){finished.completeExceptionally(e);}});
        caller.start();
        JFrame frame=null;
        for(int i=0;i<100 && frame==null;i++) {
            for(var f:Frame.getFrames())if(f instanceof JFrame jf && jf.isVisible())frame=jf;
            Thread.sleep(50);
        }
        check(frame!=null,"Selection window opens"); final JFrame ui=frame;
        SwingUtilities.invokeAndWait(() -> {
            var selector=find(ui,JComboBox.class,c->true);
            var field=find(ui,JTextField.class,c->c.getColumns()==6);
            find(ui,JTextField.class,c->c.getColumns()==20 && !(c instanceof JPasswordField)).setText("sandbox_fixture");
            check(selector.getSelectedItem()==GameIdentity.of(city),"Initial game matches flags");
            check(selector.isEnabled()!=explicit,"Explicit game locks selector");
            check(field.getText().equals(""+port),"Initial port is preserved");
            if(!explicit) {
                selector.setSelectedItem(GameIdentity.of(!city));
                check(field.getText().equals(""+(custom?port:GameIdentity.of(!city).port)),"Port follows defaults, preserves explicit port");
                field.setText("23456"); selector.setSelectedItem(GameIdentity.of(city));
                check(field.getText().equals("23456"),"Typed custom port survives switch");
                field.setText(""+GameIdentity.of(city).port); selector.setSelectedItem(GameIdentity.of(!city));
                check(field.getText().equals(""+GameIdentity.of(city).port),"Typed default-number port is custom");
                selector.setSelectedItem(GameIdentity.SANDBOX);
            }
        });
        if(!explicit) {
            var process=new ProcessBuilder("import","-window","Voxel One - Play",out.resolve("voxel-sandbox-selection.png").toString()).start();
            check(process.waitFor()==0,"Selection screenshot");
        }
        SwingUtilities.invokeAndWait(() -> find(ui,JButton.class,b->b.getText().equals("Play offline")).doClick());
        finished.get(10,java.util.concurrent.TimeUnit.SECONDS);
        check(LoginDialog.citySelected==(explicit?city:false),"Offline fallback retains selected game");
    }
    public static void main(String[] args)throws Exception {
        var out=Path.of(args[0]);
        scenario(false,false,25565,false,out);
        scenario(true,true,25565,true,out);
        scenario(false,true,25566,true,out);
        Files.writeString(out.resolve("voxel-sandbox-selection-result.txt"),"Playtest: PASS. Linux X11 Swing desktop selection; clean synthetic home, loopback host, no network request. Default Sandbox selection, City switch with port 25566, typed/custom ports (including default-number values), explicit City/Sandbox locks, and Play offline without credentials all match expected behavior. Screenshot captured from running selection UI.\n");
    }
}
