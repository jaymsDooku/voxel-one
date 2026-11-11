package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual streamed v10 save staging and normal LocalGame load integration. */
public final class ReadbackOwnerSaveCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<4;mode++) {
                var test=new ReadbackSaveSnapshotTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.ownerCaptureWorkerStageOwnerCommitPreservesTailAndModelsWithoutPromotion();
                    case 1->test.changedHealthInventoryDropsModelsAndWorldRejectBeforePublication();
                    case 2->test.sameDigestForeignSourceAndChangedTerrainCannotStageThisCapture();
                    case 3->test.wrongOwnerCannotConsumePrefixAndCancellationCleansOnlyOwnedStage();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"fixtures\":4,\"readback_owner_save\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One readback owner save snapshot");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual readback owner save snapshots\n\nOwner capture -> worker staging -> guarded owner commit\nSaved models, inventory, health and drops preserved\nPartial LocalGame authority and World geometry unchanged\nHealth/inventory/drops/model/World changes reject commit\nSame-digest foreign source generation rejected\nChanged worker terrain rejected before staging\nWrong-owner call leaves prefix for owner; then commits\nCancellation cleans own prefix; unchanged retry succeeds\n\nOwner commit blocks: no Main/frame-time integration\nNo cross-process CAS, atomic sidecars or city qualification\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
