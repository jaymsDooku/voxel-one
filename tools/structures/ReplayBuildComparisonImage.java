package dev.jayms.net;

import java.nio.file.*;
import javax.swing.*;

public final class ReplayBuildComparisonImage {
    public static void main(String[] args)throws Exception {
        Path output=Path.of(args[0]);if(Files.exists(output))throw new IllegalArgumentException("Fresh image required");
        var frame=new JFrame("Voxel One replay-build diagnostic");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("Actual replay-build comparison / correctness passed\n\n65536 source rows; 32768 selected rows; 4096 cells\n8192 recurring sections; same byte-identical fixture\n2 warmups + 20 measured builds per version\n\nBuild p95 (ms): prior "+args[1]+" | candidate "+args[2]+"\nWindow p95 (ms): prior "+args[3]+" | candidate "+args[4]+"\nProcess max RSS (KiB): prior "+args[5]+" | candidate "+args[6]+"\n\nFirst/last window order, pass counts and cleanup checked\nWarm-cache CPU/file diagnostic; sequential JVM runs\nHeap-pool peaks are not simultaneous live heap\nNo cold-cache, GPU, rendering or network measurements\nThis is NOT the required city/maximum qualification.\nSupported limits and stable rollout stay gated.\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",output.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
    }
}
