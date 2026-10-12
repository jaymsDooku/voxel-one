import dev.jayms.net.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.ui.LoginDialog;
import java.awt.*;
import java.nio.file.*;
import java.util.concurrent.*;
import javax.swing.*;
import javax.imageio.ImageIO;

/** Isolated loopback TLS login playtest. Secrets remain in memory. */
public final class OnlineLoginPlaytest {
    static JFrame window() {
        for(Window w:Window.getWindows()) if(w instanceof JFrame f && f.isShowing() && f.getTitle().equals("Voxel One - Play")) return f;
        return null;
    }
    static JComponent field(JFrame f,String name) {
        for(Component c:HubPlaytest.descendants(f)) if(c instanceof JComponent j && !(c instanceof JLabel) && name.equals(j.getAccessibleContext().getAccessibleName())) return j;
        throw new AssertionError("Login field missing");
    }
    static void click(JFrame f) {
        for(Component c:HubPlaytest.descendants(f))if(c instanceof JButton b && b.getText().equals("Sign in")){b.doClick();return;}
        throw new AssertionError("Sign in missing");
    }
    public static void main(String[] args)throws Exception {
        Path profile=Path.of(args[0]).toAbsolutePath();Files.createDirectories(profile);
        System.setProperty("user.home",profile.toString());
        char[] secret=("Synthetic-"+java.util.UUID.randomUUID()).toCharArray();
        var identity=SecureTransport.server(profile.resolve("tls"));
        var accounts=new AccountStore(profile.resolve("accounts.db"));accounts.register("hub_player",secret);
        try {
            for(boolean city:new boolean[]{true,false}) {
                try(var server=new MultiplayerServer("127.0.0.1",0,profile.resolve(city?"city.save":"sandbox.save"),accounts,identity.context(),42,city?GameConfig.cityGame():GameConfig.sandbox())) {
                    Thread serving=new Thread(()->{try{server.run();}catch(Exception ignored){}});serving.setDaemon(true);serving.start();
                    LoginDialog.citySelected=city;
                    CompletableFuture<MultiplayerClient> login=CompletableFuture.supplyAsync(()->{try{return LoginDialog.open("127.0.0.1",server.port(),identity.fingerprint(),true,true);}catch(Exception e){throw new CompletionException(e);}});
                    long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
                    while(window()==null && System.nanoTime()<end)Thread.sleep(50);
                    JFrame f=window();if(f==null)throw new AssertionError("Login did not open");
                    SwingUtilities.invokeAndWait(()->{
                        if(field(f,"Game").isEnabled())throw new AssertionError("Explicit game not locked");
                        if(((JComboBox<?>)field(f,"Game")).getSelectedItem()!=dev.jayms.GameIdentity.of(city))throw new AssertionError("Wrong selected game");
                        ((JTextField)field(f,"Username")).setText("hub_player");
                        ((JTextField)field(f,"Port")).setText("0");click(f);
                        boolean error=HubPlaytest.descendants(f).stream().anyMatch(c->c instanceof JLabel l && l.getText().equals("Enter a port from 1 to 65535."));
                        if(!error)throw new AssertionError("Invalid port not rejected");
                        ((JTextField)field(f,"Port")).setText(Integer.toString(server.port()));
                    });
                    Thread.sleep(200);
                    ImageIO.write(new Robot().createScreenCapture(f.getBounds()),"png",Path.of(args[1],city?"hub-online-city-login.png":"hub-online-sandbox-login.png").toFile());
                    SwingUtilities.invokeAndWait(()->{((JPasswordField)field(f,"Password")).setText(new String(secret));click(f);});
                    try(var client=login.get(30,TimeUnit.SECONDS)){if(!client.username.equals("hub_player"))throw new AssertionError("Wrong synthetic session");}
                }
            }
            System.out.println("Playtest: City and Sandbox explicit online login, custom server/port, invalid-port edge and pinned TLS passed.");
        } finally {java.util.Arrays.fill(secret,'\0');SwingUtilities.invokeAndWait(()->{for(Window w:Window.getWindows())w.dispose();});}
    }
}
