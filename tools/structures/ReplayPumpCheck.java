package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual private replay pump integration. */
public final class ReplayPumpCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<6;mode++) {
                var test=new RegionReplayPumpTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.deduplicatedBoundedTicksPublishActualWorldDataWithoutPromotion();
                    case 1->test.changingDesiredWindowsCancelsObsoleteDecodeAndNeverPublishesIt();
                    case 2->test.fullSharedQueueReturnsBackpressureWithoutWaitingOrTreatingItAsFailure();
                    case 3->test.failedDecodeStaysFailedUntilExplicitRetryAndThenRecovers();
                    case 4->test.inputOwnerAndModelFencesRejectBeforePublication();
                    case 5->test.closeCancelsOwnedWorkWithoutClosingOrWaitingForWorker();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"deduplication_and_readonly_publication\":true,\"obsolete_decode_cancellation\":true,\"queue_backpressure\":true,\"explicit_retry\":true,\"owner_model_fences\":true,\"nonwaiting_close\":true,\"cleanup\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One private replay request pump");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual private replay request pump integration\n\nDuplicate desired windows: one owned request\nDesired window change: obsolete decode cancelled\nFull shared queue: backpressure; no wait\nComplete result: actual World data reads succeed\nFailed decode: no auto retry; explicit retry recovers\nInvalid input/owner/model: no stale publication\nClose during blocked decode: no worker join\nSource unchanged; partial startup/save stay blocked\n\nAt most four desired windows; bounded work per tick.\nData readiness is NOT usable mesh readiness.\nNo Main startup, gameplay paging or save promotion.\nCity/GPU/32-client qualification remains pending.\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
