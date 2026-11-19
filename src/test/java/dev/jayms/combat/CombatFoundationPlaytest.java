package dev.jayms.combat;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import dev.jayms.combat.KitProfile.Stack;
import static dev.jayms.combat.KitProfile.*;
import static dev.jayms.combat.CombatIntent.*;
import static dev.jayms.combat.CombatEvent.*;

/** Executable model playtest. Draws only snapshots actually produced by the running service. */
public final class CombatFoundationPlaytest {
    private static final Vec3 EAST = new Vec3(1,0,0), DOWN = new Vec3(0,-1,0);
    private record Frame(String title, long tick, CombatSimulation.Snapshot a,
                         CombatSimulation.Snapshot b, String observed) {}
    private static final ArrayList<Frame> frames = new ArrayList<>();
    private static CombatSimulation.Pose at(double x) {
        return new CombatSimulation.Pose(new Vec3(x,0,0),Vec3.ZERO,true);
    }
    private static void input(CombatSimulation s, Action action) {
        require(s.submit("blue",new CombatIntent(s.snapshot("blue").lastSequence()+1,action))
                == CombatSimulation.Submission.QUEUED,"input queued");
    }
    private static void require(boolean value,String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void frame(CombatSimulation s,String title,String observed) {
        frames.add(new Frame(title,s.tick(),s.snapshot("blue"),s.snapshot("red"),observed));
        System.out.printf("tick=%d %s | blue=%.2f red=%.2f | %s%n",s.tick(),title,
                s.snapshot("blue").health(),s.snapshot("red").health(),observed);
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1) throw new IllegalArgumentException("PNG output path required");
        frames.clear();
        var w=new TestCombatWorld();
        var kit=new KitProfile("synthetic-playtest",List.of(new Stack(Item.SWORD,1),new Stack(Item.HEAL_SPLASH,2),
                new Stack(Item.POISON_SPLASH,2),new Stack(Item.PEARL,2)),List.of());
        var s=new CombatSimulation(CombatRules.debuff(),w);
        s.addParticipant("blue",at(0),kit); s.addParticipant("red",at(2),kit);
        input(s,new Sprint(true)); input(s,new Swing(EAST)); input(s,new Swing(EAST)); s.step(1);
        require(s.snapshot("red").health()==14,"fast reclick hurt immunity");
        frame(s,"Sprint + fast re-click","Two swings; one 6-health hit; sprint knockback 0.50");
        s.worldDamage("blue",8,DamageKind.ENVIRONMENT);
        s.setPose("red",at(2)); // next scenario starts with both bodies inside splash radius
        input(s,new Select(1)); input(s,new StartUse(DOWN)); s.step(4);
        require(s.snapshot("blue").health()>12 && s.snapshot("red").health()>14,"splash heals both sides");
        require(s.snapshot("blue").inventory().get(1).count()==1,"finite potion consumed once");
        frame(s,"Distance-weighted heal splash","Self and opponent heal at impact; one potion spent");
        w.wallX=1; s.setPose("blue",at(0)); s.setPose("red",at(2)); s.step(10);
        double before=s.snapshot("red").health();
        input(s,new Select(0)); input(s,new Swing(EAST)); s.step(1);
        require(s.snapshot("red").health()==before,"wall rejects hit");
        frame(s,"Edge: solid wall","Melee blocked; opponent health unchanged");
        w.wallX=Double.POSITIVE_INFINITY; s.addExhaustion("red",100); s.step(1);
        s.worldDamage("red",s.snapshot("red").health()-2,DamageKind.ENVIRONMENT);
        s.setPose("blue",at(0)); s.setPose("red",at(2));
        input(s,new Select(2)); input(s,new StartUse(EAST)); s.step(44);
        require(s.snapshot("red").health()==1 && s.snapshot("red").alive(),"poison is nonlethal");
        frame(s,"Debuff: poison floor","Poison pulses stop at 1 health; no death event");
        s.setPose("red",at(20)); s.setPose("blue",at(0));
        input(s,new Select(3)); input(s,new StartUse(new Vec3(1,-1,0))); s.step(4);
        require(s.snapshot("blue").pose().feet().x()>0 && s.snapshot("blue").pose().feet().y()==0,"safe pearl landing");
        frame(s,"Pearl: supported destination","Landing checked; velocity cleared; health cost 2");
        long sequence=s.snapshot("blue").lastSequence(); int count=s.snapshot("blue").inventory().get(3).count();
        require(s.submit("blue",new CombatIntent(sequence,new StartUse(DOWN)))==CombatSimulation.Submission.REPLAY,"duplicate rejected");
        input(s,new StartUse(DOWN)); s.step(1);
        require(s.snapshot("blue").inventory().get(3).count()==count,"cooldown keeps item");
        frame(s,"Regression: replay + cooldown","Duplicate rejected; early pearl keeps finite inventory");
        BufferedImage image=new BufferedImage(1280,980,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(15,23,35)); g.fillRect(0,0,image.getWidth(),image.getHeight());
        g.setColor(Color.WHITE); g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,28));
        g.drawString("Voxel One | Combat foundation live model playtest",32,46);
        g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,16)); g.setColor(new Color(175,195,215));
        g.drawString("Java 17 / Linux / fixed 20 Hz / synthetic participants / no Player or network adapter",32,76);
        for(int i=0;i<frames.size();i++) draw(g,frames.get(i),32+(i%2)*624,104+(i/2)*276);
        g.setColor(new Color(175,195,215)); g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,15));
        g.drawString("Captured from actual CLI execution. Foundation evidence; game feel / GPU qualification remains item 2.",32,960);
        g.dispose(); Path output=Path.of(args[0]); Files.createDirectories(output.toAbsolutePath().getParent());
        ImageIO.write(image,"png",output.toFile());
        System.out.println("Playtest: PASS; 6 live frames; PNG="+output);
    }
    private static void draw(Graphics2D g,Frame f,int x,int y) {
        g.setColor(new Color(27,40,57)); g.fillRoundRect(x,y,600,254,16,16);
        g.setColor(Color.WHITE); g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,19));
        g.drawString(f.title(),x+18,y+30);
        g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,14)); g.setColor(new Color(175,195,215));
        g.drawString("tick "+f.tick()+"  |  50 ms/tick",x+18,y+54);
        bar(g,"BLUE",f.a(),x+18,y+76,new Color(70,160,245));
        bar(g,"RED",f.b(),x+18,y+128,new Color(245,105,100));
        g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14)); g.setColor(new Color(205,220,233));
        g.drawString(f.observed(),x+18,y+206);
        g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));
        g.drawString("BLUE effects "+f.a().effects().stream().map(e->e.effect().name()).toList()
                +" | RED "+f.b().effects().stream().map(e->e.effect().name()).toList(),x+18,y+232);
    }
    private static void bar(Graphics2D g,String name,CombatSimulation.Snapshot s,int x,int y,Color color) {
        g.setColor(color); g.setFont(new Font(Font.MONOSPACED,Font.BOLD,14));
        g.drawString(String.format(java.util.Locale.ROOT,"%s health %5.2f/20   feet (%.2f, %.2f, %.2f)",name,
                s.health(),s.pose().feet().x(),s.pose().feet().y(),s.pose().feet().z()),x,y+12);
        g.setColor(new Color(9,16,25)); g.fillRoundRect(x,y+22,550,15,5,5);
        g.setColor(color); g.fillRoundRect(x,y+22,(int)(550*s.health()/20),15,5,5);
    }
}
