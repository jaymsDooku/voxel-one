package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual one-pass replay counting-copy integration. */
public final class ReplayCountingCopyCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<4;mode++) {
                var test=new RegionReplayCountingCopyTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.corruptedCountingCopyNeverTransfersCandidateAndRetryCleansBothFiles();
                    case 1->test.validatedCopyOutlivesLaterSourcePayloadChangeWithoutGrantingSourceAuthority();
                    case 2->test.failedScratchDeletionStaysOwnedAndBlocksNewBuildUntilCleanup();
                    case 3->test.scratchOwnershipAndSuccessfulTransferAreExplicit();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"counting_copy_crc_and_order\":true,\"single_source_pass\":true,\"detached_snapshot\":true,\"failed_cleanup_retains_ownership\":true,\"explicit_transfer\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One replay counting copy");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual replay counting-copy integration\n\nOne validated source pass, then scratch-file scatter\nValid-row corruption / truncation / tail / order: rejected\nCopied rows survive later source payload changes\nNext build still validates the source\nFailed deletion: ownership retained; new build blocked\nExplicit transfer: copied root outlives source cleanup\nWorker-only scratch ownership and retry: passed\n\nExtra raw scratch: at most 56000000 logical bytes.\nNo timing or peak-memory claim; source caps unchanged.\nNo Main startup, live paging or save promotion.\nCity/GPU/32-client qualification remains pending.\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
