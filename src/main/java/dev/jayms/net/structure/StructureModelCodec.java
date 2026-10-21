package dev.jayms.net.structure;

import dev.jayms.net.Protocol;
import dev.jayms.net.model.*;
import java.io.*;

/** VSTR v2 dependency representation only. Standalone model and world codecs are unchanged. */
public final class StructureModelCodec {
    private StructureModelCodec() {}
    public static void write(DataOutputStream out,ModelDefinition model)throws IOException {
        // Retain the existing definition's name, opacity, resolution and encoded-size contract.
        var legacy=new ByteArrayOutputStream();model.write(new DataOutputStream(legacy));
        int size=model.voxels().size(),volume=size*size*size;
        long packed=1L+2+model.name().length()+1+volume/8+3L*model.voxels().occupied();
        // Sparse and uniform dependencies keep their smaller existing octree representation.
        if(1L+legacy.size()<=packed){out.writeByte(0);out.write(legacy.toByteArray());return;}
        byte[] occupied=new byte[volume/8];int[] colors=new int[volume];
        for(int i=0;i<volume;i++) {
            int color=model.voxels().get(i%size,(i/size)%size,i/(size*size));colors[i]=color;
            if(color!=0&&color>>>24!=255)throw new IOException("Structure models require opaque ARGB colours");
            if(color!=0)occupied[i>>>3]|=(byte)(1<<(i&7));
        }
        // Old files may contain redundant octree branches. Preserve their exact definition
        // identity instead of silently canonicalizing those branches through the voxel grid.
        byte[] canonical=treeBytes(colors,size),original=legacy.toByteArray();
        int treeStart=4+2+model.name().length();
        if(!java.util.Arrays.equals(canonical,java.util.Arrays.copyOfRange(original,treeStart,original.length))) {
            out.writeByte(0);out.write(original);return;
        }
        out.writeByte(1);
        out.writeUTF(model.name());out.writeByte(size);out.write(occupied);
        byte[] rgb=new byte[3*model.voxels().occupied()];int offset=0;
        for(int i=0;i<volume;i++)if(colors[i]!=0){rgb[offset++]=(byte)(colors[i]>>>16);rgb[offset++]=(byte)(colors[i]>>>8);rgb[offset++]=(byte)colors[i];}
        out.write(rgb);
    }
    public static ModelDefinition read(DataInputStream in)throws IOException {
        int encoding=in.readUnsignedByte();
        if(encoding==0){var model=ModelDefinition.read(in);return new ModelDefinition(model.name(),model.voxels().freeze());}
        if(encoding!=1)throw new IOException("Unknown structure model encoding");
        String name=Protocol.readText(in,32);int size=in.readUnsignedByte();
        if(size!=8&&size!=16&&size!=32)throw new IOException("Structure model resolution must be 8, 16 or 32");
        int volume=size*size*size;byte[] occupied=in.readNBytes(volume/8);
        if(occupied.length!=volume/8)throw new EOFException("Structure model occupancy");
        int[] colors=new int[volume];
        for(int i=0;i<volume;i++)if((occupied[i>>>3]&(1<<(i&7)))!=0)
            colors[i]=0xff000000|in.readUnsignedByte()<<16|in.readUnsignedByte()<<8|in.readUnsignedByte();
        // Build the existing octree wire tree directly, avoiding a persistent-tree edit per voxel.
        ModelDefinition model;
        try(var data=new DataInputStream(new ByteArrayInputStream(treeBytes(colors,size)))) {
            try {model=new ModelDefinition(name,SparseVoxelOctree.read(data,size).freeze());}
            catch(IllegalArgumentException failure){throw new IOException(failure.getMessage(),failure);}
        }
        model.write(new DataOutputStream(OutputStream.nullOutputStream()));
        return model;
    }
    private static byte[] treeBytes(int[] colors,int size)throws IOException {
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(bytes)){out.writeByte(size);node(out,colors,size,0,0,0,size);}
        return bytes.toByteArray();
    }
    private static void node(DataOutputStream out,int[] colors,int size,int x,int y,int z,int side)throws IOException {
        int first=colors[x+size*(y+size*z)];boolean uniform=true;
        outer:for(int dz=0;dz<side;dz++)for(int dy=0;dy<side;dy++)for(int dx=0;dx<side;dx++)
            if(colors[x+dx+size*(y+dy+size*(z+dz))]!=first){uniform=false;break outer;}
        if(uniform){out.writeByte(first==0?0:1);if(first!=0)out.writeInt(first);return;}
        out.writeByte(2);int half=side/2;
        for(int child=0;child<8;child++)node(out,colors,size,x+((child&1)==0?0:half),y+((child&2)==0?0:half),z+((child&4)==0?0:half),half);
    }
}
