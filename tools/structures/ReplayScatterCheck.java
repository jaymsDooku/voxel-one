package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual bounded replay scatter integration. */
public final class ReplayScatterCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<2;mode++) {
                var test=new RegionReplayScatterTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.allWindowsRetainOriginalOrderAcrossPageSplitsEvictionsAndReuse();
                    case 1->test.cancellationAfterDirtyPageFlushDeletesCandidateAndRawCopyAndCanRetry();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"all_64_windows\":true,\"page_boundary_eviction_reuse\":true,\"cancel_after_partial_flush\":true,\"cleanup_and_retry\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One bounded replay scatter");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual bounded replay scatter integration\n\n32768 rows; all 64 windows match original source order\n28-byte records crossing 64 KiB pages: preserved\nPage eviction and array reuse: preserved\nCancellation after dirty-page flush: no root transfer\nRaw/candidate cleanup and retry: passed\n\nFour reusable 64 KiB pages: 262144 array bytes\nExisting scratch CRC and ownership rules remain\nPerformance measured separately; no qualification claim\nNo Main startup, live paging or save promotion\nCity/GPU/32-client qualification remains pending.\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
