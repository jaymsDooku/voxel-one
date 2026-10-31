import java.awt.*;
import java.nio.charset.StandardCharsets;
import javax.swing.*;

/** Displays the actual synthetic CLI result for a sanitized window-only capture. */
public final class RegionGenerationEvidence {
    public static void main(String[] args)throws Exception {
        var command=new ProcessBuilder("java","-Xmx512m","-cp",System.getProperty("java.class.path"),"RegionGenerationCheck","target/structure-generation-evidence").redirectErrorStream(true).start();
        String output=new String(command.getInputStream().readAllBytes(),StandardCharsets.UTF_8);int exit=command.waitFor();
        String text="Voxel One: private region-generation CLI check\n\n"
                +"Actual child command: java -Xmx512m -cp target:target/classes\n"
                +"RegionGenerationCheck target/structure-generation-evidence\n"
                +"Data: isolated synthetic directories; removed after the check.\n\n"
                +"Observed exit code: "+exit+"\n\n"+output+"\n"
                +"This tests private file publication, not a live game save.\n"
                +"Directory fsync, power-loss and full city/GPU/network qualification\n"
                +"remain pending. Production integration is unfinished.";
        SwingUtilities.invokeAndWait(()->{
            var frame=new JFrame("Voxel One generation CLI evidence");var area=new JTextArea(text);
            area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);
            area.setFont(new Font(Font.MONOSPACED,Font.PLAIN,15));area.setMargin(new Insets(22,22,22,22));
            area.setBackground(new Color(18,25,35));area.setForeground(new Color(220,235,245));
            frame.add(area);frame.setSize(960,500);frame.setLocation(80,80);
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);frame.setVisible(true);
        });
    }
}
