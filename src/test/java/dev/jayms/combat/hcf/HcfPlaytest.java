package dev.jayms.combat.hcf;

import dev.jayms.Main;
import dev.jayms.combat.*;
import dev.jayms.net.SecureTransport;
import dev.jayms.ui.Controls;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.*;

/** Normal Main entry, production keyboard/F10, four real TLS accounts and the actual arena. */
public final class HcfPlaytest {
    private static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);}
    private static void await(BooleanSupplier condition,long millis,String reason) throws Exception {
        long end=System.nanoTime()+millis*1_000_000;while(!condition.getAsBoolean() && System.nanoTime()<end)Thread.sleep(20);check(condition.getAsBoolean(),reason);
    }
    private static char[] secret(){byte[] b=new byte[16];new SecureRandom().nextBytes(b);return HexFormat.of().formatHex(b).toCharArray();}
    private static HcfSession.MemberView member(HcfServer s,String id){return s.state().members().stream().filter(m->m.id().equals(id)).findFirst().orElse(null);}
    private static void fill(Component c,char[] password) {
        if(c instanceof JTextField text && "hcf-name".equals(c.getName()))text.setText("bluebard");
        if(c instanceof JPasswordField text && "hcf-password".equals(c.getName()))text.setText(new String(password));
        if(c instanceof Container parent)for(Component child:parent.getComponents())fill(child,password);
    }
    private static String command(String... args) throws Exception {
        var process=new ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start();String result=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);check(process.waitFor()==0,"Native window command");return result.trim();
    }
    private static void key(String window,String key) throws Exception {
        command("xdotool","key","--clearmodifiers","--window",window,"--delay","160",key);Thread.sleep(250);
    }
    private static void capture(String window,Path path) throws Exception {command("import","-window",window,path.toString());}
    private static void move(HcfClient c,Vec3 goal) throws Exception {
        long end=System.nanoTime()+20_000_000_000L,frame=0;
        while(c.self().body().pose().feet().distance(goal)>.25 && System.nanoTime()<end) {
            Vec3 p=c.self().body().pose().feet();double yaw=Math.toDegrees(Math.atan2(goal.z()-p.z(),goal.x()-p.x()));
            c.input(++frame,Math.min(1,p.distance(goal)/.215),0,yaw,0,false,false,false,false,false,0);Thread.sleep(50);
        }
        c.input(++frame,0,0,0,0,false,false,false,false,false,0);check(c.self().body().pose().feet().distance(goal)<.5,"Movement reaches clear-lane waypoint");
    }
    private static HcfClient lateClient(HcfServer server,String pin,char[] password) throws Exception {
        Thread.sleep(250);return new HcfClient("127.0.0.1",server.port(),pin,"goldrogue",password,true,1);
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=4 || !args[0].equals("--display") || !args[1].equals(System.getenv("DISPLAY")))throw new IllegalArgumentException("--display inherited DISPLAY evidence ignored-scratch");
        Path evidence=Path.of(args[2]),scratch=Path.of(args[3]);Files.createDirectories(evidence);Files.createDirectories(scratch);
        char[] bardSecret=secret(),allySecret=secret(),archerSecret=secret(),focusSecret=secret(),reserveSecret=secret();var failure=new AtomicReference<Throwable>();
        var tls=SecureTransport.server(scratch.resolve("synthetic-tls"));
        try(var server=new HcfServer(tls.context(),InetAddress.getLoopbackAddress(),0,scratch.resolve("server"),HcfRules.initial(),System::currentTimeMillis,HcfSession.Phase.ACTIVE,false);
            var ally=new HcfClient("127.0.0.1",server.port(),tls.fingerprint(),"bluefighter",allySecret,true,0)) {
            Thread.sleep(250);
            try(var archer=new HcfClient("127.0.0.1",server.port(),tls.fingerprint(),"goldarcher",archerSecret,true,1)) {
                Thread.sleep(250);
                try(var focus=new HcfClient("127.0.0.1",server.port(),tls.fingerprint(),"goldfighter",focusSecret,true,1);
                    var reserve=lateClient(server,tls.fingerprint(),reserveSecret)) {
                    var heartbeat=new java.util.concurrent.atomic.AtomicBoolean(true);
                    Thread pulse=new Thread(()->{while(heartbeat.get())try{for(var client:List.of(ally,archer,focus,reserve))if(client.connected())client.input(0,0,0,client.self()==null?0:client.self().yaw(),0,false,false,false,false,false,client.self()==null?0:client.self().body().selectedSlot());Thread.sleep(2000);}catch(Exception ignored){}});
                    pulse.setDaemon(true);pulse.start();
                    Thread driver=new Thread(()->{
                        String window="";
                        try {
                            await(()->Arrays.stream(Window.getWindows()).anyMatch(w->w.isShowing() && w instanceof JDialog),15000,"HCF sign-in dialog via normal Main entry");
                            SwingUtilities.invokeAndWait(()->{for(Window w:Window.getWindows())if(w.isShowing() && w instanceof JDialog){fill(w,bardSecret);if(w instanceof JDialog d && d.getRootPane().getDefaultButton()!=null)d.getRootPane().getDefaultButton().doClick();}});
                            await(()->member(server,"bluebard")!=null && member(server,"bluebard").connected(),10000,"Production signed-in account");
                            Thread.sleep(3000);window=command("xdotool","search","--name","^Voxel One \\| HCF factions$").lines().findFirst().orElseThrow();
                            command("xdotool","windowfocus","--sync",window);
                            key(window,"e");key(window,"g");key(window,"b");
                            await(()->member(server,"bluebard").active()==HcfRole.BARD,8000,"Armor warmup");
                            archer.operation(HcfCommand.Op.CLAIM,0);reserve.operation(HcfCommand.Op.EQUIP,HcfRole.ROGUE.ordinal());Thread.sleep(250);
                            archer.operation(HcfCommand.Op.EQUIP,HcfRole.ARCHER.ordinal());Thread.sleep(200);archer.operation(HcfCommand.Op.DEPLOY,0);ally.operation(HcfCommand.Op.DEPLOY,0);focus.operation(HcfCommand.Op.DEPLOY,0);key(window,"r");
                            await(()->member(server,"bluebard").deployed() && ally.self().deployed() && archer.self().deployed() && focus.self().deployed(),4000,"Four opted-in participants on two teams");
                            var movers=new ArrayList<java.util.concurrent.CompletableFuture<Void>>();
                            HcfClient[] moving={ally,archer,focus};Vec3[] goals={new Vec3(-1,1,1),new Vec3(2,1,1),new Vec3(6,1,0)};
                            for(int i=0;i<3;i++){final int side=i;movers.add(java.util.concurrent.CompletableFuture.runAsync(()->{try{move(moving[side],goals[side]);}catch(Exception e){throw new RuntimeException(e);}}));}
                            command("xdotool","keydown","--window",window,"w");
                            await(()->member(server,"bluebard").body().pose().feet().z()>-2.3,12000,"Production W moves to KOTH");command("xdotool","keyup","--window",window,"w");
                            java.util.concurrent.CompletableFuture.allOf(movers.toArray(java.util.concurrent.CompletableFuture[]::new)).get(30,java.util.concurrent.TimeUnit.SECONDS);
                            await(()->server.state().contested(),3000,"Opposing teams contest KOTH");check(server.state().captureTicks()==0,"Contest resets capture");
                            key(window,"F10");key(window,"j");
                            await(()->member(server,"bluefighter").auraUntil()>server.state().tick(),3000,"Bard reaches ally");check(member(server,"goldarcher").auraUntil()==0,"Aura excludes enemy");check(member(server,"bluebard").energy()>=70 && member(server,"bluebard").energy()<73,"Bard spends thirty energy once");
                            capture(window,evidence.resolve("hcf-team-aura-koth.png"));
                            archer.input(100,0,0,180,0,false,false,false,true,false,6);Thread.sleep(1100);archer.input(101,0,0,180,0,false,false,false,false,true,6);
                            await(()->member(server,"bluefighter").markedBy()==1,3000,"Real arrow marks enemy for Gold focus");
                            // Reach melee range from the side so the arrow path remains unobstructed.
                            move(focus,new Vec3(-1,1,-1));Thread.sleep(550);double before=member(server,"bluefighter").body().health();
                            Vec3 fp=focus.self().body().pose().feet(),tp=member(server,"bluefighter").body().pose().feet();
                            focus.input(200,0,0,Math.toDegrees(Math.atan2(tp.z()-fp.z(),tp.x()-fp.x())),0,false,false,true,false,false,0);
                            await(()->member(server,"bluefighter").body().health()<before-2.9,2000,"Gold team focus amplifies melee");
                            double focusDamage=before-member(server,"bluefighter").body().health();check(Math.abs(focusDamage-3)<.01,"Marked Fighter damage is three");
                            // Clear the friendly body out of the next arrow ray, retaining its combat tag.
                            move(focus,new Vec3(6,1,-4));
                            // Hit the production player, then test H through its real keyboard path.
                            Vec3 ap=archer.self().body().pose().feet(),bp=member(server,"bluebard").body().pose().feet();double yaw=Math.toDegrees(Math.atan2(bp.z()-ap.z(),bp.x()-ap.x()));
                            archer.input(300,0,0,yaw,0,false,false,false,true,false,6);Thread.sleep(1100);archer.input(301,0,0,yaw,0,false,false,false,false,true,6);
                            await(()->member(server,"bluebard").tagUntil()>server.state().tick(),3000,"Production player combat tagged");key(window,"h");check(member(server,"bluebard").deployed(),"Tagged H cannot escape to spawn");
                            capture(window,evidence.resolve("hcf-tagged-home-rejected.png"));
                            focus.interruptConnection();await(()->member(server,"goldfighter").deathbanUntil()>server.state().now(),3000,"Combat disconnect commits defeat");Thread.sleep(250);focus.reconnect();await(()->focus.self()!=null,3000,"Account reconnect");
                            check(focus.self().deathbanUntil()>focus.state().now() && !focus.self().deployed(),"Reconnect retains deathban");check(server.state().teams().get(1).dtr()==2,"Combat logout loses one DTR");
                            capture(window,evidence.resolve("hcf-logout-deathban.png"));key(window,"F10");
                            archer.interruptConnection();await(()->server.state().teams().get(1).dtr()==1,3000,"Archer combat logout DTR loss");
                            reserve.operation(HcfCommand.Op.DEPLOY,0);await(()->reserve.self().deployed(),3000,"Rogue deployment");
                            Vec3 rear=member(server,"bluefighter").body().pose().feet().add(new Vec3(-2,0,0));move(reserve,rear);
                            double rogueBefore=member(server,"bluefighter").body().health();reserve.input(400,0,0,0,0,false,false,true,false,false,0);
                            await(()->member(server,"goldrogue").abilityReadyAt()>server.state().tick(),3000,"Real Rogue rear hit spends cooldown");
                            check(rogueBefore-member(server,"bluefighter").body().health()>3.7,"Rogue amplified hit survives possible food regen");
                            capture(window,evidence.resolve("hcf-rogue-backstab.png"));reserve.interruptConnection();
                            await(()->server.state().teams().get(1).dtr()==0 && server.state().teams().get(1).raidable(),3000,"Exactly zero DTR opens claim");
                            await(()->server.state().teams().get(0).score()>0,14000,"Blue takeover completes full KOTH capture");
                            capture(window,evidence.resolve("hcf-koth-takeover.png"));
                            await(()->member(server,"bluebard").tagUntil()<=server.state().tick(),17000,"Combat tag expires before raid entry");
                            command("xdotool","keydown","--window",window,"w");await(()->member(server,"bluebard").body().pose().feet().z()>8,6000,"Production W reaches claim approach");command("xdotool","keyup","--window",window,"w");
                            command("xdotool","keydown","--window",window,"a");await(()->member(server,"bluebard").body().pose().feet().x()>21,10000,"Production A enters zero-DTR claim");command("xdotool","keyup","--window",window,"a");
                            int stash=server.state().teams().get(0).stash();key(window,"t");check(server.state().teams().get(0).stash()>=stash+1,"Production raid transfers supplies");
                            capture(window,evidence.resolve("hcf-raid-zero-dtr.png"));key(window,"h");check(!member(server,"bluebard").deployed(),"Untagged home works");
                            int supplies=server.state().teams().get(0).stash();long supplyTick=server.state().tick();key(window,"b");
                            long restocks=server.state().tick()/HcfRules.initial().restockTicks()-supplyTick/HcfRules.initial().restockTicks();
                            check(server.state().teams().get(0).stash()==supplies-1+restocks,"Economy-free production resupply accounts for scheduled restock");
                            key(window,"l");await(()->!member(server,"bluebard").connected(),3000,"Safe logout");key(window,"c");await(()->member(server,"bluebard").connected(),4000,"Production C sign-in after safe logout");
                            check(member(server,"bluebard").deathbanUntil()==0,"Safe logout avoids defeat");
                            Files.writeString(scratch.resolve("receipt.json"),"{\"normalMainEntry\":true,\"realTlsAccounts\":5,\"opposingTeams\":2,\"productionKeyboardAndF10\":true,\"allyOnlyAura\":true,\"energySpend\":30,\"contestedReset\":true,\"archerMark\":true,\"focusDamage\":"+focusDamage+",\"taggedHomeRejected\":true,\"combatLogoutDefeat\":true,\"reconnectDeathban\":true,\"goldDtr\":0,\"rogueBackstab\":true,\"kothTakeoverScore\":true,\"zeroDtrRaid\":true,\"economyFreeResupply\":true,\"safeLogoutReconnect\":true}\n");
                            check(server.healthy(),"Server remains healthy");
                            command("xdotool","keydown","--window",window,"Escape");Thread.sleep(250);command("xdotool","keyup","Escape");
                        }catch(Throwable e){failure.set(e);
                            try{Files.writeString(scratch.resolve("failure-summary.txt"),e.getClass().getSimpleName()+": "+e.getMessage()+"\n"+server.state().members().stream().map(m->m.id()+" "+m.body().pose().feet()+" connected="+m.connected()+" deployed="+m.deployed()+" shield="+(m.spawnUntil()-server.state().tick())).reduce((a,b)->a+"\n"+b).orElse(""));}catch(Exception ignored){}
                            if(!window.isEmpty())try{key(window,"Escape");}catch(Throwable ignored){}
                        }
                    },"hcf-production-playtest");driver.setDaemon(true);driver.start();
                    try{Main.main(new String[]{"--game","hcf","--server","127.0.0.1","--port",Integer.toString(server.port()),"--fingerprint",tls.fingerprint()});}
                    finally{heartbeat.set(false);}
                    driver.join(5000);if(failure.get()!=null)throw new AssertionError("HCF native workflow",failure.get());check(Files.exists(scratch.resolve("receipt.json")),"Workflow receipt");
                    Path recordings=Controls.directory().resolve("hcf/recordings");try(var files=Files.list(recordings)) {
                        Path clip=files.filter(p->p.toString().endsWith(".mp4")).max(Comparator.comparing(Path::toString)).orElseThrow();check(Files.size(clip)<6_000_000,"F10 clip under six MB");Files.copy(clip,evidence.resolve("hcf-team-playtest.mp4"),StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }finally{for(char[] p:List.of(bardSecret,allySecret,archerSecret,focusSecret,reserveSecret))Arrays.fill(p,'\0');}
        System.out.println("PASS: normal Main; five TLS clients; ally-only Bard spend; contested KOTH; Archer team focus; tagged home denied; combat logout/rejoin deathban");
    }
}
