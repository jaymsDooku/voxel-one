package dev.jayms.net;

import dev.jayms.ChunkPos;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Actual bounded spool CLI, not full game/city or retained-memory qualification. */
public final class RegionReplaySpoolCheck {
    private static Protocol.Edit expected(int i) {
        int x=(i%2048)*16-16384,z=(i/2048)*16-8192;
        return i%3==0?new Protocol.Edit(x,128,z,0):i%3==1?
                new Protocol.Edit(x,128,z,Blocks.piece(Blocks.LED,1),1,1,0,1,0x123456):
                new Protocol.Edit(x,128,z,Blocks.STONE);
    }
    private static String hash(Path p)throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        try(var in=new DigestInputStream(Files.newInputStream(p),digest)){in.transferTo(OutputStream.nullOutputStream());}
        return HexFormat.of().formatHex(digest.digest());
    }
    public static void main(String[] args)throws Exception {
        if(args.length<2||args.length>3)throw new IllegalArgumentException("Fresh directory, fresh result path, optional --evidence");
        var root=Path.of(args[0]);var result=Path.of(args[1]);
        boolean evidence=args.length==3&&args[2].equals("--evidence");
        if(args.length==3&&!evidence||Files.exists(root)||Files.exists(result))throw new IllegalArgumentException("Fresh paths required");
        Files.createDirectory(root);var rows=new ArrayList<String>();
        try {
            for(int records:new int[]{100_000,LocalGame.MAX_SAVED_EDITS}) {
                Path source=root.resolve("source.dat");
                try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(source),65536))) {
                    out.writeInt(records);var buffer=new ByteArrayOutputStream(24);var body=new DataOutputStream(buffer);
                    for(int i=0;i<records;i++) {
                        var edit=expected(i);if(!edit.valid())throw new AssertionError("Fixture edit");
                        buffer.reset();edit.write(body);RegionReplay.section(out,ChunkPos.fromBlock(edit.x(),edit.y(),edit.z()),1,buffer);
                    }
                    out.writeInt(0x12345678);
                }
                String before=hash(source);long start=System.nanoTime();double stageMs,readMs;long encoded;
                try(var in=new DataInputStream(new BufferedInputStream(Files.newInputStream(source),65536));
                    var spool=RegionReplaySpool.stage(root,in,e->true,()->false)) {
                    stageMs=(System.nanoTime()-start)/1e6;encoded=spool.encodedBytes();
                    if(spool.size()!=records||encoded!=4L+44L*records||in.readInt()!=0x12345678||in.read()!=-1)throw new AssertionError("Count/length/tail");
                    int[] seen={0};start=System.nanoTime();
                    spool.visit(e->true,()->false,(region,section)->{
                        if(section.size()!=1)throw new AssertionError("Worst-case section fixture");
                        var edit=section.get(0);if(!edit.equals(expected(seen[0]++)))throw new AssertionError("Order/air/fractional/color/material");
                    });
                    readMs=(System.nanoTime()-start)/1e6;if(seen[0]!=records)throw new AssertionError("Replay count");
                }
                if(!hash(source).equals(before))throw new AssertionError("Source modified");
                try(var paths=Files.list(root)){if(paths.count()!=1)throw new AssertionError("Spool cleanup");}
                Files.delete(source);
                rows.add(String.format(Locale.ROOT,"{\"records\":%d,\"sections\":%d,\"encoded_bytes\":%d,\"stage_ms\":%.6f,\"read_ms\":%.6f}",records,records,encoded,stageMs,readMs));
            }
        }finally {
            // Every owned spool must already be gone. Leave failures for inspection.
            try(var paths=Files.list(root)){if(paths.findAny().isEmpty())Files.delete(root);}
        }
        String json="{\"status\":\"passed\",\"heap_max_bytes\":"+Runtime.getRuntime().maxMemory()+",\"runs_per_fixture\":1,\"qualification\":false,\"rows\":["+String.join(",",rows)+"]}";
        Files.writeString(result,json+"\n");System.out.println(json);
        if(evidence)javax.swing.SwingUtilities.invokeAndWait(()->{
            var frame=new javax.swing.JFrame("Voxel One bounded replay spool CLI");
            var text=new javax.swing.JTextArea("Voxel One: bounded regional replay spool\n\nActual CLI integration checks passed before this window.\n\n"+json+"\n\nStreamed source files; no global edit map. One record per section.\nExact replay order, explicit air, fractional LED color and stone verified.\nWorld tail left unread; source bytes unchanged; owned spool files cleaned.\n\nOne run each, 64 MiB heap setting. No retained-memory bound claimed.\nPrivate worker staging only; not wired into live startup or authority.\n2,000,000-record cap unchanged. City/max and full game/network gates pending.");
            text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,0,15));text.setMargin(new java.awt.Insets(20,20,20,20));
            frame.add(text);frame.setSize(1050,560);frame.setLocation(50,50);frame.setVisible(true);
            var timer=new javax.swing.Timer(20000,e->frame.dispose());timer.setRepeats(false);timer.start();
        });
    }
}
