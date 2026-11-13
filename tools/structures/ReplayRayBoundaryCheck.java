package dev.jayms;

import java.nio.file.*;
import javax.swing.*;

/** Exercise actual World/ray/collision integration using synthetic fixtures. */
public final class ReplayRayBoundaryCheck {
    public static void main(String[] args)throws Exception {
        Path result=Path.of(args[0]),image=Path.of(args[1]);
        if(Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");
        var test=new ReplayRayBoundaryTest();
        test.unknownReplayStopsAtFlooredCellWithoutSamplingAndCollisionStaysSolid();
        test.knownReplayDataStillStopsUntilResidentGeometryExists();
        test.residentHitAndMissKeepLegacyResultsAndStopAtFirstMissingChunk();
        test.invalidTraceInputsReturnBeforeWorldAccess();
        Files.writeString(result,"{\"status\":\"passed\",\"unknown_replay_boundary\":true,\"data_ready_not_mesh_ready\":true,\"resident_hit_miss_and_first_unloaded\":true,\"invalid_inputs\":true,\"legacy_cast_unchanged\":true,\"collision_boundary_solid\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One replay ray boundary");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual World / ray / collision integration\n\nUnknown replay cell: exact negative coordinates reported\nNo geometry sampling beyond the unavailable boundary\nReady replay data still requires resident geometry\nResident hit and miss match the legacy cast result\nFirst missing chunk stops traversal; no hit through it\nInvalid inputs return before World access\nLegacy cast does not add a data-readiness query\nCollision remains a solid loading boundary\n\nOwner-side trace only: no I/O, requests or publication\nMain partial startup remains disabled\nNo frame/tick/GPU or city/32-client qualification.\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
