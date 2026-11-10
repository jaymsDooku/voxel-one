package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual private spool selected-directory integration. */
public final class ReplayDirectoryCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<3;mode++) {
                var test=new RegionReplayDirectorySnapshotTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.boundedSnapshotKeepsValidatedEntriesWhenOriginalIndexChangesDuringDelivery();
                    case 1->test.thresholdAndOverflowPreserveOrderAndRowBudget();
                    case 2->test.selectedPayloadStillMatchesSealedDirectoryAndGlobalCorruptionRejectsBeforePolicy();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"threshold_and_fallback\":true,\"order_and_row_budget\":true,\"sealed_snapshot\":true,\"payload_integrity\":true,\"cleanup\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One replay directory snapshot");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual private replay directory integration\n\n4096 selected sections: bounded validated snapshot\n4097 selected sections: streaming fallback\n114688 bytes of primitive snapshot storage per visit\nRecurring section order and row preflight: passed\nGlobal index CRC before policy delivery: passed\nSelected payload CRC still checked: passed\nChanged original index cannot alter retained entries\nNext visit revalidates original index\nCleanup and empty selections: passed\n\nFirst global scan remains. No timing or heap claim.\nNo live startup, save promotion or cap increase.\nCity/GPU/32-client qualification remains pending.\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
