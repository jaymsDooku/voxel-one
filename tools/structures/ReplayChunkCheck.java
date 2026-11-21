package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual LocalGame serialization/publication with bounded interleaved writers. */
public final class ReplayChunkCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<3;mode++) {
                var test=new RegionReplayChunkTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.fullNegativeChunkMatchesIndependentGeometryAndOutlivesCache();
                    case 1->test.geometryAdmissionCancellationAndRetryDoNotPublishPartialResults();
                    case 2->test.terrainRevisionAndOwnerChangesRejectBeforeReturn();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"fixtures\":3,\"independent_cell_comparisons\":4096,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One detached replay chunk geometry");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: detached replay chunk CPU geometry\n\n4096 negative-coordinate cells match independent replay\nImplicit terrain, fractional glass and coloured LED retained\nModel reference ids retained; definitions remain external\nFrozen result survives cache and spool cleanup\nGeometry budget and cancellation reject without a result\nTerrain revision and foreign owner changes rejected\nValid retry succeeds after rejected assembly\n\nOne cell page retained at a time; no global history copy\nNo live installation, meshes, save or gameplay authority\nNo city, GPU, frame or 32-client qualification\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
