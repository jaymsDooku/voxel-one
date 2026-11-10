package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual LocalGame serialization/publication with bounded interleaved writers. */
public final class SaveRowsCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            var test=new LocalGameSaveRowsTest();test.folder=Files.createDirectory(root.resolve("rows"));
            test.inconsistentOrInvalidRowsPreservePublishedWorldAndAllowRetry();
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"formats\":2,\"rejected_cases\":8,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One save row validation");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual LocalGame save row validation\n\nLegacy and component formats tested\nToo few rows: rejected before publication\nToo many rows: rejected before publication\nNull and invalid coordinates: rejected\nPublished world bytes and health preserved\nUnowned legacy scratch preserved\nOwned temporary attempts cleaned\nValid retry reopens with expected geometry\n\nNo format, limit, CAS or city sidecar change\nNo GPU, frame or 32-client qualification\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
