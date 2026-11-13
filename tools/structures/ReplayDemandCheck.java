package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual saved-world/worker/ray demand integration using synthetic profiles. */
public final class ReplayDemandCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<4;mode++) {
                var test=new RegionReplayDemandTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.actualRayDemandLoadsDataWithoutMeshOrAuthorityPromotion();
                    case 1->test.negativeCellsMapToTheCorrectRegionAndAlignedWindow();
                    case 2->test.invalidAndOverBudgetHintsDoNotCancelExistingWork();
                    case 3->test.staleReadyHintReloadsAfterEvictionInsteadOfTrustingItsFlag();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"saved_world_ray_worker_data\":true,\"unmeshed_boundary_still_blocks\":true,\"negative_coordinates\":true,\"bounded_atomic_demand_preflight\":true,\"stale_ready_hint_reload\":true,\"owner_fence\":true,\"save_authority_not_promoted\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One bounded ray replay demand");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual saved-world / worker / ray demand\n\nRay boundary -> desired window -> worker -> ready data\n64 duplicate cell hints produce one window, not 64 requests\nNegative cells select the correct region/aligned window\nInvalid/null/over-cap hints preserve existing active work\nForeign owner rejected; empty demand clears desired windows\nStale ready flag after eviction does not suppress reload\n\nReady data remains unmeshed: ray/collision still block\nOriginal save bytes unchanged; partial save still rejected\nOwner demand preflight submits no work until explicit tick\nMain partial startup and live gameplay remain disabled\nNo frame/tick/GPU or city/32-client qualification.\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
