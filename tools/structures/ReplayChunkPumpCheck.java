package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual LocalGame serialization/publication with bounded interleaved writers. */
public final class ReplayChunkPumpCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<6;mode++) {
                var test=new RegionReplayChunkPumpTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.deduplicatedTicksRetainDetachedGeometryWithoutPromotion();
                    case 1->test.movingDemandCancelsObsoleteDecodeAndDropsItsGeometry();
                    case 2->test.fullSharedQueueReturnsBackpressureWithoutWaitingOrTreatingItAsFailure();
                    case 3->test.failedDecodeStaysFailedUntilExplicitRetryAndThenRecovers();
                    case 4->test.inputOwnerAndModelFencesRejectBeforePublication();
                    case 5->test.closeCancelsOwnedWorkWithoutClosingOrWaitingForWorker();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"fixtures\":6,\"max_desired_chunks\":2,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One detached chunk demand scheduler");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual detached chunk demand scheduler\n\nDuplicate demand submits once; at most two desired chunks\nMoving demand cancels obsolete work and drops its result\nFull shared queue reports backpressure without waiting\nDecode failure waits for explicit retry and recovers\nInvalid input, owner and model changes reject\nClose cancels owned work without waiting for worker\nSaved world bytes and partial-save rejection preserved\nImplicit terrain keeps baseline data state; chunks unloaded\n\nReady means detached CPU result, not live coverage\nNo Main/mesh activation or city/GPU/network qualification\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
