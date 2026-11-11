package dev.jayms.net;
import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;
public final class ReplayMeshFenceCheck {
 public static void main(String[] args)throws Exception {
  Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
  if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
  try {
   var test=new dev.jayms.ReplayMeshFenceTest();
   test.absentCenterAndEachNeighborLoadingInvalidatesOnlyMeshReceipt();
   test.meshOnlyInvalidationAndResidentReplacementRejectOldReceipt();
   test.distantNaturalResidencyDoesNotInvalidateAndForeignOwnerCannotValidate();
   test.canonicalGeometryTerrainModelsAndClosedWorldRetainBaselineChecks();
   for(int i=0;i<2;i++) {
    var worker=new RegionReplayWorkerMeshFenceTest();worker.folder=Files.createDirectory(root.resolve("case-"+i));
    if(i==0)worker.readyChunkRejectsAdjacentResidencyButDistantChangesAndPagesRemainValid();
    else worker.adjacentChangeWhileQueuedRejectsLateResultWithoutMutatingWorld();
   }
  }finally{try(var paths=Files.walk(root)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
  Files.writeString(result,"{\"status\":\"passed\",\"fixtures\":6,\"resident_mesh_fence\":true,\"qualification\":false}\n");
  var frame=new JFrame("Voxel One replay resident mesh fence");
  SwingUtilities.invokeAndWait(()->{var area=new JTextArea("PASS: replay resident mesh dependency fences\n\nCenter + six face positions captured on owner\nAbsent -> loaded rejects old mesh receipt\nMesh-only invalidation rejects unchanged geometry\nUnload/replacement and same-wrapper reattach reject\nDistant natural residency remains valid\nWrong thread/World, edit, terrain, model and close reject\nReady and queued worker results reject adjacent changes\nCell-page extraction remains valid for residency changes\nFresh unchanged chunk retry succeeds\n\nSeven references; no geometry copy or worker World reads\nNo installation/readiness, GL/GPU or city qualification\n");area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",0,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);});
  try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}finally{SwingUtilities.invokeAndWait(frame::dispose);}
  System.out.println(Files.readString(result));
 }
}
