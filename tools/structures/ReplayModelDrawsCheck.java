package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual LocalGame serialization/publication with bounded interleaved writers. */
public final class ReplayModelDrawsCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<4;mode++) {
                var test=new RegionReplayModelDrawsTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.sharedBatchPreservesDefinitionAndLocalPlacementKeysAcrossCells();
                    case 1->test.aggregateInstanceSurfaceBudgetRejectsBeforePublishingAndAllowsBoundedRetry();
                    case 2->test.cancellationAndMutableDefinitionCaptureDoNotPublishOrFreezeCaller();
                    case 3->test.boundWorkerRestoresCustomDrawDataAfterMetadataTransferWithoutPromotion();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"fixtures\":4,\"model_draws\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One detached model draw batches");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual detached model draw staging\n\nShared CPU batch keeps exact model geometry and cell keys\n8 and 32 resolution, colours and references preserved\nAggregate instance surfaces checked before mesh emission\n170 checker instances admitted;171 rejected at existing cap\nMissing dependencies and cancellation reject without result\nCaptured definitions frozen; caller wrapper remains editable\nWorker uses source definitions after metadata transfer\nOwner model changes invalidate extraction; saved bytes stable\n\nCPU buffers only: no GPU, World/Main or save promotion\nNo city, frame, physical-GPU or 32-client qualification\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
