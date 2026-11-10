package dev.jayms.net;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Scan/index comparison with actual page construction; no city qualification. */
public final class RegionReplayCellsCheck {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static String summary(double[] values) {
        var sorted=values.clone();Arrays.sort(sorted);
        return String.format(Locale.ROOT,"{\"median_ms\":%.6f,\"p95_ms\":%.6f,\"worst_ms\":%.6f}",(sorted[9]+sorted[10])/2,sorted[18],sorted[19]);
    }
    static List<Protocol.Edit> regionFixture() {
        var rows=new ArrayList<Protocol.Edit>();
        for(int i=0;i<4096;i++)rows.add(RegionReplayWindowCheck.whole(i,new int[]{0,Blocks.STONE,Blocks.GLASS,1031}[i&3]));
        for(int i=4095;i>=0;i--){var p=RegionReplayWindowCheck.whole(i,0);rows.add(new Protocol.Edit(p.x(),p.y(),p.z(),Blocks.piece(Blocks.LED,4),4,i&15,(i>>4)&15,(i>>8)&15,i*37));}
        return rows;
    }
    static List<Protocol.Edit> fragmentedFixture() {
        var rows=new ArrayList<Protocol.Edit>();var p=RegionReplayWindowCheck.whole(0,0);rows.add(p);
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)rows.add(new Protocol.Edit(p.x(),p.y(),p.z(),Blocks.piece(Blocks.LED,4),4,x,y,z,(x+16*z+256*y)*37));
        return rows;
    }
    static String run(Path folder,List<Protocol.Edit> rows,int windows)throws Exception {
        var region=RegionReplayWindowCheck.REGION;var source=RegionReplayWindowCheck.source(rows);
        var expectedWorld=new WorldVoxels(new Terrain(42));rows.forEach(expectedWorld::apply);
        var expectedHistories=new ArrayList<Map<String,Protocol.Edit>>();
        for(int i=0;i<windows;i++) {
            var range=windows==1?new RegionReplayPage.CellRange(0,1):new RegionReplayPage.CellRange(i*64,i*64+64);
            var expected=new LinkedHashMap<String,Protocol.Edit>();for(var edit:rows)if(range.contains(edit))WorldVoxels.rememberValidated(expected,edit);expectedHistories.add(expected);
        }
        int budget=windows==1?4097:128;
        var stage=new double[20];var build=new double[20];var scan=new double[20];var indexed=new double[20];var total=new double[20];
        long copyBytes=0;int scanCallbacks=0,indexCallbacks=0,buildCallbacks=0;
        for(int run=-3;run<20;run++) {
            long before=System.nanoTime();
            try(var spool=RegionReplaySpool.stage(folder,new DataInputStream(new ByteArrayInputStream(source)),e->true,()->false)) {
                double stageMs=(System.nanoTime()-before)/1e6;int[] buildCalls={0};before=System.nanoTime();
                try(var cells=RegionReplayCells.build(folder,spool,region,rows.size(),e->{buildCalls[0]++;return true;},()->false)) {
                    double buildMs=(System.nanoTime()-before)/1e6;copyBytes=cells.encodedBytes();
                    double scanMs=0,indexMs=0;int[] scanCalls={0},indexCalls={0};
                    // Alternate traversal order to reduce a fixed first-mode bias.
                    for(int mode=0;mode<2;mode++) {
                        boolean useIndex=((run&1)==0)==(mode==0);
                        var assembled=new WorldVoxels(new Terrain(42));
                        for(int i=0;i<windows;i++) {
                            var range=windows==1?new RegionReplayPage.CellRange(0,1):new RegionReplayPage.CellRange(i*64,i*64+64);
                            before=System.nanoTime();
                            var page=useIndex?RegionReplayPage.loadIndexed(cells,range,new Terrain(42),budget,e->{indexCalls[0]++;return true;},()->false):
                                    RegionReplayPage.loadWindow(spool,region,range,new Terrain(42),rows.size(),budget,e->{scanCalls[0]++;return true;},()->false);
                            double elapsed=(System.nanoTime()-before)/1e6;if(useIndex)indexMs+=elapsed;else scanMs+=elapsed;
                            require(range.equals(page.range()),"Coverage");
                            require(new ArrayList<>(expectedHistories.get(i).entrySet()).equals(new ArrayList<>(page.history().entrySet())),"Filtered history order");
                            for(var entry:page.cells().entrySet()){var p=entry.getKey();assembled.installCellImage(p.x(),p.y(),p.z(),entry.getValue());}
                        }
                        for(var row:rows)require(expectedWorld.cell(row.x(),row.y(),row.z()).sameValues(assembled.cell(row.x(),row.y(),row.z())),"Geometry oracle");
                    }
                    require(buildCalls[0]==2*rows.size()&&scanCalls[0]==windows*rows.size()&&indexCalls[0]==rows.size(),"Policy callback counts");
                    scanCallbacks=scanCalls[0];indexCallbacks=indexCalls[0];buildCallbacks=buildCalls[0];
                    if(run>=0){stage[run]=stageMs;build[run]=buildMs;scan[run]=scanMs;indexed[run]=indexMs;total[run]=buildMs+indexMs;}
                }
            }
            try(var files=Files.list(folder)){require(files.findAny().isEmpty(),"Spool/index cleanup");}
        }
        // Independent copied-index lifetime and metadata admission before I/O.
        RegionReplayCells detached;
        try(var spool=RegionReplaySpool.stage(folder,new DataInputStream(new ByteArrayInputStream(source)),e->true,()->false)) {
            detached=RegionReplayCells.build(folder,spool,region,rows.size(),e->true,()->false);
        }
        try(detached) {
            var range=windows==1?new RegionReplayPage.CellRange(0,1):new RegionReplayPage.CellRange(0,64);int[] calls={0};
            try{detached.readWindow(range,budget-1,e->{calls[0]++;return true;},()->false);throw new AssertionError("Selected budget accepted");}
            catch(IOException expected){require(calls[0]==0,"Budget decoded payload");}
            var page=RegionReplayPage.loadIndexed(detached,range,new Terrain(42),budget,e->true,()->false);
            require(new ArrayList<>(expectedHistories.get(0).entrySet()).equals(new ArrayList<>(page.history().entrySet())),"Post-source-close page");
            var entry=page.cells().entrySet().iterator().next();var p=entry.getKey();var original=entry.getValue().tree().copy();
            var installed=new WorldVoxels(new Terrain(42));installed.installCellImage(p.x(),p.y(),p.z(),entry.getValue());installed.apply(new Protocol.Edit(p.x(),p.y(),p.z(),0));
            require(original.sameValues(entry.getValue().tree()),"Image independence");
        }
        try(var files=Files.list(folder)){require(files.findAny().isEmpty(),"Detached cleanup");}
        return "{\"source_rows\":"+rows.size()+",\"windows\":"+windows+",\"copied_bytes\":"+copyBytes+",\"metadata_primitive_bytes\":"+RegionReplayCells.METADATA_PRIMITIVE_BYTES+
                ",\"scan_policy_callbacks\":"+scanCallbacks+",\"index_read_policy_callbacks\":"+indexCallbacks+",\"index_build_policy_callbacks\":"+buildCallbacks+
                ",\"source_stage\":"+summary(stage)+",\"index_build\":"+summary(build)+",\"scan_load_sum\":"+summary(scan)+",\"indexed_load_sum\":"+summary(indexed)+",\"build_plus_indexed\":"+summary(total)+
                ",\"stage_samples_ms\":"+Arrays.toString(stage)+",\"build_samples_ms\":"+Arrays.toString(build)+",\"scan_samples_ms\":"+Arrays.toString(scan)+",\"indexed_samples_ms\":"+Arrays.toString(indexed)+",\"total_samples_ms\":"+Arrays.toString(total)+"}";
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Fresh directory, result JSON, evidence PNG");
        var folder=Path.of(args[0]);var result=Path.of(args[1]);var png=Path.of(args[2]);
        require(!Files.exists(folder)&&!Files.exists(result)&&!Files.exists(png),"Fresh outputs required");Files.createDirectory(folder);
        String region=run(folder,regionFixture(),64);String fragment=run(folder,fragmentedFixture(),1);Files.delete(folder);
        String json="{\"status\":\"passed\",\"qualification\":false,\"warmups\":3,\"runs\":20,\"region\":"+region+",\"full_cell\":"+fragment+"}";
        Files.writeString(result,json+"\n");System.out.println(json);
        String text="Voxel One: copied region cell index\n\nActual CLI checks passed before this window.\n\n4096-cell region / 64 windows; full4097-row coloured cell.\nScan and indexed pages match independent history and geometry.\nFiltered source order, air, material, LED colour and model IDs retained.\nSource-close lifetime, row admission, cleanup and independent installation pass.\n\n3 warmups +20 samples per fixture; alternating read-mode order.\nFull matching-region policy validated twice at index build.\nIndexed reads recheck only selected copied cells; other corruption is deferred.\nOriginal scan path remains; no wire/save/public-limit change.\n\nPrivate index: 28 bytes per row, at most56000000 data bytes per region.\nRoot arrays:81928 primitive bytes, plus Java objects and staging buffers.\nSetup cost and complete timing samples are in the actual JSON receipt.\n\nTemporary worker staging, not live authority, durable storage or city support.\nCold cache, GPU, peak memory, network and full qualification gates remain pending.";
        javax.swing.SwingUtilities.invokeAndWait(()->{
            var frame=new javax.swing.JFrame("Voxel One cell-index CLI");var area=new javax.swing.JTextArea(text);
            area.setEditable(false);area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,0,15));area.setMargin(new java.awt.Insets(20,20,20,20));
            frame.add(area);frame.setSize(1150,610);frame.setLocation(30,30);frame.setVisible(true);
            var timer=new javax.swing.Timer(1200,e->{try{javax.imageio.ImageIO.write(new java.awt.Robot().createScreenCapture(frame.getBounds()),"png",png.toFile());}
                catch(Exception failure){throw new RuntimeException(failure);}finally{frame.dispose();}});timer.setRepeats(false);timer.start();
        });
    }
}
