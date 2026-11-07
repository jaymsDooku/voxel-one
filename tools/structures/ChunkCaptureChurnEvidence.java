import java.awt.*;
import java.nio.charset.StandardCharsets;
import javax.swing.*;

/** Fixed synthetic child; no accounts, runner state or runtime logs. */
public final class ChunkCaptureChurnEvidence {
    public static void main(String[] args)throws Exception {
        var command=new ProcessBuilder("java","-Xmx512m","-cp",System.getProperty("java.class.path"),"ChunkCaptureChurnCheck").redirectErrorStream(true).start();
        byte[] bytes=command.getInputStream().readNBytes(16385);if(bytes.length>16384){command.destroyForcibly();throw new IllegalStateException("Child output cap");}
        int exit=command.waitFor();String output=new String(bytes,StandardCharsets.UTF_8);
        String text="Voxel One: chunk capture after synthetic map churn\n\nActual child: java -Xmx512m -cp "+System.getProperty("java.class.path")+" ChunkCaptureChurnCheck\n\nObserved exit: "+exit+"\n\n"+output+"\n\n8192 prior chunks cleared;257 current chunks captured in3 advances.\nOne warmup,20 runs. Each run verifies all final geometry.\nTimings are diagnostic; no full-city/GPU/network qualification.\nLinked iteration avoids retained hash-table scans; memory overhead and\nwall-time/GC/metadata stalls remain unqualified. Rollout stays gated.";
        if(exit!=0)throw new IllegalStateException("Synthetic child failed");
        SwingUtilities.invokeAndWait(()->{var frame=new JFrame("Voxel One chunk churn CLI evidence");var area=new JTextArea(text);area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);area.setFont(new Font(Font.MONOSPACED,Font.PLAIN,14));area.setMargin(new Insets(20,20,20,20));area.setBackground(new Color(18,25,35));area.setForeground(new Color(220,235,245));frame.add(area);frame.setSize(1000,680);frame.setLocation(60,60);frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);frame.setVisible(true);});
    }
}
