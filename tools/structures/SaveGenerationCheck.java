package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual streamed v10 save staging and normal LocalGame load integration. */
public final class SaveGenerationCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<4;mode++) {
                var test=new WorldSaveGenerationTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.sameGenerationConcurrentPublishersHaveExactlyOneWinner();
                    case 1->test.ordinaryPublicationInvalidatesEvenIdenticalBytesAndWrongTargetCannotUseToken();
                    case 2->test.guardedStagedSaveRejectsStaleCommitCleansPrefixAndRetries();
                    case 3->test.boundedCaptureAbsenceObservedChangesAndCancellationNeverPublish();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"fixtures\":4,\"save_generation_guard\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One same-process save generation guards");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual same-process save generation guards\n\nTwo concurrent writers, one token: exactly one complete winner\nOrdinary replacement invalidates old token, even identical bytes\nWrong-target token rejected before publication\nStale staged save rejected; newer destination preserved\nOwned prefix cleanup and fresh-token retry passed\nBounded hash, absent destination and cancellation checked\nCompleted foreign byte change detected with restored mtime\n\n64 stripes may conservatively reject unrelated publication\nNo cross-process/alias-proof CAS or atomic sidecar commit\nNo city, GPU, frame or 32-client qualification\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
