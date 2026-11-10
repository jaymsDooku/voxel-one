package dev.jayms.net;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;

/** Synthetic file-race integration check. Attribute checks are not generation locks. */
public final class ReadbackFileChangeCheck {
    static final RegionReplayWorker.Revision REV=new RegionReplayWorker.Revision(1,2,3);
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void write(Path path)throws Exception {
        var rows=new WorldVoxels.History();WorldVoxels.remember(rows,new Protocol.Edit(0,160,0,Blocks.STONE));
        try(var staged=StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,new dev.jayms.net.model.ModelLibrary(),rows,()->false,n->{})) {
            staged.publish(new Inventory(),20,List.of(),0);
        }
    }
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        require(!Files.exists(root)&&!Files.exists(result)&&!Files.exists(image),"Fresh outputs required");Files.createDirectory(root);
        Path spool=Files.createDirectory(root.resolve("spool")),world=root.resolve("world.dat");
        try {
            for(int mode=0;mode<3;mode++) {
                write(world);int variant=mode;var changed=new AtomicBoolean();
                try {
                    var unexpected=LocalGame.openRegionalReadback(world,spool,REV,()->false,n->{
                        if(n>0&&changed.compareAndSet(false,true))try {
                            if(variant==2){Files.delete(world);return;}
                            var time=Files.getLastModifiedTime(world);
                            if(variant==0) {
                                try(var file=new RandomAccessFile(world.toFile(),"rw")){file.seek(4);file.writeLong(43);}
                                Files.setLastModifiedTime(world,FileTime.fromMillis(time.toMillis()+2000));
                            }else {
                                var replacement=root.resolve("replacement.dat");Files.write(replacement,Files.readAllBytes(world));Files.setLastModifiedTime(replacement,time);
                                Files.move(replacement,world,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                            }
                        }catch(IOException error){throw new UncheckedIOException(error);}
                    });unexpected.spool().close();throw new AssertionError("Changed source escaped");
                }catch(IOException expected){require(changed.get(),"Mutation callback reached");if(mode<2)require(expected.getMessage().equals("Saved world changed during regional readback"),"Attribute guard reached");}
                try(var files=Files.list(spool)){require(files.count()==0,"Failed source cleanup");}
            }
            write(world);byte[] original=Files.readAllBytes(world);
            var source=LocalGame.openRegionalReadback(world,spool,REV,()->false,n->{});
            try(var owned=source.spool()) {
                require(Arrays.equals(original,Files.readAllBytes(world)),"Unchanged source preserved");Files.delete(world);
                require(owned.size()==1&&source.terrain().seed==42&&!source.metadata().transfer().replayComplete(),"Detached validated source survives later deletion");
            }
            try(var files=Files.list(spool)){require(files.count()==0,"Success cleanup");}
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"observed_file_changes_rejected\":3,\"cleanup\":true,\"unchanged_source_regression\":true,\"post_open_snapshot\":true,\"immutable_generation_guarantee\":false,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One readback file-change check");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: observed readback file changes rejected\n\nSame-size in-place write with changed timestamp: rejected\nAtomic replacement with same bytes and mtime: rejected\nSource deletion during opening: rejected\nPrivate spool cleanup: passed\nUnchanged source / detached snapshot regression: passed\n\nAttribute checks are NOT immutable-generation locks.\nNo live paging or performance qualification.\nSynthetic files only; private scratch removed.\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,17));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try {
            var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);
            javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());
        }finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
