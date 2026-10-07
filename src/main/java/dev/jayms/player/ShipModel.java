package dev.jayms.player;

import dev.jayms.*;
import dev.jayms.net.Blocks;
import dev.jayms.net.city.ShippingRoutes;
import org.joml.Matrix4f;

/** Stepped steel hull, stacked ISO cargo, aft bridge and funnel. */
public final class ShipModel implements AutoCloseable {
    private final Mesh cube;
    public ShipModel() { var c=new Chunk(); c.setBlock(0,0,0,Blocks.STONE); c.generateMesh(); cube=c.getMesh(); }
    private void box(ShaderProgram s, Matrix4f r, float x,float y,float z,float w,float h,float d,int color) {
        s.setVector3("uColor",((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f);
        s.setMatrix4("uModel",new Matrix4f(r).translate(x,y,z).scale(w,h,d).translate(-.5f,0,-.5f)); cube.render();
    }
    public void render(ShippingRoutes.Ship p,ShaderProgram s) {
        s.setInt("uVertexColor",0); s.setInt("uInstanced",0);
        var r=new Matrix4f().translation(p.x(),14.4f,p.z()).rotateY(p.yaw());
        // A pointed bow faces positive local Z. Narrow keel and red waterline.
        for(int z=-11;z<=11;z++) {
            float w=z>7?6-(z-7):z<-9?5:6;
            box(s,r,0,-.4f,z,w-.6f,.7f,1,0x973d35);
            box(s,r,0,.3f,z,w,1.5f,1,0x263645);
            box(s,r,0,1.8f,z,w,.25f,1,0x87969e);
        }
        int[] colors={0xb63c32,0x2c6984,0xd49b35,0x527b49,0xd9d7c9};
        for(int row=0;row<3;row++) for(int col=0;col<2;col++) for(int tier=0;tier<3;tier++) {
            float x=col==0?-1.45f:1.45f,z=-3+row*4;
            int color=colors[(row+col*2+tier)%colors.length];
            box(s,r,x,2.05f+tier*1.6f,z,2.65f,1.5f,3.7f,color);
            for(int rib=0;rib<7;rib++) for(int side:new int[]{-1,1})
                box(s,r,x+side*1.34f,2.1f+tier*1.6f,z-1.5f+rib*.5f,.07f,1.4f,.08f,0x354650);
        }
        // White aft accommodation block with wraparound bridge glazing.
        box(s,r,0,2,-8,5.3f,3,4,0xdde2df);
        box(s,r,0,5,-8,5.8f,.85f,3.5f,0x30576c);
        box(s,r,0,5.85f,-8,6,.25f,3.7f,0xe4e9e5);
        for(int side:new int[]{-1,1}) for(int deck=0;deck<2;deck++) for(int window=0;window<3;window++)
            box(s,r,side*2.66f,2.6f+deck,-9+window,.04f,.4f,.5f,0x30576c);
        box(s,r,0,6.1f,-9,1.2f,1.5f,1.4f,0xb43b30);
        box(s,r,0,7.6f,-9,1.3f,.3f,1.5f,0x18242b);
        box(s,r,0,6.1f,-7,.12f,2.5f,.12f,0xdddccc);
        box(s,r,0,7.9f,-7,2,.12f,.12f,0xdddccc);
        box(s,r,-2.9f,5.9f,-7,.15f,.15f,.15f,0xff493e);
        box(s,r,2.9f,5.9f,-7,.15f,.15f,.15f,0x49ec84);
        if(p.sailing()) for(int i=0;i<3;i++) box(s,r,0,-.15f,-13-i*2,2+i,.08f,1,0xd0e4e9);
        s.setInt("uVertexColor",1);
    }
    public void close(){cube.close();}
}
