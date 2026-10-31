import java.awt.*;
import java.nio.file.*;
import javax.swing.*;

/** Shows the bounded sanitized summary derived from the actual benchmark JSON. */
public final class OwnerCaptureBenchmarkEvidence {
    public static void main(String[] args)throws Exception {
        var child=new ProcessBuilder("python3","tools/structures/summarize_owner_capture.py").redirectErrorStream(true).start();byte[] bytes=child.getInputStream().readNBytes(16*1024+1);if(bytes.length>16*1024){child.destroyForcibly();throw new IllegalArgumentException("Summary budget");}if(child.waitFor()!=0)throw new IllegalStateException("Summary validation failed");String text=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        SwingUtilities.invokeAndWait(()->{var frame=new JFrame("Voxel One owner capture benchmark evidence");var area=new JTextArea(text);area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);area.setFont(new Font(Font.MONOSPACED,Font.PLAIN,15));area.setMargin(new Insets(22,22,22,22));area.setBackground(new Color(18,25,35));area.setForeground(new Color(220,235,245));frame.add(area);frame.setSize(1040,580);frame.setLocation(60,60);frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);frame.setVisible(true);});
    }
}
