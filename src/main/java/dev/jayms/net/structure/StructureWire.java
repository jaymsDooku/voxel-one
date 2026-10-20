package dev.jayms.net.structure;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** One validated publication carries dependency definitions before complete destination cells. */
public final class StructureWire {
    public record Delta(List<ModelLibrary.Entry> models,List<Protocol.CellState> cells) {
        public Delta {models=List.copyOf(models);cells=List.copyOf(cells);}
    }
    public static byte[] encode(Delta delta)throws IOException {
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(new StructurePackage.BudgetOutput(new GZIPOutputStream(new StructurePackage.BudgetOutput(bytes,StructurePackage.COMPRESSED)),StructurePackage.DECODED))) {
            out.writeInt(delta.models.size());
            for(var entry:delta.models){out.writeInt(entry.id());out.writeUTF(entry.author());entry.definition().write(out);}
            out.writeInt(delta.cells.size());for(var cell:delta.cells)cell.write(out);
        }
        if(bytes.size()>StructurePackage.COMPRESSED)throw new IOException("Structure synchronization byte limit");return bytes.toByteArray();
    }
    public static Delta decode(byte[] payload)throws IOException {
        if(payload.length>StructurePackage.COMPRESSED)throw new IOException("Structure synchronization byte limit");
        try(var raw=new GZIPInputStream(new ByteArrayInputStream(payload));var in=new DataInputStream(new Limited(raw))) {
            int n=in.readInt();if(n<0||n>ModelLibrary.MAX_CUSTOM+2048)throw new IOException("World dependency transfer limit");
            var models=new ArrayList<ModelLibrary.Entry>();
            for(int i=0;i<n;i++)models.add(new ModelLibrary.Entry(in.readInt(),Protocol.readText(in,16),ModelDefinition.read(in)));
            n=in.readInt();if(n<0||n>2_000_000)throw new IOException("Structure cell transfer limit");
            var cells=new ArrayList<Protocol.CellState>();var positions=new HashSet<String>();int leaves=0;
            for(int i=0;i<n;i++) {
                var cell=Protocol.CellState.read(in);
                if(!positions.add(cell.x()+","+cell.y()+","+cell.z()))throw new IOException("Duplicate structure cell");
                leaves+=cell.tree().leaves().size();if(leaves>2_000_000)throw new IOException("World synchronization leaf limit");
                cells.add(cell);
            }
            if(in.read()!=-1)throw new IOException("Trailing structure delta");return new Delta(models,cells);
        }
    }
    public static void writePayload(DataOutputStream out,byte[] payload)throws IOException {out.writeInt(payload.length);out.write(payload);}
    public static byte[] readPayload(DataInputStream in)throws IOException {
        int n=in.readInt();if(n<0||n>StructurePackage.COMPRESSED)throw new IOException("Structure payload limit");
        byte[] payload=in.readNBytes(n);if(payload.length!=n)throw new EOFException();return payload;
    }
    private static final class Limited extends FilterInputStream {
        long remaining=StructurePackage.DECODED;
        Limited(InputStream in){super(in);}
        @Override public int read()throws IOException {int v=in.read();if(v>=0&&--remaining<0)throw new IOException("Decoded delta limit");return v;}
        @Override public int read(byte[] b,int o,int n)throws IOException {int count=in.read(b,o,(int)Math.min(n,remaining+1));if(count>0&&(remaining-=count)<0)throw new IOException("Decoded delta limit");return count;}
    }
}
