package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Exercise real World objects and the production private replay worker. */
public final class ReplayWorldFenceCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<5;mode++) {
                var test=new RegionReplayFenceTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.sameWorldReturnsDetachedPageWithoutMutationAndCannotBypassFence();
                    case 1->test.geometryModelTerrainAndCloseInvalidateReadyResults();
                    case 2->test.replacingModelOrTerrainIdentityWithMatchingNumbersInvalidates();
                    case 3->test.residentGeometryChangesAndWrongThreadInvalidate();
                    case 4->test.unfencedRequestsDoNotClaimWorldValidationAndClosedTargetsCannotSubmit();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"actual_world_identity_revision_fences\":true,\"tag_only_bypass_rejected\":true,\"owner_checks\":true,\"closed_worker_rejected\":true,\"detached_result_no_installation\":true,\"cleanup\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One replay world-fence integration");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual World / replay worker integration\n\nSame World: detached page returned; no world mutation\nTag-only bypass / same-number replacement World: rejected\nEdit / resident geometry / models / terrain / close: rejected\nReplacement model or terrain identity: rejected\nWrong owner / closed worker / unfenced extraction: rejected\nPrivate spool and worker cleanup: passed\n\nNo page installation or live paging enabled.\nSource generations, dependency matching and unloaded-cell\nrules remain separate requirements. No performance claim.\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
