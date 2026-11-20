package dev.jayms.combat.hcf;

import dev.jayms.combat.duel.PracticeView;
import dev.jayms.net.SecureTransport;
import dev.jayms.recording.ScreenRecorder;
import dev.jayms.ui.Controls;
import dev.jayms.ui.Controls.Action;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;

/** Native HCF entry. Explicit sign-in and team choice precede opt-in deployment. */
public final class HcfApp {
    private record Launch(boolean local,String host,int port,String pin,String name,char[] password,boolean register,int team) {}
    private static Launch choose(String address,int number,String fingerprint,boolean localDefault) throws Exception {
        Launch[] result={null};SwingUtilities.invokeAndWait(()->{
            var local=new JCheckBox("Local persistent HCF server",localDefault);var host=new JTextField(address);var port=new JTextField(Integer.toString(number));var pin=new JTextField(fingerprint);
            var name=new JTextField();name.setName("hcf-name");var password=new JPasswordField();password.setName("hcf-password");
            var register=new JCheckBox("Create HCF account (first visit)",true);register.setName("hcf-register");
            var team=new JComboBox<>(new String[]{"Blue","Gold"});team.setName("hcf-team");
            var panel=new JPanel(new java.awt.GridLayout(0,1,4,4));panel.add(new JLabel("HCF is opt-in. Combat logout is defeat. Deathban: 60s by default."));
            panel.add(local);panel.add(new JLabel("Server"));panel.add(host);panel.add(new JLabel("Port"));panel.add(port);panel.add(new JLabel("Trusted server certificate SHA-256 (online)"));panel.add(pin);
            panel.add(new JLabel("Separate HCF username"));panel.add(name);panel.add(new JLabel("HCF password (10-128 characters; never saved by client)"));panel.add(password);panel.add(register);panel.add(team);
            if(JOptionPane.showConfirmDialog(null,panel,"Voxel One HCF",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION)
                result[0]=new Launch(local.isSelected(),host.getText().trim(),Integer.parseInt(port.getText().trim()),pin.getText().trim(),name.getText().trim(),password.getPassword(),register.isSelected(),team.getSelectedIndex());
        });return result[0];
    }
    public static void main(String[] args) throws Exception {
        boolean local=true;String host="127.0.0.1",pin="";int port=HcfWire.PORT;
        for(int i=0;i<args.length;i++)switch(args[i]) {
            case "--game" -> {if(!args[++i].equals("hcf"))throw new IllegalArgumentException("HCF game identity");}
            case "--offline" -> local=true;case "--server" -> {host=args[++i];local=false;}case "--port" -> port=Integer.parseInt(args[++i]);case "--fingerprint" -> pin=args[++i];
            default -> throw new IllegalArgumentException("--game hcf [--offline | --server HOST --port PORT --fingerprint SHA256]");
        }
        var launch=choose(host,port,pin,local);if(launch==null)return;
        Path directory=Controls.directory().resolve("hcf");Files.createDirectories(directory);HcfServer server=null;
        try {
            if(launch.local()) {
                var tls=SecureTransport.server(directory.resolve("tls"));
                server=new HcfServer(tls.context(),InetAddress.getLoopbackAddress(),0,directory.resolve("server"),HcfRules.initial(),System::currentTimeMillis,HcfSession.Phase.ACTIVE,false);
                launch=new Launch(true,"127.0.0.1",server.port(),tls.fingerprint(),launch.name(),launch.password(),launch.register(),launch.team());
            }
            try(var client=new HcfClient(launch.host(),launch.port(),launch.pin(),launch.name(),launch.password(),launch.register(),launch.team())) {run(client,directory);}
        }finally{Arrays.fill(launch.password(),'\0');if(server!=null)server.close();}
    }
    public static void run(HcfClient client,Path directory) throws Exception {
        if(!glfwInit())throw new IllegalStateException("GLFW initialization failed");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(1100,700,"Voxel One | HCF factions",0,0);if(window==0){glfwTerminate();throw new IllegalStateException("HCF window failed");}
        glfwMakeContextCurrent(window);glfwSwapInterval(1);GL.createCapabilities();
        var controls=new Controls(Controls.directory().resolve("controls.properties"));var edges=new EnumMap<Action,Boolean>(Action.class);boolean[] keys=new boolean[GLFW_KEY_LAST+1],mouse=new boolean[2];double[] last={Double.NaN,0};int[] slot={0},roleChoice={-1};
        glfwSetInputMode(window,GLFW_CURSOR,GLFW_CURSOR_DISABLED);glfwSetScrollCallback(window,(w,x,y)->slot[0]=Math.floorMod(slot[0]-(int)Math.signum(y),9));
        try(var view=new PracticeView();var recorder=new ScreenRecorder(directory.resolve("recordings"))) {
            long frame=0,lastInput=0;String message="";
            while(!glfwWindowShouldClose(window)) {
                glfwPollEvents();int[] w={0},h={0};glfwGetFramebufferSize(window,w,h);if(w[0]<1 || h[0]<1){Thread.sleep(20);continue;}
                double[] x={0},y={0};glfwGetCursorPos(window,x,y);
                if(!Double.isNaN(last[0])){view.camera.setYaw((float)((view.camera.yaw()+(x[0]-last[0])*controls.sensitivity)%360));view.camera.setPitch((float)(view.camera.pitch()-(y[0]-last[1])*controls.sensitivity));}last[0]=x[0];last[1]=y[0];
                boolean left=controls.down(window,Action.BREAK),right=controls.down(window,Action.PLACE);
                try {
                    if(pressed(window,GLFW_KEY_ESCAPE,keys))break;
                    if(pressed(window,controls,Action.RECORD,edges))recorder.toggle(w[0],h[0]);
                    if(pressed(window,GLFW_KEY_C,keys)){client.reconnect();message="Signed back in";}
                    if(pressed(window,GLFW_KEY_R,keys))client.operation(HcfCommand.Op.DEPLOY,0);
                    if(pressed(window,GLFW_KEY_E,keys) && client.self()!=null) {
                        roleChoice[0]=((roleChoice[0]<0?client.self().armor().ordinal():roleChoice[0])+1)%HcfRole.values().length;
                        client.operation(HcfCommand.Op.EQUIP,roleChoice[0]);
                    }
                    int[] bindings={GLFW_KEY_B,GLFW_KEY_J,GLFW_KEY_H,GLFW_KEY_L,GLFW_KEY_G,GLFW_KEY_T,GLFW_KEY_U};
                    HcfCommand.Op[] ops={HcfCommand.Op.RESUPPLY,HcfCommand.Op.ABILITY,HcfCommand.Op.HOME,HcfCommand.Op.SAFE_LOGOUT,HcfCommand.Op.CLAIM,HcfCommand.Op.RAID,HcfCommand.Op.LOOT};
                    for(int i=0;i<bindings.length;i++)if(pressed(window,bindings[i],keys))client.operation(ops[i],0);
                    if(pressed(window,GLFW_KEY_F,keys))client.refill(slot[0]);
                    for(int i=0;i<9;i++)if(pressed(window,controls,Action.values()[Action.SLOT_1.ordinal()+i],edges))slot[0]=i;
                    long now=System.nanoTime();if(client.connected() && (now-lastInput>=50_000_000L || left!=mouse[0] || right!=mouse[1])) {
                        client.input(frame,axis(window,controls,Action.FORWARD,Action.BACKWARD),axis(window,controls,Action.RIGHT,Action.LEFT),view.camera.yaw(),view.camera.pitch(),controls.down(window,Action.JUMP),controls.down(window,Action.SPRINT),left && !mouse[0],right && !mouse[1],!right && mouse[1],slot[0]);lastInput=now;
                    }
                }catch(java.io.IOException e){message="Connection lost. Press C to sign back in.";}
                mouse[0]=left;mouse[1]=right;view.renderHcf(client,w[0],h[0],message+" "+recorder.status("F10"));recorder.capture(w[0],h[0]);glfwSwapBuffers(window);frame++;
            }
        }finally{org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(window);glfwDestroyWindow(window);glfwTerminate();}
    }
    private static int axis(long w,Controls c,Action positive,Action negative){return (c.down(w,positive)?1:0)-(c.down(w,negative)?1:0);}
    private static boolean pressed(long w,int key,boolean[] prior){boolean down=glfwGetKey(w,key)==GLFW_PRESS,edge=down && !prior[key];prior[key]=down;return edge;}
    private static boolean pressed(long w,Controls c,Action key,EnumMap<Action,Boolean> prior){boolean down=c.down(w,key),edge=down && !prior.getOrDefault(key,false);prior.put(key,down);return edge;}
}
