package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual streamed v10 save staging and normal LocalGame load integration. */
public final class ReplayOverlaySaveCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<4;mode++) {
                var test=new StagedReplayOverlaySaveTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.appendedOverlayPreservesRawOrderAndLoadedFractionalModelGeometry();
                    case 1->test.captureIsImmutableBoundedValidatedAndCancellable();
                    case 2->test.overlayCancellationAndPolicyFailureLeaveOldDestinationIntactThenRetry();
                    case 3->test.combinedExistingPersistenceLimitRejectsBeforeCreatingDestinationPrefix();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"fixtures\":4,\"ordered_overlay_save\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One ordered replay overlay save");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual ordered replay overlay save\n\nBase records followed by exact captured overlay order\nFractional LED, explicit air and custom model data preserved\nNormal loader geometry matches independent WorldVoxels\nImmutable capture: 65,536 rows admitted; 65,537 rejected\nInvalid edits, changed list size and cancellation rejected\nOverlay cancellation/policy failure leave old save intact\nOwned prefix cleanup and clean retry passed\n2,000,000 base rows plus one reject before destination creation\n\nNo dirty-owner integration, source-generation/CAS or sidecars\nNo city, GPU, frame or 32-client qualification\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
