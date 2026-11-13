package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual LocalGame serialization/publication with bounded interleaved writers. */
public final class SaveIsolationCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<3;mode++) {
                var test=new LocalGameSaveIsolationTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.failedOverlappingWriterCannotRewritePublishedSiblingOrLegacyScratch();
                    case 1->test.successfulOverlappingAttemptsPublishOnlyTheirOwnCompleteFile();
                    case 2->test.failedPublicationCleansOnlyItsOwnAttemptAndPreservesTargetContents();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"failed_writer_preserves_published_bytes\":true,\"legacy_scratch_untouched\":true,\"successful_overlapping_files_complete\":true,\"publication_failure_cleanup\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One independent save staging");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual LocalGame save integration\n\nA fails after B publishes: B bytes remain unchanged\nLegacy shared .tmp sentinel remains untouched\nSuccessful overlap: each published file loads completely\nLast writer behavior remains; no generation CAS added\nDenied publication preserves existing target contents\nOwned attempt files removed after success/failure\n\nFile flush/force and atomic-move policy retained\nFormats, row limits and partial-save guard unchanged\nMain/native video is historical for this save change\nNo city, crash-durability, GPU or 32-client qualification.\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
