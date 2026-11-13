package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual LocalGame serialization/publication with bounded interleaved writers. */
public final class ReplayChunkProgressCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<3;mode++) {
                var test=new RegionReplayChunkProgressTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.implicitCellsAdvanceWithoutSourceRowsAndFinalCountIsNotAuthority();
                    case 1->test.callbackCancellationStopsBeforeNextCellAndRetryResetsProgress();
                    case 2->test.ownerObservesProgressWhileWorkerBlockedAndCancellationStillWins();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"fixtures\":3,\"progress_cells\":4096,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One replay chunk progress");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual detached chunk progress integration\n\nImplicit terrain advances with zero source rows\nCounts cover 0 through 4096 staged cells\nFinal count4096 can still fail terrain validation\nCallback cancellation stops at128; retry starts0\nOwner observes128 while actual worker decode is blocked\nRequest stays LOADING; no early result escapes\nCancellation wins; retry reaches4096 and returns geometry\nPage requests keep completedCells0\n\nNo live installation, data/mesh readiness or save promotion\nNo city, GPU, frame or 32-client qualification\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
