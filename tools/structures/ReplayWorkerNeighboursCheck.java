package dev.jayms.net;
import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;
public final class ReplayWorkerNeighboursCheck {
 public static void main(String[] args)throws Exception {
  Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
  if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
  try {
   for(int i=0;i<5;i++) {
    var test=new RegionReplayWorkerNeighboursTest();test.folder=Files.createDirectory(root.resolve("case-"+i));
    switch(i) {
     case 0->test.allSixMaskBitsUseSameSourceAndDefaultKeepsUnknownFaces();
     case 1->test.invalidMasksAndWorldEdgesRejectBeforeQueueAndCancellationFreesSharedSlot();
     case 2->test.cancellationDuringNeighbourReadNeverPublishesAndUnchangedRetryWorks();
     case 3->test.failedNeighbourPolicyCannotPublishCenterOrPoisonWorker();
     case 4->test.sourceTerrainChangeDuringNeighbourLoadRejectsCompleteCenter();
    }
   }
  }finally{try(var paths=Files.walk(root)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
  Files.writeString(result,"{\"status\":\"passed\",\"fixtures\":5,\"same_source_neighbour_worker\":true,\"qualification\":false}\n");
  var frame=new JFrame("Voxel One replay worker neighbours");
  SwingUtilities.invokeAndWait(()->{var area=new JTextArea("PASS: actual replay worker neighbour meshing\n\nDefault + six individual masks + all faces tested\nNegative chunk positions; CPU mesh faces culled exactly\nSame-source geometry unchanged; no live World install\nInvalid masks/world edges reject before queueing\nShared queue bound; cancellation frees queued slot\nCancel during neighbour read; no late result; retry passes\nNeighbour policy failure rejects center; retry passes\nOwner World edit invalidates result extraction\nSource terrain change rejects fully staged center\n\nAt most 6 extra roots; existing per-chunk limits unchanged\nNo Main, GL/GPU, city or network qualification\n");area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",0,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);});
  try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}finally{SwingUtilities.invokeAndWait(frame::dispose);}
  System.out.println(Files.readString(result));
 }
}
