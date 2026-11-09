package dev.jayms.net.structure;

import dev.jayms.net.model.SparseVoxelOctree;
import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Internal immutable geometry bytes, in bounded segments rather than retained node graphs. */
final class StructureGeometry {
    record Decoded(int leaves,boolean uniform,int value) {}
    private static final int SHIFT=16,SIZE=1<<SHIFT,MASK=SIZE-1;
    private final byte[][] segments;
    private final int length;
    private StructureGeometry(byte[][] segments,int length){this.segments=segments;this.length=length;}

    private interface Bytes {
        int length();
        byte[] segment(int index);
    }
    private final Bytes bytes=new Bytes() {
        public int length(){return length;}
        public byte[] segment(int index){return segments[index];}
    };
    SparseVoxelOctree read(int offset){return read(bytes,offset);}
    void write(int offset,DataOutputStream out)throws IOException {write(bytes,offset,out);}

    private static int at(Bytes bytes,int offset){return bytes.segment(offset>>>SHIFT)[offset&MASK]&255;}
    private static int recordLength(Bytes bytes,int offset)throws IOException {
        if(offset<0||(long)offset+4>bytes.length())throw new IOException("Invalid packed geometry offset");
        int length=at(bytes,offset)<<24|at(bytes,offset+1)<<16|at(bytes,offset+2)<<8|at(bytes,offset+3);
        // A complete 16-resolution octree is below 64 KiB, even without uniform folding.
        if(length<1||length>SIZE||(long)offset+4+length>bytes.length())throw new IOException("Invalid packed geometry length");
        return length;
    }
    private static SparseVoxelOctree read(Bytes bytes,int offset) {
        try {
            int length=recordLength(bytes,offset);
            try(var in=new DataInputStream(new Input(bytes,offset+4,length))) {
                var tree=SparseVoxelOctree.read(in,16).freeze();
                if(in.read()!=-1)throw new IOException("Trailing packed geometry");
                return tree;
            }
        } catch(IOException invalid){throw new IllegalStateException("Invalid internal structure geometry",invalid);}
    }
    private static void write(Bytes bytes,int offset,DataOutputStream out)throws IOException {
        int remaining=recordLength(bytes,offset);offset+=4;
        while(remaining>0) {
            int count=Math.min(remaining,SIZE-(offset&MASK));
            out.write(bytes.segment(offset>>>SHIFT),offset&MASK,count);offset+=count;remaining-=count;
        }
    }
    private static final class Input extends InputStream {
        private final Bytes bytes;
        private final int end;
        private int offset;
        Input(Bytes bytes,int offset,int length){this.bytes=bytes;this.offset=offset;end=offset+length;}
        @Override public int read(){return offset==end?-1:at(bytes,offset++);}
        @Override public int read(byte[] target,int start,int length) {
            Objects.checkFromIndexSize(start,length,target.length);
            if(length==0)return 0;
            if(offset==end)return -1;
            int count=Math.min(length,end-offset),remaining=count;
            while(remaining>0) {
                int n=Math.min(remaining,SIZE-(offset&MASK));
                System.arraycopy(bytes.segment(offset>>>SHIFT),offset&MASK,target,start,n);
                offset+=n;start+=n;remaining-=n;
            }
            return count;
        }
    }

    /** Append-only worker storage. Published full segments can never be overwritten. */
    static final class Builder extends OutputStream {
        private final List<byte[]> segments=new ArrayList<>();
        private final DataOutputStream out=new DataOutputStream(this);
        private int length;
        private final Bytes bytes=new Bytes() {
            public int length(){return length;}
            public byte[] segment(int index){return segments.get(index);}
        };
        int size(){return length;}
        private byte[] segment(int index){return segments.get(index);}
        int add(SparseVoxelOctree tree)throws IOException {
            if(tree.size()!=16)throw new IOException("Invalid structure geometry resolution");
            int start=length;out.writeInt(0);tree.write(out);
            int count=length-start-4;
            for(int i=0;i<4;i++)segment((start+i)>>>SHIFT)[(start+i)&MASK]=(byte)(count>>>(24-i*8));
            return start;
        }
        /** Strict world-cell wire validation without allocating a temporary node graph.
         * Only the private package decoder uses this path. Failure aborts that decode;
         * partial arena bytes never become a published cell. */
        Decoded readValidated(DataInputStream in,int leafBudget,BooleanSupplier cancel)throws IOException {
            Objects.requireNonNull(cancel);StructurePackage.cancelled(cancel);
            int size=in.readUnsignedByte();if(size==0)size=in.readUnsignedShort();
            if(size!=16)throw new IOException("Invalid octree resolution");
            int tag=in.readUnsignedByte();
            if(tag==0)throw new IOException("Invalid/empty cell");
            if(tag==1)return new Decoded(1,true,leaf(in,16,leafBudget));
            if(tag!=2)throw new IOException("Invalid octree branch");
            int start=length;out.writeInt(0);out.writeByte(16);out.writeByte(2);
            int leaves=0;
            for(int i=0;i<8;i++)leaves+=node(in,8,leafBudget-leaves,cancel);
            if(leaves==0)throw new IOException("Invalid/empty cell");
            int count=length-start-4;
            if(count>SIZE)throw new IOException("Invalid packed geometry length");
            for(int i=0;i<4;i++)segment((start+i)>>>SHIFT)[(start+i)&MASK]=(byte)(count>>>(24-i*8));
            return new Decoded(leaves,false,start);
        }
        private int node(DataInputStream in,int side,int leafBudget,BooleanSupplier cancel)throws IOException {
            StructurePackage.cancelled(cancel);int tag=in.readUnsignedByte();
            if(tag==0){out.writeByte(0);return 0;}
            if(tag==1){int color=leaf(in,side,leafBudget);out.writeByte(1);out.writeInt(color);return 1;}
            if(tag!=2||side==1)throw new IOException("Invalid octree branch");
            out.writeByte(2);int leaves=0;
            for(int i=0;i<8;i++)leaves+=node(in,side/2,leafBudget-leaves,cancel);
            return leaves;
        }
        private static int leaf(DataInputStream in,int side,int budget)throws IOException {
            int color=in.readInt();
            if(color>>>24!=255&&color>>>24!=254)throw new IOException("Invalid voxel value");
            if(budget<1)throw new IOException("Experimental geometry cap");
            StructurePackage.validateMaterial(side,color);return color;
        }
        SparseVoxelOctree read(int offset){return StructureGeometry.read(bytes,offset);}
        @Override public void write(int value)throws IOException {
            budget(1);if((length&MASK)==0)segments.add(new byte[SIZE]);
            segment(length>>>SHIFT)[length&MASK]=(byte)value;length++;
        }
        @Override public void write(byte[] data,int offset,int count)throws IOException {
            Objects.checkFromIndexSize(offset,count,data.length);budget(count);
            while(count>0) {
                if((length&MASK)==0)segments.add(new byte[SIZE]);
                int n=Math.min(count,SIZE-(length&MASK));
                System.arraycopy(data,offset,segment(length>>>SHIFT),length&MASK,n);
                length+=n;offset+=n;count-=n;
            }
        }
        private void budget(int count)throws IOException {
            if((long)length+count>StructurePackage.DECODED)throw new IOException("Packed structure geometry limit");
        }
        StructureGeometry snapshot() {
            byte[][] frozen=segments.toArray(byte[][]::new);
            // The appendable tail is copied; completed segments contain only committed records.
            if((length&MASK)!=0)frozen[frozen.length-1]=Arrays.copyOf(frozen[frozen.length-1],length&MASK);
            return new StructureGeometry(frozen,length);
        }
    }
}
