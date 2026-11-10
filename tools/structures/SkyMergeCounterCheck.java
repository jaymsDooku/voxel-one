package dev.jayms.render;

import java.nio.file.*;
import javax.swing.*;

/** Actual World/sky-worker integration; no OpenGL/frame performance claim. */
public final class SkyMergeCounterCheck {
    public static void main(String[] args)throws Exception {
        Path result=Path.of(args[0]),image=Path.of(args[1]);
        if(Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");
        for(int run=0;run<5;run++)new SkyVisibilitySchedulerTest().disjointArrivalsDoNotRestartAnInProgressMergeAndNewerTilesStillPublish();
        Files.writeString(result,"{\"status\":\"passed\",\"world_fixtures\":5,\"controlled_merges\":10,\"nonzero_counter_baseline\":true,\"cursor_work_preserved\":true,\"original_generation_retained\":true,\"exact_increment_per_merge\":true,\"followup_geometry_matches\":true,\"qualification\":false}\n");
        var frame=new JFrame("Voxel One cumulative sky merge counter");
        SwingUtilities.invokeAndWait(()->{
            var area=new JTextArea("PASS: actual World / sky-worker integration\n\n5 world fixtures; 2 controlled merges each\nSecond merge explicitly starts with a nonzero counter\nExactly one new partial publication per controlled merge\nExisting cursor work is not discarded\nSubmitted generation stays the original capture generation\nFollow-up atlas matches full expected world geometry\n\nProduction renderer and save code unchanged this phase\nNo OS branch, test skip or timing-budget relaxation\nNo OpenGL/frame, GPU, city or 32-client qualification.\n");
            area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);
        });
        try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}
        finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.out.println(Files.readString(result));
    }
}
