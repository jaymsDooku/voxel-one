package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual LocalGame serialization/publication with bounded interleaved writers. */
public final class ReplayMeshCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<4;mode++) {
                var test=new RegionReplayMeshTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.diskReplayMeshMatchesIndependentMaterialsColoursWaterAndModelExclusion();
                    case 1->test.cancellationAndPersistentCaptureDoNotMutateOrFreezeCaller();
                    case 2->test.existingMeshBudgetRejectsFragmentationThenAllowsCleanRetry();
                    case 3->test.boundWorkerReturnsCpuMeshWithoutLiveInstallationOrSavePromotion();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"fixtures\":4,\"cpu_mesh\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One detached replay CPU mesh");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual detached replay CPU mesh integration\n\nVertices, indices and surface data match independent replay\nFractional glass, LED colour and water preserved\nCustom model ids retained; model draw staging separate\nCancellation remains cancellation; retry is clean\nExisting fragmented mesh budget still rejects excess\nPersistent capture does not freeze the caller wrapper\nBound worker returns CPU mesh; saved bytes unchanged\nGeometry survives worker/source cleanup\n\nConservative air borders need live neighbour fences\nNo World/Main/GL activation or city/GPU/network qualification\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
