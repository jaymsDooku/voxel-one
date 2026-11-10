package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual worker-private replay bucket integration. */
public final class ReplayBucketsCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<5;mode++) {
                var test=new RegionReplayBucketsTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.indexedRegionsPreserveRecurringOrderAndCopiedStorageIsBounded();
                    case 1->test.selectedBucketIntegrityPrecedesPolicyAndUnselectedCorruptionIsDeferred();
                    case 2->test.preparationCorruptionAndCancellationCleanPrefixesAndAllowRetry();
                    case 3->test.largeMatchingDirectoryFallsBackWithoutWeakeningRowPreflight();
                    case 4->test.failedBucketDeletionRetainsOwnershipAndOwnerThreadRules();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"recurring_order_and_bucket_bounds\":true,\"selected_integrity_before_policy\":true,\"unselected_corruption_deferred\":true,\"prepare_cancel_cleanup_retry\":true,\"large_selection_row_preflight\":true,\"failed_cleanup_ownership\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One private replay metadata buckets");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual private replay metadata bucket integration\n\n96 recurring regions: order preserved across collisions\nAt most 32 files; copied bytes match sealed source index\nSelected bucket CRC passes before policy/payload delivery\nOther-bucket corruption deferred until that bucket is read\nCanceled/invalid preparation: cleanup and retry passed\n4097 matching sections: fallback with row preflight\nFailed deletion stays owned; wrong thread rejected\n\nOne initial global scan; later lookup scans one bucket\nHash collisions may still require a full-size scan\nNo performance, Main startup or save-promotion claim\nCity/GPU/32-client qualification remains pending.\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
