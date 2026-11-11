package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual detached CPU mesh neighbour integration. */
public final class ReplayNeighboursCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<3;mode++) {
                var test=new RegionReplayMeshNeighboursTest();
                switch(mode) {
                    case 0->test.sixFacesCullExactlyAndUnknownNeighboursKeepExteriorFaces();
                    case 1->test.malformedNeighbourSetsRejectBeforeProducingMeshAndRetryWorks();
                    case 2->test.capturesNeighbourRootWithoutFreezingCallerAndCancelsSampling();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"fixtures\":3,\"neighbour_mesh\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One detached mesh neighbours");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual detached mesh neighbour sampling\n\nAll six shared chunk faces culled in sequence\nUnknown exterior remains visible\nPartial cells and model references do not hide full faces\nExisting water visibility rule preserved\nDuplicate, diagonal, far and excess neighbours rejected\nResolution and terrain revision mismatches rejected\nCancellation stops sampling; clean retry passes\nNeighbour root copied; caller wrapper stays editable\n\nCPU mesh only: no live World/GPU installation\nNo live neighbour freshness or city qualification\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
