package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Run actual integration fixtures; explicit denial injection is not Windows execution. */
public final class ReadbackReplacementCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");
        Files.createDirectory(root);boolean filesystemDenied;
        try {
            var actual=new ReadbackFileChangeTest();actual.folder=Files.createDirectory(root.resolve("filesystem"));
            filesystemDenied=actual.replacement(false);
            var injected=new ReadbackFileChangeTest();injected.folder=Files.createDirectory(root.resolve("injected"));
            if(!injected.replacement(true))throw new AssertionError("Injected denial not reached");
            var unchanged=new ReadbackFileChangeTest();unchanged.folder=Files.createDirectory(root.resolve("regression"));
            unchanged.unchangedSourceAndPostOpenReplacementRetainDetachedSnapshot();
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        String outcome=filesystemDenied?"denied_without_source_change":"replaced_and_readback_rejected";
        Files.writeString(result,"{\"status\":\"passed\",\"filesystem_replacement\":\""+outcome+"\",\"injected_access_denied\":true,\"unchanged_source_regression\":true,\"cleanup\":true,\"windows_execution\":"+System.getProperty("os.name").startsWith("Windows")+",\"qualification\":false}\n");
        var frame=new JFrame("Voxel One readback replacement integration");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: replacement fixture portability\n\nActual filesystem: "+outcome+"\nInjected AccessDeniedException branch: passed\nDenied attempt preserves bytes, hash, metadata and replay\nRetry after reader close: passed\nUnchanged-source / detached-snapshot regression: passed\nPrivate scratch cleanup: passed\n\nSynthetic denial is NOT Windows execution.\nNew exact-head Windows CI is still required.\nNo production behavior or qualification gates changed.\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
