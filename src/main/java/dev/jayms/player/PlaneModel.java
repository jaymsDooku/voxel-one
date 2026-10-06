package dev.jayms.player;

import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.Aviation;
import org.joml.Matrix4f;

/** Passenger jet assembled from stepped voxel boxes: cabin, swept wings, twin engines and tail. */
public final class PlaneModel implements AutoCloseable {
    private final Mesh cube;
    public PlaneModel() { var c=new Chunk();c.setBlock(0,0,0,Blocks.STONE);c.generateMesh();cube=c.getMesh(); }
    private void box(ShaderProgram s, Matrix4f root, float x,float y,float z,float w,float h,float d, int color) {
        s.setVector3("uColor", ((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f);
        s.setMatrix4("uModel",new Matrix4f(root).translate(x,y,z).scale(w,h,d).translate(-.5f,0,-.5f));cube.render();
    }
    public void render(Aviation.Plane p, ShaderProgram s) {
        s.setInt("uVertexColor",0);s.setInt("uInstanced",0);
        var r=new Matrix4f().translate(p.x(),p.y(),p.z()).rotateY((float)Math.toRadians(-p.yaw())).rotateZ((float)Math.toRadians(p.pitch()));
        box(s,r,0,1,0,12,2,2,0xe9edf2);
        box(s,r,6.25f,1.25f,0,1.5f,1.5f,1.5f,0xe9edf2);
        box(s,r,7.25f,1.5f,0,.5f,1,1,0xd1d9e2);
        box(s,r,5.5f,2.2f,0,1.8f,.6f,1.6f,0x28566f);
        box(s,r,-6.25f,1.5f,0,1.5f,1,1,0xe9edf2);
        for(int side:new int[]{-1,1}) {
            for(int step=1;step<=4;step++) box(s,r,1-step*.7f,1.5f,side*(1+step),4-step*.5f,.4f,2,0xcbd5e1);
            box(s,r,-5.5f,2,side*1.8f,2.5f,.3f,3,0x2563bb);
            box(s,r,.5f,.7f,side*3,2.4f,.9f,1.2f,0xd3dbe4);
            box(s,r,1.8f,.9f,side*3,.15f,.5f,.8f,0x263343);
            for(int window=-4;window<=3;window++) box(s,r,window,2.1f,side*1.015f,.35f,.45f,.08f,0x295c86);
        }
        box(s,r,-5.5f,2.5f,0,2.2f,2.5f,.35f,0x2563bb);
        box(s,r,-6.1f,4.5f,0,1,.8f,.35f,0x2563bb);
        if(!p.airborne()) for(float x:new float[]{-3,4}) for(int side:new int[]{-1,1})
            box(s,r,x,0,side*.8f,.8f,1,.5f,0x1c2732);
    }
    public void close() { cube.close(); }
}
