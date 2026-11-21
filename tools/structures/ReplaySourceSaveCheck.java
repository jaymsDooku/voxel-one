package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual streamed v10 save staging and normal LocalGame load integration. */
public final class ReplaySourceSaveCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<4;mode++) {
                var test=new StagedReplaySourceSaveTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.streamedSourceMatchesResidentWriterBytesModelsAndSurvival();
                    case 1->test.recurringRegionOverwritesAndExplicitAirRetainExactSourceOrder();
                    case 2->test.cancellationPolicyAndProgressFailurePreserveOldDestinationAndAllowRetry();
                    case 3->test.lateCorruptionRejectsPrivatePrefixAndEmptySourceStillRoundTrips();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"fixtures\":4,\"streamed_save_prefix\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One streamed replay save prefix");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual streamed replay save staging\n\nStreamed v10 output matches resident writer byte-for-byte\nCustom 32-resolution model and survival/drop bytes preserved\nRecurring region overwrite and explicit air order preserved\nOne bounded replay section; no resident history required\nCancellation, policy/progress failure and late CRC reject\nOld destination stays intact; owned prefix files cleaned\nAbandoned stage leaves destination intact\nClean retry and empty source round-trip succeed\n\nNo partial-world save authority, dirty overlay or CAS\nNo city, GPU, frame or 32-client qualification\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
