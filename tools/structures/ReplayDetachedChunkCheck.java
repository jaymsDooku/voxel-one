package dev.jayms.net;
import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;
public final class ReplayDetachedChunkCheck {
 public static void main(String[] args)throws Exception {
  Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
  if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
  try {
   var test=new dev.jayms.ReplayChunkPreparationTest();
   test.geometryModelsFractionalAndLedMatchOrdinaryReplayWithoutGl();
   test.persistentCaptureKeepsInputAndPreparedEditsIndependent();
   test.cancellationBadResolutionAndLeafAdmissionRejectBeforeExposure();
   for(int i=0;i<2;i++) {
    var worker=new RegionReplayDetachedChunkTest();worker.folder=Files.createDirectory(root.resolve("case-"+i));
    if(i==0)worker.boundWorkerTransfersIndependentChunkAndRejectsOwnerMutation();
    else worker.attachmentAndPrematureMeshInstallationInvalidateReceipt();
   }
  }finally{try(var paths=Files.walk(root)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
  Files.writeString(result,"{\"status\":\"passed\",\"fixtures\":5,\"detached_chunk_preparation\":true,\"qualification\":false}\n");
  var frame=new JFrame("Voxel One detached replay Chunk preparation");
  SwingUtilities.invokeAndWait(()->{var area=new JTextArea("PASS: detached replay Chunk preparation\n\n4096 cells match ordinary replay geometry\nModel maps, fractional flags and colored LEDs preserved\nPersistent copies keep input and prepared edits separate\nCancellation, bad size/material and leaf budget reject\nEmpty zero-budget chunk and unchanged retry pass\nBound worker returns CPU Chunk with no World or GL mesh\nOwner mutation invalidates detached result reuse\nAttachment or early mesh install invalidates receipt\nLive World/history/save authority remain unchanged\n\nExisting 131072-leaf per-chunk cap retained\nNo GPU upload, Main, city or network qualification\n");area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",0,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);});
  try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}finally{SwingUtilities.invokeAndWait(frame::dispose);}
  System.out.println(Files.readString(result));
 }
}
