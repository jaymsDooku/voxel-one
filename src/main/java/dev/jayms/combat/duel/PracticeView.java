package dev.jayms.combat.duel;

import dev.jayms.*;
import dev.jayms.combat.*;
import dev.jayms.combat.arena.BasaltCourtyard;
import dev.jayms.ui.Overlay;
import org.joml.Matrix4f;
import java.nio.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.lwjgl.system.MemoryUtil;
import java.util.*;
import static org.lwjgl.opengl.GL33.*;

/** Real engine meshes and renderer. Both production and two-client playtests use this view. */
public final class PracticeView implements AutoCloseable {
    private record Draw(ChunkPos pos,Mesh mesh) {}
    private final BasaltCourtyard.Fixture arena=BasaltCourtyard.generate();
    private final List<Draw> draws=new ArrayList<>();
    // A bounded diffuse pass keeps combat legible and input responsive. City/Sandbox's
    // atmosphere/GI pipeline remains unchanged. Geometry still uses the real engine mesher.
    private final ShaderProgram shader=new ShaderProgram("shaders/practice.vert","shaders/practice.frag");
    private final Overlay overlay=new Overlay();
    private final Mesh blue=cube(.15f,.65f,.95f),gold=cube(1,.65f,.15f),green=cube(.15f,1,.45f),red=cube(1,.15f,.25f);
    public final Camera camera=new Camera();
    private long lastEvent,epoch=-1,feedbackUntil;
    private String feedback="";
    public PracticeView() {
        for(var e:arena.world().getLoadedChunks().entrySet()) if(!e.getValue().isEmpty())
            draws.add(new Draw(e.getKey(),new Mesh(MeshDataGenerator.generate(new ChunkSnapshot(arena.world(),e.getKey(),e.getValue())))));
    }
    private static Mesh cube(float r,float g,float b) {
        float[][] p={{0,0,0},{1,0,0},{1,1,0},{0,1,0},{0,0,1},{1,0,1},{1,1,1},{0,1,1}};
        int[][] faces={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{0,1,5,4},{3,7,6,2}};
        float[][] normals={{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},{0,-1,0},{0,1,0}};
        float[] verts=new float[24*9];int[] indices=new int[36];
        for(int f=0;f<6;f++) {
            for(int i=0;i<4;i++) {int n=(f*4+i)*9;System.arraycopy(p[faces[f][i]],0,verts,n,3);System.arraycopy(normals[f],0,verts,n+3,3);verts[n+6]=r;verts[n+7]=g;verts[n+8]=b;}
            int n=f*6,k=f*4;indices[n]=k;indices[n+1]=k+1;indices[n+2]=k+2;indices[n+3]=k;indices[n+4]=k+2;indices[n+5]=k+3;
        }
        return new Mesh(new MeshData(verts,indices));
    }
    private void object(Mesh mesh,double x,double y,double z,float w,float h,float d) {
        shader.setMatrix4("uModel",new Matrix4f().translation((float)x,(float)y,(float)z).scale(w,h,d));mesh.render();
    }
    public void render(DuelClient client,int width,int height,boolean inventory,int firstSlot,int secondSlot,String message) {
        var state=client.state();if(state==null) {
            glBindFramebuffer(GL_FRAMEBUFFER,0);glViewport(0,0,width,height);glClearColor(.04f,.06f,.09f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
            overlay.begin(width,height);text("VOXEL ONE / PRACTICE",24,30,2);
            text(client.connected()?"Waiting for authoritative practice state...":"Connection lost. C reconnects; Esc leaves.",24,70,1.5f);
            if(!message.isEmpty())text(message,24,110,1.3f);overlay.end();return;
        }
        int side=client.side();var self=state.fighters().get(Math.max(0,side));var feet=self.body().pose().feet();
        if(epoch!=state.epoch()) {epoch=state.epoch();lastEvent=0;feedback="";camera.setYaw(side==1?-90:90);camera.setPitch(0);}
        if(side>=0) camera.position().set((float)feet.x(),(float)feet.y()+1.62f,(float)feet.z());
        else {camera.position().set(0,12.62f,-44);camera.setYaw(90);camera.setPitch(-10);}
        var projection=new Matrix4f().perspective((float)Math.toRadians(75),width/(float)height,.08f,200);
        glBindFramebuffer(GL_FRAMEBUFFER,0);glViewport(0,0,width,height);glClearColor(.18f,.25f,.32f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);glEnable(GL_CULL_FACE);shader.bind();shader.setMatrix4("uProjection",projection);shader.setMatrix4("uView",camera.createViewMatrix());
        for(var d:draws){shader.setMatrix4("uModel",new Matrix4f().translation(d.pos.chunkX()*16,d.pos.chunkY()*16,d.pos.chunkZ()*16));d.mesh.render();}
        for(int i=0;i<2;i++) if(i!=side) {
            var f=state.fighters().get(i);var p=f.body().pose().feet();Mesh body=i==0?blue:gold;
            object(body,p.x()-.3,p.y()+.65,p.z()-.3,.6f,.7f,.6f);
            object(body,p.x()-.24,p.y()+1.35,p.z()-.24,.48f,.45f,.48f);
            object(body,p.x()-.25,p.y(),p.z()-.22,.2f,.65f,.44f);object(body,p.x()+.05,p.y(),p.z()-.22,.2f,.65f,.44f);
            var dir=DuelSession.direction(f.yaw(),0);object(gold,p.x()+dir.x()*.5-.04,p.y()+.8,p.z()+dir.z()*.5-.04,.08f,.7f,.08f);
        }
        for(var p:state.projectiles()) {
            var v=p.position();Mesh mesh=p.item()==KitProfile.Item.HEAL_SPLASH?green:p.item()==KitProfile.Item.PEARL?blue:red;
            object(mesh,v.x()-.1,v.y()-.1,v.z()-.1,.2f,.2f,.2f);
        }
        for(var e:state.events()) {
            if(e.id()>lastEvent) {
                if(e.type()==CombatEvent.Type.HIT || e.type()==CombatEvent.Type.HEAL || e.type()==CombatEvent.Type.PEARL_REJECT || e.type()==CombatEvent.Type.EFFECT_START) {
                    feedback=e.type()+" "+(e.target()==null?"":e.target())+" "+e.detail();feedbackUntil=System.nanoTime()+800_000_000L;
                }
                lastEvent=Math.max(lastEvent,e.id());
            }
            if(e.type()==CombatEvent.Type.PROJECTILE_IMPACT || e.type()==CombatEvent.Type.HIT || e.type()==CombatEvent.Type.HEAL) {
                var p=e.position();object(e.type()==CombatEvent.Type.HEAL?green:red,p.x()-.25,p.y()+.1,p.z()-.25,.5f,.12f,.5f);
            }
        }
        hud(client,state,width,height,inventory,firstSlot,secondSlot,message);
    }
    private void text(String s,float x,float y,float size){overlay.text(s,x,y,size,.9f,.94f,1,1);}
    private static String number(double n){return String.format(Locale.ROOT,"%.1f",n);}
    public static String item(KitProfile.Item item) {return switch(item){
        case EMPTY->"--";case SWORD->"Sword";case HEAL_SPLASH->"Heal";case PEARL->"Pearl";case SPEED_DRINK->"Speed";
        case FIRE_RESIST_DRINK->"Fire";case FOOD->"Food";case POISON_SPLASH->"Poison";case SLOWNESS_SPLASH->"Slow";default->item.name();};}
    private void hud(DuelClient client,DuelSession.State state,int width,int height,boolean inventory,int first,int second,String message) {
        overlay.begin(width,height);overlay.rectangle(12,12,Math.min(width-24,810),130,.025f,.045f,.07f,.9f);
        var self=state.fighters().get(Math.max(0,client.side()));var b=self.body();
        text("VOXEL ONE  /  PRACTICE  /  BASALT COURTYARD",24,24,1.6f);
        text(state.profile()+"  "+state.phase()+"  "+(state.remainingTicks()/20)+"s  |  You: "+(client.side()<0?"Spectator":self.id()),24,48,1.4f);
        text("Health "+number(b.health())+" / 20   Food "+number(b.hunger())+"   Pearl "+Math.max(0,(b.pearlReadyAt()-state.combatTick()+19)/20)+"s",24,70,1.5f);
        text("Gear "+b.armor().stream().map(a->a.slot()+":"+a.durability()).reduce((a,c)->a+" "+c).orElse("none"),24,92,1.1f);
        text("Effects "+b.effects().stream().map(e->e.effect()+" "+Math.max(0,(e.expiresAt()-state.combatTick())/20)+"s").reduce((a,c)->a+", "+c).orElse("none"),24,112,1.1f);
        int other=client.side()==1?0:1;var target=state.fighters().get(other);
        text("Target "+target.id()+"  "+number(target.body().health())+" hp  "+number(b.pose().feet().distance(target.body().pose().feet()))+" blocks",24,152,1.4f);
        if(System.nanoTime()<feedbackUntil) text(feedback,width/2f-150,height/2f+28,1.5f);
        overlay.line(width/2f-6,height/2f,width/2f+6,height/2f,2,1,1,1,1);overlay.line(width/2f,height/2f-6,width/2f,height/2f+6,2,1,1,1,1);
        float cell=Math.min(90,(width-24)/9f),start=(width-cell*9)/2;
        for(int i=0;i<9;i++) {
            var stack=b.inventory().get(i);float x=start+i*cell;
            overlay.rectangle(x,height-82,cell-3,51,i==b.selectedSlot()?.25f:.04f,.12f,.18f,.95f);
            text((i+1)+" "+item(stack.item()),x+5,height-72,1.15f);text("x"+stack.count(),x+5,height-52,1.1f);
        }
        text("WASD move | Space jump | Shift sprint | Left attack | Right use | 1-9 / wheel | E kit | F refill",18,height-22,1.05f);
        if(state.phase()!=DuelSession.Phase.LIVE) {
            overlay.rectangle(width/2f-310,height/2f-82,620,150,.025f,.045f,.07f,.94f);
            text(state.phase()==DuelSession.Phase.RESULT?state.result():state.phase()==DuelSession.Phase.QUEUE?"Waiting for another player":state.phase()==DuelSession.Phase.WARMUP?"Warmup - starting in "+((state.remainingTicks()+19)/20)+"s":"Ready area - edit your kit, then press R",width/2f-290,height/2f-64,1.6f);
            text("R ready / rematch   N new queue   E kit   Esc leave   F10 record",width/2f-290,height/2f-38,1.15f);
            int y=0;for(var f:state.fighters()) {var score=f.score();text(f.id()+" "+(f.connected()?f.ready()?"READY":"connected":"reconnecting")+" | hits "+score.hits()+" combo "+score.longestCombo()+" pots "+score.potsUsed()+" left "+score.potsRemaining()+" hp "+number(score.health()),width/2f-290,height/2f-8+y,1.1f);y+=22;}
        }
        if(inventory) {
            float left=width/2f-300,top=height/2f-160;
            overlay.rectangle(left-12,top-38,624,248,.02f,.04f,.08f,.99f);
            text("NEXT ROUND KIT: arrows select; Enter marks / swaps; E closes.",left,top-26,1.1f);
            for(int i=0;i<36;i++) {var stack=self.nextKit().get(i);float x=left+(i%9)*67,y=top+(i/9)*48;
                overlay.rectangle(x,y,64,44,i==second?.35f:.08f,i==first?.4f:.15f,.25f,1);
                text((i+1)+" "+item(stack.item()),x+3,y+7,1);text("x"+stack.count(),x+3,y+25,1);
            }
        }
        if(!client.connected()) text("Disconnected. Press C to reconnect; 10 second grace.",24,height-112,1.5f);
        if(!message.isEmpty()) text(message,24,height-138,1.3f);
        if(!client.notice().isEmpty()) text(client.notice(),24,height-158,1.3f);
        overlay.end();
    }
    public static void capture(Path path,int width,int height) throws Exception {
        ByteBuffer rgb=MemoryUtil.memAlloc(width*height*3);
        try {glPixelStorei(GL_PACK_ALIGNMENT,1);glReadPixels(0,0,width,height,GL_RGB,GL_UNSIGNED_BYTE,rgb);
            var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<height;y++)for(int x=0;x<width;x++){int i=(y*width+x)*3;image.setRGB(x,height-y-1,(rgb.get(i)&255)<<16|(rgb.get(i+1)&255)<<8|(rgb.get(i+2)&255));}
            ImageIO.write(image,"png",path.toFile());
        }finally{MemoryUtil.memFree(rgb);}
    }
    @Override public void close() throws Exception {
        for(var d:draws)d.mesh.close();blue.close();gold.close();green.close();red.close();overlay.close();shader.close();arena.close();
    }
}
