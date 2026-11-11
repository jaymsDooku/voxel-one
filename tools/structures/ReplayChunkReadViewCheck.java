package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual staged-chunk read-only World integration. */
public final class ReplayChunkReadViewCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<5;mode++) {
                var test=new RegionReplayChunkReadViewTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.completeChunksServeExactWorldQueriesWithoutLiveMeshOrSaveAuthority();
                    case 1->test.evictionAndNegativeCoordinatesNeverFallBackToImplicitTerrain();
                    case 2->test.pendingCancelledAndClosedCoverageStaysUnavailable();
                    case 3->test.ownerModelAndViewIdentityFencesProtectAllReads();
                    case 4->test.failedDecodeNeverGrantsCoverageAndRequiresExplicitRetry();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"fixtures\":5,\"chunk_read_view\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One read-only staged chunk data");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual read-only staged chunk data\n\nAll 4096 source cells match independent WorldVoxels\nFractional LED, custom model and implicit terrain preserved\nOnly complete staged chunks grant data coverage\nLoading, failed and evicted cells never return terrain\nExplicit retry required after failed decode; then succeeds\nNegative coordinates use floor division\nCancellation/close remove coverage without waiting\nOwner, model and World view fences reject stale reads\nReturned cell snapshots are frozen and survive eviction\n\nLive meshes, mutation, streaming and saving remain blocked\nNo Main/GPU, city or 32-client qualification\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
