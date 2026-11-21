package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual streamed v10 save staging and normal LocalGame load integration. */
public final class ReadbackSaveGenerationCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<4;mode++) {
                var test=new ReadbackSaveGenerationTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.actualReadbackGenerationGuardsStreamedOverlayPublicationAndNormalReload();
                    case 1->test.laterOrdinarySaveRejectsReadbackTokenAndRetainsNewSave();
                    case 2->test.matchingCaptureRejectsWrongMissingAndMalformedSourceDigests();
                    case 3->test.bufferedSameSizeRewriteWithRestoredAttributesCannotReturnSourceMetadata();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"fixtures\":4,\"readback_save_generation\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One replay source generation binding");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual replay source generation binding\n\nDecoded v10 digest matches bounded destination capture\nTransferred partial metadata carries matching save token\nGuarded streamed overlay save and normal reload passed\nPartial LocalGame still rejects ordinary save\nLater ordinary save invalidates readback token\nWrong, absent and malformed source digests rejected\nBuffered same-size rewrite with restored mtime rejected\nFailed readback cleans spool; unchanged retry succeeds\n\nJVM-only: no foreign writer exclusion or immutable manifest\nNo Main/owner commit, atomic sidecars or city qualification\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
