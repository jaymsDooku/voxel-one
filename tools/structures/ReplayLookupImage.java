package dev.jayms.net;

import java.nio.file.*;
import javax.swing.*;

/** Capture measured diagnostic text on the inherited role display. */
public final class ReplayLookupImage {
    public static void main(String[] args)throws Exception {
        Path text=Path.of(args[0]),output=Path.of(args[1]);
        if(Files.exists(output))throw new IllegalArgumentException("Fresh image required");
        String measured=Files.readString(text);var frame=new JFrame("Voxel One replay lookup diagnostic");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea(measured);area.setEditable(false);
            area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));
            area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
            frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",output.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
    }
}
