package dev.jayms.net;

import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;

/** Actual World sampling through bounded validated replay windows. */
public final class ReplayReadViewCheck {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
        if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
        try {
            for(int mode=0;mode<7;mode++) {
                var test=new RegionReplayReadViewTest();test.folder=Files.createDirectory(root.resolve("case-"+mode));
                switch(mode) {
                    case 0->test.unknownIsNotImplicitTerrainAndKnownWindowsPreserveGeometryModelsAndColour();
                    case 1->test.emptyNaturalWindowsNegativeCoordinatesAndFifoEvictionNeverFallBackForUnknown();
                    case 2->test.partialOrForeignPublicationsLeaveCoverageUnchanged();
                    case 3->test.readonlyWorldRejectsMutationCaptureHistoryAndStreamingBeforeCallbacks();
                    case 4->test.openAdmissionAndViewIdentityFencesCannotBeBypassed();
                    case 5->test.ownerSourceLifetimeAndModelInvalidationNeverExposeFallbackData();
                    case 6->test.cancelledDecodeDoesNotPublishReadiness();
                }
            }
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        Files.writeString(result,"{\"status\":\"passed\",\"actual_world_sampler_coverage\":true,\"unknown_never_falls_back\":true,\"fractional_colour_and_model32\":true,\"implicit_known_terrain\":true,\"negative_coordinates_fifo\":true,\"atomic_coverage_admission\":true,\"readonly_guards\":true,\"owner_cancel_close\":true,\"cleanup\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One read-only replay coverage");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: World samplers / validated replay windows\n\nUnknown cells: typed unloaded error; no terrain fallback\nKnown data: fractional colour and 32-resolution model IDs\nKnown empty window: correct default terrain\nNegative coordinates / FIFO eviction: passed\nPartial, foreign or cancelled publication: no readiness\nEdit / capture / history / stream / bootstrap gates: closed\nOwner / model invalidation / cleanup: passed\n\nData readiness is NOT mesh readiness.\nNo Main startup, live movement or save promotion enabled.\nPerformance and city qualification remain pending.\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
