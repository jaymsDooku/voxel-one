package dev.jayms.net;
import java.nio.file.*;
import java.util.Comparator;
import javax.swing.*;
public final class StagedSaveDropCountCheck {
 public static void main(String[] args)throws Exception {
  Path root=Path.of(args[0]),result=Path.of(args[1]),image=Path.of(args[2]);
  if(Files.exists(root)||Files.exists(result)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");
  Files.createDirectory(root);
  try {var test=new StagedSaveDropCountTest();test.folder=root;test.invalidDeclaredCountsAndChangingRowsLeaveOldSaveIntact();}
  finally{try(var paths=Files.walk(root)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
  Files.writeString(result,"{\"status\":\"passed\",\"invalid_cases\":6,\"valid_retry\":true,\"qualification\":false}\n");
  var frame=new JFrame("Voxel One staged save drop counts");
  SwingUtilities.invokeAndWait(()->{var area=new JTextArea("PASS: staged v10 save drop count integration\n\nNegative and over-limit counts rejected\nExtra, missing and null rows rejected\nAll six failures preserve original save bytes\nPrivate staged prefixes removed after rejection\nValid retry loads drops, seed and health\n\nCLI persistence check; no browser workflow\nNo Main, GPU, city or network qualification\n");area.setEditable(false);area.setFont(new java.awt.Font("Monospaced",0,16));area.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));frame.add(area);frame.pack();frame.setLocation(40,40);frame.setVisible(true);});
  try{var robot=new java.awt.Robot();robot.waitForIdle();robot.delay(300);javax.imageio.ImageIO.write(robot.createScreenCapture(frame.getBounds()),"png",image.toFile());}finally{SwingUtilities.invokeAndWait(frame::dispose);}
  System.out.println(Files.readString(result));
 }
}
