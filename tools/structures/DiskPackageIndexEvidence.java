import java.awt.*;
import java.nio.charset.StandardCharsets;
import javax.swing.*;

/** Shows actual fixed-child diagnostic output, never a game UI. */
public final class DiskPackageIndexEvidence {
    public static void main(String[] args)throws Exception {
        var child=new ProcessBuilder("java","-Xmx256m","-cp",System.getProperty("java.class.path"),"DiskPackageIndexCheck").redirectErrorStream(true).start();
        byte[] bytes=child.getInputStream().readNBytes(16385);if(bytes.length>16384){child.destroyForcibly();throw new IllegalStateException("Child output cap");}
        int exit=child.waitFor();String output=new String(bytes,StandardCharsets.UTF_8);if(exit!=0)throw new IllegalStateException("Diagnostic failed: "+output);
        String text="Voxel One: read-only disk package index diagnostic\n\nActual child: java -Xmx256m -cp <compiled check + production classes>\nDiskPackageIndexCheck\nObserved exit: "+exit+"\n\n"+output+"\n\nFive cells in three spatial regions roundtrip against production encoding.\nModel reload, frozen fractional geometry and cache cleanup pass.\n18 rejection cases cover malformed input, CRC, order, bounds, references,\ncancellation, closed handles and corrupt cache reads before callbacks.\n\nDecoded cache lives on disk; only model/region offsets stay indexed.\nVisitor reads one cell at a time. No live world is changed.\n\nPrivate diagnostic only. Production importer and limits unchanged.\nFull city/maximum, physical GPU and 32-client qualification pending.";
        SwingUtilities.invokeAndWait(()->{var frame=new JFrame("Voxel One disk index CLI evidence");var area=new JTextArea(text);area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);area.setFont(new Font(Font.MONOSPACED,Font.PLAIN,15));area.setMargin(new Insets(20,20,20,20));area.setBackground(new Color(18,25,35));area.setForeground(new Color(220,235,245));frame.add(area);frame.setSize(1000,540);frame.setLocation(60,60);frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);frame.setVisible(true);});
    }
}
