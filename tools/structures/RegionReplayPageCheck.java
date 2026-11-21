package dev.jayms.net;

import dev.jayms.ChunkPos;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;

/** Executes the private page workflow; does not qualify live paging or cities. */
public final class RegionReplayPageCheck {
    static final ChunkPos REGION=new ChunkPos(-2,11,-1);
    static byte[] encode(List<Protocol.Edit> rows)throws Exception {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(rows.size());
        // Deliberately fragmented sections, with recurring regions.
        var body=new ByteArrayOutputStream(24);var payload=new DataOutputStream(body);
        for(var row:rows){body.reset();row.write(payload);RegionReplay.section(out,ChunkPos.fromBlock(row.x(),row.y(),row.z()),1,body);}
        return bytes.toByteArray();
    }
    static RegionReplayPage load(RegionReplaySpool spool)throws Exception {
        return RegionReplayPage.load(spool,REGION,new Terrain(42),4096,64,e->true,()->false);
    }
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static String summary(double[] values) {
        var sorted=values.clone();Arrays.sort(sorted);
        return String.format(Locale.ROOT,"{\"median_ms\":%.6f,\"p95_ms\":%.6f,\"worst_ms\":%.6f}",
                (sorted[9]+sorted[10])/2,sorted[18],sorted[19]);
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Fresh directory, JSON result, PNG evidence");
        var root=Path.of(args[0]);var result=Path.of(args[1]);var png=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(png))throw new IllegalArgumentException("Fresh output paths required");
        Files.createDirectory(root);var rows=new ArrayList<Protocol.Edit>();
        // Eight leaves per cell, all 64 cell slots. LED, glass, explicit air,
        // unused fractional space and a custom model reference are retained.
        for(int cell=0;cell<64;cell++)for(int i=0;i<8;i++) {
            int x=-32+(cell&15),y=176+(cell>>4),z=-1;
            if(i==0)rows.add(new Protocol.Edit(32,180,32,Blocks.STONE));
            rows.add(new Protocol.Edit(x,y,z,i==0?0:Blocks.piece(i%2==0?Blocks.GLASS:Blocks.LED,4),
                    4,i,0,0,i%2==0?0:0x123456+i));
        }
        rows.add(new Protocol.Edit(-32,176,-1,1031));
        var encoded=encode(rows);var expectedHistory=new LinkedHashMap<String,Protocol.Edit>();
        var expectedWorld=new WorldVoxels(new Terrain(42));
        for(var edit:rows)if(ChunkPos.fromBlock(edit.x(),edit.y(),edit.z()).equals(REGION)) {
            WorldVoxels.rememberValidated(expectedHistory,edit);expectedWorld.apply(edit);
        }
        var stage=new double[20];var pageTimes=new double[20];int count=0,leaves=0;RegionReplayPage retained=null;
        for(int run=-3;run<20;run++) {
            long start=System.nanoTime();
            try(var spool=RegionReplaySpool.stage(root,new DataInputStream(new ByteArrayInputStream(encoded)),e->true,()->false)) {
                double stageMs=(System.nanoTime()-start)/1e6;start=System.nanoTime();var page=load(spool);
                double pageMs=(System.nanoTime()-start)/1e6;
                require(new ArrayList<>(expectedHistory.entrySet()).equals(new ArrayList<>(page.history().entrySet())),"Ordered history mismatch");
                leaves=0;
                for(var entry:page.cells().entrySet()) {
                    var p=entry.getKey();require(expectedWorld.cell(p.x(),p.y(),p.z()).sameValues(entry.getValue().tree()),"Geometry mismatch");
                    leaves+=entry.getValue().tree().leafCount();
                }
                require(page.cells().size()==64,"Cell boundary");count=page.history().size();retained=page;
                if(run>=0){stage[run]=stageMs;pageTimes[run]=pageMs;}
                if(run==19) {
                    int[] policyCalls={0};
                    try{RegionReplayPage.load(spool,REGION,new Terrain(42),512,64,e->{policyCalls[0]++;return true;},()->false);throw new AssertionError("Row budget accepted");}
                    catch(IOException expected){require(policyCalls[0]==0,"Budget decoded payload");}
                    try{RegionReplayPage.load(spool,REGION,new Terrain(42),4096,63,e->true,()->false);throw new AssertionError("Cell budget accepted");}catch(IOException expected){}
                    try{RegionReplayPage.load(spool,REGION,new Terrain(42),4096,64,e->true,()->true);throw new AssertionError("Cancellation accepted");}catch(CancellationException expected){}
                    require(load(spool).cells().size()==64,"Retry failed");
                    var empty=RegionReplayPage.load(spool,new ChunkPos(0,0,0),new Terrain(42),0,0,e->true,()->false);
                    require(empty.cells().isEmpty()&&empty.history().isEmpty(),"Missing region");
                }
            }
            try(var files=Files.list(root)){require(files.findAny().isEmpty(),"Spool files leaked");}
        }
        // A complete page remains usable after its owned spool is gone.
        var installed=new WorldVoxels(new Terrain(42));
        for(var entry:retained.cells().entrySet()){var p=entry.getKey();installed.installCellImage(p.x(),p.y(),p.z(),entry.getValue());}
        installed.apply(new Protocol.Edit(-32,176,-1,0));
        require(retained.cells().get(new WorldVoxels.CellPosition(-32,176,-1)).tree().get(0,0,0)!=0,"Installed edit changed page");
        Files.delete(root);
        String json="{\"status\":\"passed\",\"qualification\":false,\"warmups\":3,\"runs\":20,\"source_rows\":"+rows.size()+
                ",\"selected_rows\":513,\"history_rows\":"+count+",\"cells\":64,\"leaves\":"+leaves+
                ",\"stage\":"+summary(stage)+",\"page\":"+summary(pageTimes)+",\"stage_samples_ms\":"+Arrays.toString(stage)+
                ",\"page_samples_ms\":"+Arrays.toString(pageTimes)+"}";
        Files.writeString(result,json+"\n");System.out.println(json);
        String text="Voxel One: bounded private replay page\n\nActual CLI checks passed before this window.\n\n513 selected rows; 64 cells; recurring one-row sections.\nHistory and every voxel match independent ordered replay.\nLED colour, glass, explicit air and custom model ID retained.\nRow rejection precedes payload policy; cell cap rejects.\nCancellation, missing region, retry, spool cleanup pass.\nReturned images survive close and later installed-world edits.\n\n3 warmups + 20 warm private-file component samples.\nStage: "+summary(stage)+"\nPage: "+summary(pageTimes)+
                "\n\nPrivate result only. No live paging, durable page or city support claim.\nNo physical GPU, frame/tick/network or peak-memory qualification.\nPublic admission limits and rollout gates remain unchanged.";
        javax.swing.SwingUtilities.invokeAndWait(()->{
            var frame=new javax.swing.JFrame("Voxel One replay page CLI");var area=new javax.swing.JTextArea(text);
            area.setEditable(false);area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,0,15));area.setMargin(new java.awt.Insets(20,20,20,20));
            frame.add(area);frame.setSize(1080,570);frame.setLocation(35,35);frame.setVisible(true);
            var timer=new javax.swing.Timer(1200,e->{try {
                javax.imageio.ImageIO.write(new java.awt.Robot().createScreenCapture(frame.getBounds()),"png",png.toFile());
            }catch(Exception failure){throw new RuntimeException(failure);}finally{frame.dispose();}});timer.setRepeats(false);timer.start();
        });
    }
}
