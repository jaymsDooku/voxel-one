package dev.jayms.net;

import dev.jayms.ChunkPos;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;

/** Actual bounded-cell-window integration, not live paging qualification. */
public final class RegionReplayWindowCheck {
    static final ChunkPos REGION=new ChunkPos(-2,-2,-1);
    static Protocol.Edit whole(int key,int type) {
        return new Protocol.Edit(-32+(key&15),-32+(key>>8),-16+((key>>4)&15),type);
    }
    static byte[] source(List<Protocol.Edit> rows)throws Exception {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(rows.size());
        var buffer=new ByteArrayOutputStream(24*1024);var body=new DataOutputStream(buffer);
        for(int start=0;start<rows.size();start+=1024) {
            buffer.reset();int count=Math.min(1024,rows.size()-start);
            for(int i=0;i<count;i++)rows.get(start+i).write(body);
            RegionReplay.section(out,REGION,count,buffer);
        }
        return bytes.toByteArray();
    }
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static String summary(double[] values) {
        var sorted=values.clone();Arrays.sort(sorted);
        return String.format(Locale.ROOT,"{\"median_ms\":%.6f,\"p95_ms\":%.6f,\"worst_ms\":%.6f}",(sorted[9]+sorted[10])/2,sorted[18],sorted[19]);
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Fresh directory, JSON result, PNG evidence");
        var root=Path.of(args[0]);var result=Path.of(args[1]);var png=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(png))throw new IllegalArgumentException("Fresh outputs required");
        Files.createDirectory(root);var rows=new ArrayList<Protocol.Edit>();
        for(int i=0;i<4096;i++)rows.add(whole(i,new int[]{0,Blocks.STONE,Blocks.GLASS,1031}[i&3]));
        for(int i=4095;i>=0;i--) {
            var p=whole(i,0);rows.add(new Protocol.Edit(p.x(),p.y(),p.z(),Blocks.piece(Blocks.LED,4),4,i&15,(i>>4)&15,(i>>8)&15,i*37));
        }
        var encoded=source(rows);var expectedWorld=new WorldVoxels(new Terrain(42));rows.forEach(expectedWorld::apply);
        var expectedHistories=new ArrayList<Map<String,Protocol.Edit>>();
        for(int first=0;first<4096;first+=64) {
            var range=new RegionReplayPage.CellRange(first,first+64);var history=new LinkedHashMap<String,Protocol.Edit>();
            for(var row:rows)if(range.contains(row))WorldVoxels.rememberValidated(history,row);
            expectedHistories.add(history);
        }
        var fragmented=new ArrayList<Protocol.Edit>();var origin=whole(0,0);fragmented.add(origin);
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
            fragmented.add(new Protocol.Edit(origin.x(),origin.y(),origin.z(),Blocks.piece(Blocks.LED,4),4,x,y,z,(x+16*z+256*y)*37));
        var fragmentedSource=source(fragmented);var fragmentedWorld=new WorldVoxels(new Terrain(42));fragmented.forEach(fragmentedWorld::apply);
        var stage=new double[20];var windows=new double[20];var wholeBatch=new double[20];
        var fragmentedStage=new double[20];var fragmentedLoad=new double[20];
        RegionReplayPage retained=null;int leaves=0;
        for(int run=-3;run<20;run++) {
            long started=System.nanoTime();
            try(var spool=RegionReplaySpool.stage(root,new DataInputStream(new ByteArrayInputStream(encoded)),e->true,()->false)) {
                double stageMs=(System.nanoTime()-started)/1e6;var assembled=new WorldVoxels(new Terrain(42));
                long batchStarted=System.nanoTime();double windowMs=0;int cells=0;leaves=0;
                for(int first=0;first<4096;first+=64) {
                    var range=new RegionReplayPage.CellRange(first,first+64);long before=System.nanoTime();
                    var page=RegionReplayPage.loadWindow(spool,REGION,range,new Terrain(42),8192,128,e->true,()->false);
                    windowMs+=(System.nanoTime()-before)/1e6;
                    require(range.equals(page.range()),"Coverage mismatch");
                    require(new ArrayList<>(expectedHistories.get(first/64).entrySet()).equals(new ArrayList<>(page.history().entrySet())),"Filtered source order");
                    require(page.cells().size()==64,"Cell boundary");cells+=page.cells().size();
                    for(var entry:page.cells().entrySet()) {
                        var p=entry.getKey();assembled.installCellImage(p.x(),p.y(),p.z(),entry.getValue());leaves+=entry.getValue().tree().leafCount();
                    }
                    retained=page;
                }
                double batchMs=(System.nanoTime()-batchStarted)/1e6;require(cells==4096,"Coverage count");
                for(var row:rows)require(expectedWorld.cell(row.x(),row.y(),row.z()).sameValues(assembled.cell(row.x(),row.y(),row.z())),"Assembled geometry mismatch");
                if(run>=0){stage[run]=stageMs;windows[run]=windowMs;wholeBatch[run]=batchMs;}
                if(run==19) {
                    var range=new RegionReplayPage.CellRange(0,64);int[] calls={0};
                    try{RegionReplayPage.loadWindow(spool,REGION,range,new Terrain(42),8191,128,e->{calls[0]++;return true;},()->false);throw new AssertionError("Decoded budget accepted");}
                    catch(IOException expected){require(calls[0]==0,"Payload decoded before admission");}
                    try{RegionReplayPage.loadWindow(spool,REGION,range,new Terrain(42),8192,127,e->true,()->false);throw new AssertionError("Selected budget accepted");}catch(IOException expected){}
                    try{RegionReplayPage.loadWindow(spool,REGION,range,new Terrain(42),8192,128,e->!Blocks.structureModel(e.type()),()->false);throw new AssertionError("Excluded model policy ignored");}catch(IOException expected){}
                    try{RegionReplayPage.loadWindow(spool,REGION,range,new Terrain(42),8192,128,e->true,()->true);throw new AssertionError("Cancellation accepted");}catch(CancellationException expected){}
                    require(RegionReplayPage.loadWindow(spool,REGION,range,new Terrain(42),8192,128,e->true,()->false).cells().size()==64,"Retry");
                }
            }
            try(var files=Files.list(root)){require(files.findAny().isEmpty(),"Temporary files leaked");}
            long fragmentStarted=System.nanoTime();
            try(var spool=RegionReplaySpool.stage(root,new DataInputStream(new ByteArrayInputStream(fragmentedSource)),e->true,()->false)) {
                double stageMs=(System.nanoTime()-fragmentStarted)/1e6;fragmentStarted=System.nanoTime();
                var page=RegionReplayPage.loadWindow(spool,REGION,new RegionReplayPage.CellRange(0,1),new Terrain(42),4097,4097,e->true,()->false);
                double loadMs=(System.nanoTime()-fragmentStarted)/1e6;
                require(page.history().size()==4097&&page.cells().size()==1,"Full-cell row retention");
                var image=page.cells().values().iterator().next().tree();
                require(image.leafCount()==4096&&fragmentedWorld.cell(origin.x(),origin.y(),origin.z()).sameValues(image),"Full-cell geometry");
                if(run>=0){fragmentedStage[run]=stageMs;fragmentedLoad[run]=loadMs;}
            }
            try(var files=Files.list(root)){require(files.findAny().isEmpty(),"Fragmented spool cleanup");}
        }
        // The last returned window remains detached after every spool closes.
        var entry=retained.cells().entrySet().iterator().next();var p=entry.getKey();var before=entry.getValue().tree().copy();
        var installed=new WorldVoxels(new Terrain(42));installed.installCellImage(p.x(),p.y(),p.z(),entry.getValue());installed.apply(new Protocol.Edit(p.x(),p.y(),p.z(),0));
        require(before.sameValues(entry.getValue().tree()),"Installed edit changed page");Files.delete(root);
        String json="{\"status\":\"passed\",\"qualification\":false,\"warmups\":3,\"runs\":20,\"source_rows\":8192,\"cells\":4096,\"windows_per_run\":64,\"rows_per_window\":128,\"leaves\":"+leaves+
                ",\"stage\":"+summary(stage)+",\"window_load_sum\":"+summary(windows)+",\"batch_including_checks_and_install\":"+summary(wholeBatch)+
                ",\"stage_samples_ms\":"+Arrays.toString(stage)+",\"window_load_sum_samples_ms\":"+Arrays.toString(windows)+",\"batch_samples_ms\":"+Arrays.toString(wholeBatch)+
                ",\"full_cell\":{\"source_rows\":4097,\"history_rows\":4097,\"cells\":1,\"leaves\":4096,\"stage\":"+summary(fragmentedStage)+",\"load\":"+summary(fragmentedLoad)+
                ",\"stage_samples_ms\":"+Arrays.toString(fragmentedStage)+",\"load_samples_ms\":"+Arrays.toString(fragmentedLoad)+"}}";
        Files.writeString(result,json+"\n");System.out.println(json);
        String text="Voxel One: bounded replay cell windows\n\nActual CLI checks passed before this window.\n\n8192 source rows; 4096 edited cells; 64 windows per run.\n128 selected rows / 64 cells per window, negative coordinates.\nFiltered source order and complete assembled geometry match independent replay.\nExplicit air, stone, glass, LED colour and model IDs retained.\nDecoded/selected limits, excluded model policy, cancellation/retry pass.\nOwned spool cleanup and post-close image independence pass.\n\n3 warmups + 20 warm private-file component samples.\nStage: "+summary(stage)+"\nAll64 window loads summed: "+summary(windows)+
                "\nFullcell(clear+4096 coloured leaves) load: "+summary(fragmentedLoad)+
                "\n\nEvery window still decodes the whole selected region. Repeated I/O remains.\nPrivate worker only; no live paging, durable page or city support claim.\nGPU, network, peak memory and all full qualification gates remain pending.";
        javax.swing.SwingUtilities.invokeAndWait(()->{
            var frame=new javax.swing.JFrame("Voxel One replay window CLI");var area=new javax.swing.JTextArea(text);
            area.setEditable(false);area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,0,15));area.setMargin(new java.awt.Insets(20,20,20,20));
            frame.add(area);frame.setSize(1150,570);frame.setLocation(30,30);frame.setVisible(true);
            var timer=new javax.swing.Timer(1200,e->{try{javax.imageio.ImageIO.write(new java.awt.Robot().createScreenCapture(frame.getBounds()),"png",png.toFile());}
                catch(Exception failure){throw new RuntimeException(failure);}finally{frame.dispose();}});timer.setRepeats(false);timer.start();
        });
    }
}
