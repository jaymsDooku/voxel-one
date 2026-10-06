package dev.jayms.net.city;

import dev.jayms.net.*;
import java.util.*;

/** Coastal infrastructure and reusable voxel cargo/carrier components. Carriers are docked assets. */
public final class Shipping {
    public static final int DEPTH = 20;

    public static List<Protocol.Edit> container(int x, int y, int z, int material) {
        var edits = new ArrayList<Protocol.Edit>();
        // Closed cargo box, ridged roof, contrasting corner posts and double doors.
        for (int dx=0; dx<2; dx++) for (int dz=0; dz<4; dz++) for (int dy=0; dy<2; dy++) {
            boolean post = dz==0 || dz==3;
            edits.add(new Protocol.Edit(x+dx,y+dy,z+dz,post?Blocks.STONE:material));
        }
        for (int dz=1;dz<3;dz++) for(int dx=0;dx<8;dx++)
            edits.add(Protocol.Edit.at(x+dx*.25,y+2,z+dz,Blocks.piece(Blocks.STONE,3),3));
        for (int dx=0; dx<2; dx++)
            edits.add(Protocol.Edit.at(x+dx+.5,y+.5,z-.125,Blocks.piece(Blocks.STONE,3),3));
        return edits;
    }

    public static List<Protocol.Edit> carrier(int x, int y, int z) {
        var edits = new ArrayList<Protocol.Edit>();
        // Tapered bow, steel hull, cargo deck, bridge windows and navigation lamps.
        for(int dz=0;dz<6;dz++) for(int dx=0;dx<4;dx++) {
            if(dz==5 && (dx==0 || dx==3)) continue;
            edits.add(new Protocol.Edit(x+dx,y,z+dz,Blocks.STONE));
            edits.add(new Protocol.Edit(x+dx,y+1,z+dz,Blocks.PLANKS));
        }
        edits.addAll(container(x+1,y+2,z+2,Blocks.BRICKS));
        for(int dx=0;dx<4;dx++) for(int dy=2;dy<4;dy++)
            edits.add(new Protocol.Edit(x+dx,y+dy,z,dy==3?Blocks.GLASS:Blocks.BRICKS));
        edits.add(new Protocol.Edit(x,y+4,z,Blocks.LED).withColor(0xff4444));
        edits.add(new Protocol.Edit(x+3,y+4,z,Blocks.LED).withColor(0x44ff88));
        return edits;
    }

    public static List<Protocol.Edit> port(int x, int y, int z) {
        var edits = new ArrayList<Protocol.Edit>();
        for(int dx=0;dx<6;dx++) for(int dz=0;dz<14;dz++) {
            int deck = Math.max(Geography.SEA_LEVEL+2, y-Math.max(0,dz-3));
            edits.add(new Protocol.Edit(x+dx,deck,z+dz,Blocks.STONE));
            if ((dx==0 || dx==5) && dz%4==0)
                for(int h=Geography.SEA_LEVEL;h<deck;h++) edits.add(new Protocol.Edit(x+dx,h,z+dz,Blocks.WOOD));
        }
        edits.addAll(container(x,y+1,z,Blocks.BRICKS));
        edits.addAll(container(x+4,y+1,z,Blocks.PLANKS));
        // Crane leaves the central entrance and quay aisle clear.
        int crane = Math.max(Geography.SEA_LEVEL+2,y-5);
        for(int dy=1;dy<=6;dy++) edits.add(new Protocol.Edit(x,crane+dy,z+8,Blocks.STONE));
        for(int dx=0;dx<6;dx++) edits.add(new Protocol.Edit(x+dx,crane+6,z+8,Blocks.STONE));
        for(int dy=2;dy<6;dy++) edits.add(new Protocol.Edit(x+5,crane+dy,z+8,Blocks.WOOD));
        edits.addAll(carrier(x+1,Geography.SEA_LEVEL+1,z+14));
        return edits;
    }

    private Shipping() {}
}
