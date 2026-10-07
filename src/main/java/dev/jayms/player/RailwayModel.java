package dev.jayms.player;

import dev.jayms.*;
import dev.jayms.net.Blocks;
import dev.jayms.net.city.*;
import org.joml.Matrix4f;
import java.util.*;

/** Block-built rails and animated steam engines. The simulation owns all movement and passengers. */
public final class RailwayModel implements AutoCloseable {
    private final Mesh cube;
    public RailwayModel() { var c=new Chunk();c.setBlock(0,0,0,Blocks.STONE);c.generateMesh();cube=c.getMesh(); }
    private void box(ShaderProgram s,Matrix4f root,float x,float y,float z,float w,float h,float d) {
        s.setMatrix4("uModel",new Matrix4f(root).translate(x,y,z).scale(w,h,d).translate(-.5f,0,-.5f));cube.render();
    }
    private void color(ShaderProgram s,float r,float g,float b) {s.setVector3("uColor",r,g,b);}
    public void render(CityFrame city,ShaderProgram s) {
        s.setInt("uVertexColor",0);s.setInt("uInstanced",0);
        var cells=new HashSet<Polygon.Cell>();for(var t:city.railway().tracks())cells.add(new Polygon.Cell(t.x(),t.z()));
        for(var t:city.railway().tracks()) {
            boolean east=cells.contains(new Polygon.Cell(t.x()+1,t.z())),west=cells.contains(new Polygon.Cell(t.x()-1,t.z()));
            boolean north=cells.contains(new Polygon.Cell(t.x(),t.z()-1)),south=cells.contains(new Polygon.Cell(t.x(),t.z()+1));
            var root=new Matrix4f().translation(t.x()+.5f,t.y()+1.01f,t.z()+.5f);
            if(east||west||!north&&!south)track(s,root,true);
            if(north||south)track(s,root,false);
        }
        for(var t:city.railway().trains()) {
            if(t.stop()==t.depot()&&t.dwell()>0)continue;
            var root=new Matrix4f().translation(t.x(),t.y()+.16f,t.z()).rotateY((float)Math.toRadians(t.yaw()));
            color(s,.65f,.08f,.06f);box(s,root,0,.4f,0,1.3f,.25f,3.4f);
            color(s,.07f,.09f,.10f);
            // Stepped boiler: six courses retain the voxel silhouette.
            for(int i=0;i<6;i++){float width=i==0||i==5?.6f:i==1||i==4?1:1.2f;box(s,root,0,.9f+i*.16f,.7f,width,.16f,1.9f);}
            box(s,root,0,1.65f,1.25f,.35f,.8f,.35f);box(s,root,0,2.4f,1.25f,.55f,.16f,.55f);
            color(s,.82f,.64f,.24f);box(s,root,0,1.3f,1.69f,.62f,.3f,.08f);
            color(s,.07f,.09f,.10f);box(s,root,0,.7f,-1.05f,1.4f,1.6f,.12f);box(s,root,0,2.2f,-1.05f,1.6f,.15f,1.3f);
            for(int side:new int[]{-1,1}) {
                box(s,root,side*.65f,.7f,-1.05f,.12f,.65f,1.1f);
                for(float z:new float[]{-1.55f,-.55f})box(s,root,side*.65f,1.3f,z,.12f,.95f,.12f);
                color(s,.40f,.70f,.78f);box(s,root,side*.65f,1.4f,-1.05f,.06f,.55f,.65f);color(s,.07f,.09f,.10f);
                for(int axle=-1;axle<=1;axle++) {
                    var wheel=new Matrix4f(root).translate(side*.7f,.5f,axle*.85f).rotateX(t.phase()*2);
                    box(s,wheel,0,-.4f,0,.18f,.8f,.55f);box(s,wheel,0,-.27f,0,.18f,.55f,.8f);
                    color(s,.72f,.12f,.08f);box(s,wheel,side*.11f,-.06f,0,.06f,.12f,.65f);color(s,.07f,.09f,.10f);
                }
                color(s,.65f,.68f,.7f);box(s,root,side*.85f,.5f+(float)Math.sin(t.phase()*2)*.18f,0,.08f,.08f,2.1f);color(s,.07f,.09f,.10f);
            }
            // Coal tender and passenger coach.
            box(s,root,0,.6f,-2.6f,1.3f,.85f,1.4f);color(s,.025f,.025f,.03f);box(s,root,0,1.45f,-2.6f,1.1f,.18f,1.2f);
            color(s,.22f,.35f,.22f);box(s,root,0,.6f,-5.1f,1.5f,1.4f,2.9f);
            color(s,.12f,.14f,.14f);box(s,root,0,2,-5.1f,1.7f,.16f,3.1f);
            color(s,.48f,.72f,.8f);for(int side:new int[]{-1,1})for(float z:new float[]{-4.25f,-5.1f,-5.95f})box(s,root,side*.76f,1.25f,z,.05f,.5f,.5f);
            color(s,.05f,.05f,.06f);for(int side:new int[]{-1,1})for(float z:new float[]{-2.9f,-2.3f,-4.2f,-6})box(s,root,side*.7f,.25f,z,.2f,.5f,.5f);
            if(t.dwell()==0) {color(s,.8f,.82f,.82f);for(int i=0;i<3;i++){float rise=(t.phase()*.3f+i*.7f)%2;box(s,root,(float)Math.sin(t.phase()+i)*.15f,2.6f+rise,1.25f-rise*.35f,.3f+rise*.2f,.3f,.3f+rise*.2f);}}
        }
        s.setInt("uVertexColor",1);
    }
    private void track(ShaderProgram s,Matrix4f root,boolean horizontal) {
        var r=new Matrix4f(root);if(horizontal)r.rotateY((float)Math.PI/2);
        color(s,.32f,.2f,.12f);for(float z:new float[]{-.33f,0,.33f})box(s,r,0,0,z,1.6f,.06f,.13f);
        color(s,.6f,.64f,.68f);for(float x:new float[]{-.7f,.7f})box(s,r,x,.06f,0,.08f,.1f,1);
    }
    public void close(){cube.close();}
}
