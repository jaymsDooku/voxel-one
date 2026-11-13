package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual validated-file / metadata / target binding integration; no installation. */
public final class ReplaySourceBindingCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<8;mode++) {
                var test=new RegionReplayBindingTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.actualSourceMetadataAndDependenciesBindWithoutMutationOrPromotion();
                    case 1->test.wrongSeedGeneratorLibraryTerrainOrNonFreshTargetRejectWithoutConsumingBinding();
                    case 2->test.foreignMetadataAndRequestsRejectEvenWithIdenticalSourceHashAndTags();
                    case 3->test.metadataModelMutationBeforeBindingAndAfterReadyResultsReject();
                    case 4->test.sourcePathDeletionDoesNotChangeValidatedSnapshotAndStillCannotSavePartialState();
                    case 5->test.sourceTerrainMustMatchWholeFileMetadata();
                    case 6->test.ownerCloseAndCancellationCannotBypassBinding();
                    case 7->test.sourceTerrainChangeDuringDecodeCannotPublishBoundPage();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"actual_saved_file_binding\":true,\"model_resolution\":32,\"foreign_metadata_requests_rejected\":true,\"model_identity_revision_guards\":true,\"source_terrain_race_rejected\":true,\"owner_cancel_close\":true,\"no_world_mutation_or_promotion\":true,\"cleanup\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One private replay source binding");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: saved file / metadata / target binding\n\nValidated source + exact owner model library: matched\n32-resolution dependency / ordered replay: preserved\nForeign metadata, requests and numeric-tag bypass: rejected\nModel identity/revision and non-fresh targets: rejected\nSource terrain mismatch / decode-time change: rejected\nOwner / cancellation / worker close / cleanup: passed\nDetached snapshot after path deletion: retained\nNo target mutation, state promotion or save permission\n\nNo live paging or path compare-and-publish enabled.\nUnloaded coverage and city qualification remain pending.\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
