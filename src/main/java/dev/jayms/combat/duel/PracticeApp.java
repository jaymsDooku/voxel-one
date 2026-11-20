package dev.jayms.combat.duel;

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

/** Opt-in native practice client. No City/Sandbox world or account is loaded. */
public final class PracticeApp {
    private record Launch(boolean offline,String host,int port,String pin,DuelSession.Profile profile,String spectate) {}
    private static Launch choose() throws Exception {
        Launch[] result={null};
        SwingUtilities.invokeAndWait(()->{
            var profile=new JComboBox<>(new String[]{"No Debuff","Debuff"});var offline=new JCheckBox("Offline drill target",true);
            var host=new JTextField("127.0.0.1");var port=new JTextField(Integer.toString(DuelWire.PORT));var pin=new JTextField();
            var panel=new JPanel(new java.awt.GridLayout(0,1,4,4));
            panel.add(new JLabel("Practice duels - opt-in combat in Basalt Courtyard"));panel.add(profile);panel.add(offline);
            panel.add(new JLabel("Online server"));panel.add(host);panel.add(new JLabel("Port"));panel.add(port);
            panel.add(new JLabel("Trusted server certificate SHA-256 (online only)"));panel.add(pin);
            if(JOptionPane.showConfirmDialog(null,panel,"Voxel One practice",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION)
                result[0]=new Launch(offline.isSelected(),host.getText().trim(),Integer.parseInt(port.getText().trim()),pin.getText().trim(),profile.getSelectedIndex()==0?DuelSession.Profile.NO_DEBUFF:DuelSession.Profile.DEBUFF,"");
        });return result[0];
    }
    public static void main(String[] args) throws Exception {
        Launch launch;
        if(args.length==0 || (args.length==2 && args[0].equals("--game") && args[1].equals("practice"))) launch=choose();
        else {
            boolean offline=false;String host="127.0.0.1",pin="",spectate="";int port=DuelWire.PORT;var profile=DuelSession.Profile.NO_DEBUFF;
            for(int i=0;i<args.length;i++) switch(args[i]) {
                case "--game" -> {if(!args[++i].equals("practice"))throw new IllegalArgumentException("Practice game identity");}
                case "--offline" -> offline=true;
                case "--server" -> host=args[++i];
                case "--port" -> port=Integer.parseInt(args[++i]);
                case "--fingerprint" -> pin=args[++i];
                case "--profile" -> profile=switch(args[++i]){case "no-debuff"->DuelSession.Profile.NO_DEBUFF;case "debuff"->DuelSession.Profile.DEBUFF;default->throw new IllegalArgumentException("Profile must be no-debuff or debuff");};
                case "--spectate" -> spectate=args[++i];
                default -> throw new IllegalArgumentException("Usage: --game practice --offline | --server HOST --port PORT --fingerprint SHA256 [--profile no-debuff|debuff] [--spectate MATCH]");
            }
            if(port<1 || port>65535 || (offline && !spectate.isEmpty())) throw new IllegalArgumentException("Practice launch options");
            launch=new Launch(offline,host,port,pin,profile,spectate);
        }
        if(launch==null)return;
        Path directory=Controls.directory().resolve("practice");Files.createDirectories(directory);
        DuelServer local=null;
        try {
            if(launch.offline()) {
                var identity=SecureTransport.server(directory.resolve("local-tls"));
                local=new DuelServer(identity.context(),InetAddress.getLoopbackAddress(),0,true);
                launch=new Launch(true,"127.0.0.1",local.port(),identity.fingerprint(),launch.profile(),"");
            }
            try(var client=new DuelClient(launch.host(),launch.port(),launch.pin(),launch.profile(),!launch.spectate().isEmpty(),launch.spectate(),0)) {run(client,directory);}
        }finally{if(local!=null)local.close();}
    }
    public static void run(DuelClient client,Path directory) throws Exception {
        if(!glfwInit())throw new IllegalStateException("GLFW initialization failed");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(1100,700,"Voxel One | Practice duels",0,0);
        if(window==0){glfwTerminate();throw new IllegalStateException("Practice window failed");}
        glfwMakeContextCurrent(window);glfwSwapInterval(1);GL.createCapabilities();
        var controls=new Controls(Controls.directory().resolve("controls.properties"));
        var actionEdges=new java.util.EnumMap<Action,Boolean>(Action.class);
        boolean[] previous=new boolean[GLFW_KEY_LAST+1];boolean[] mouse={false,false};double[] last={Double.NaN,0};int[] slot={0};
        boolean[] inventory={false};int[] selected={0,-1};String message="";
        glfwSetInputMode(window,GLFW_CURSOR,GLFW_CURSOR_DISABLED);
        glfwSetScrollCallback(window,(w,x,y)->{slot[0]=Math.floorMod(slot[0]-(int)Math.signum(y),9);});
        try(var view=new PracticeView();var recorder=new ScreenRecorder(directory.resolve("recordings"))) {
            long frame=0,lastInput=0,lastEpoch=-1;
            while(!glfwWindowShouldClose(window)) {
                glfwPollEvents();int[] width={0},height={0};glfwGetFramebufferSize(window,width,height);
                if(width[0]<1 || height[0]<1){Thread.sleep(20);continue;}
                var state=client.state();
                if(state!=null && state.epoch()!=lastEpoch){lastEpoch=state.epoch();slot[0]=0;}
                if(state!=null && state.phase()==DuelSession.Phase.LIVE && inventory[0]) {
                    inventory[0]=false;selected[1]=-1;glfwSetInputMode(window,GLFW_CURSOR,GLFW_CURSOR_DISABLED);
                }
                double[] x={0},y={0};glfwGetCursorPos(window,x,y);
                if(!Double.isNaN(last[0]) && !inventory[0]) {view.camera.setYaw((float)((view.camera.yaw()+(x[0]-last[0])*controls.sensitivity)%360));view.camera.setPitch((float)(view.camera.pitch()-(y[0]-last[1])*controls.sensitivity));}
                last[0]=x[0];last[1]=y[0];
                boolean left=controls.down(window,Action.BREAK),right=controls.down(window,Action.PLACE);
                try {
                    if(pressed(window,GLFW_KEY_ESCAPE,previous))break;
                    if(pressed(window,GLFW_KEY_C,previous)){client.reconnect();message="Reconnect requested";}
                    if(pressed(window,controls,Action.RECORD,actionEdges))recorder.toggle(width[0],height[0]);
                    if(pressed(window,GLFW_KEY_R,previous))client.operation(DuelSession.Op.READY,0,0);
                    if(pressed(window,GLFW_KEY_N,previous))client.operation(DuelSession.Op.QUEUE,0,0);
                    if(pressed(window,GLFW_KEY_Q,previous))client.operation(DuelSession.Op.FORFEIT,0,0);
                    if(pressed(window,GLFW_KEY_F,previous))client.operation(DuelSession.Op.REFILL,slot[0],0);
                    if(pressed(window,controls,Action.INVENTORY,actionEdges)) {
                        if(inventory[0]) {inventory[0]=false;glfwSetInputMode(window,GLFW_CURSOR,GLFW_CURSOR_DISABLED);}
                        else if(state!=null && state.phase()==DuelSession.Phase.LIVE) message="Kit edits between rounds; F refills an empty hotbar slot";
                        else {inventory[0]=!inventory[0];glfwSetInputMode(window,GLFW_CURSOR,inventory[0]?GLFW_CURSOR_NORMAL:GLFW_CURSOR_DISABLED);}
                    }
                    if(inventory[0]) {
                        if(pressed(window,GLFW_KEY_LEFT,previous)) selected[0]=Math.floorMod(selected[0]-1,36);
                        if(pressed(window,GLFW_KEY_RIGHT,previous)) selected[0]=(selected[0]+1)%36;
                        if(pressed(window,GLFW_KEY_UP,previous)) selected[0]=Math.floorMod(selected[0]-9,36);
                        if(pressed(window,GLFW_KEY_DOWN,previous)) selected[0]=(selected[0]+9)%36;
                        if(pressed(window,GLFW_KEY_ENTER,previous)) {
                            if(selected[1]<0)selected[1]=selected[0];else{client.operation(DuelSession.Op.SWAP,selected[1],selected[0]);selected[1]=-1;}
                        }
                    }
                    for(int i=0;i<9;i++)if(pressed(window,controls,Action.values()[Action.SLOT_1.ordinal()+i],actionEdges))slot[0]=i;
                    long now=System.nanoTime();
                    if(client.connected() && (now-lastInput>=50_000_000L || left!=mouse[0] || right!=mouse[1])) {
                        client.input(frame,inventory[0]?0:axis(window,controls,Action.FORWARD,Action.BACKWARD),inventory[0]?0:axis(window,controls,Action.RIGHT,Action.LEFT),view.camera.yaw(),view.camera.pitch(),
                                !inventory[0] && controls.down(window,Action.JUMP),!inventory[0] && controls.down(window,Action.SPRINT),!inventory[0] && left && !mouse[0],!inventory[0] && right && !mouse[1],!right && mouse[1],slot[0]);
                        lastInput=now;
                    }
                }catch(java.io.IOException e){message="Connection lost. Press C to reconnect.";}
                mouse[0]=left;mouse[1]=right;
                view.render(client,width[0],height[0],inventory[0],selected[1],selected[0],message);
                recorder.capture(width[0],height[0]);glfwSwapBuffers(window);frame++;
            }
        }finally {
            if(client.connected())try{client.operation(DuelSession.Op.FORFEIT,0,0);}catch(java.io.IOException ignored){}
            org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(window);glfwDestroyWindow(window);glfwTerminate();
        }
    }
    private static int key(long window,int key){return glfwGetKey(window,key)==GLFW_PRESS?1:0;}
    private static int axis(long window,Controls controls,Action positive,Action negative){return (controls.down(window,positive)?1:0)-(controls.down(window,negative)?1:0);}
    private static boolean pressed(long window,Controls controls,Action action,java.util.EnumMap<Action,Boolean> previous){boolean down=controls.down(window,action),edge=down && !previous.getOrDefault(action,false);previous.put(action,down);return edge;}
    private static boolean pressed(long window,int key,boolean[] previous){boolean down=key(window,key)>0,edge=down && !previous[key];previous[key]=down;return edge;}
}
