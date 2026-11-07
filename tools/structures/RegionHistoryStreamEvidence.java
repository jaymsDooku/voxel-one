import java.awt.*;
import java.nio.charset.StandardCharsets;
import javax.swing.*;

/** Fixed synthetic stream integration, no live game or account files. */
public final class RegionHistoryStreamEvidence {
    public static void main(String[] args)throws Exception {
        var command=new ProcessBuilder("java","-Xmx512m","-cp",System.getProperty("java.class.path"),"RegionHistoryStreamCheck").redirectErrorStream(true).start();
        byte[] bytes=command.getInputStream().readNBytes(16385);if(bytes.length>16384){command.destroyForcibly();throw new IllegalStateException("Child output cap");}
        int exit=command.waitFor();String output=new String(bytes,StandardCharsets.UTF_8);if(exit!=0)throw new IllegalStateException("Synthetic child failed: "+output);
        String text="Voxel One: bounded region stream CLI check\n\nActual child: java -Xmx512m -cp "+System.getProperty("java.class.path")+" RegionHistoryStreamCheck\n\nObserved exit: "+exit+"\n\n"+output+"\n\nVersion1 bytes unchanged; short reads and file restart preserve\nfractional geometry and explicit air. Caller streams stay open.\nCRC/trailing/truncated/oversize input and cancellation reject.\nAt most byte cap plus one trailing-data probe is consumed.\nFailed output may contain a prefix: stage before publication.\n\nPrivate codec only. Existing caps and live backend unchanged.\nFull city/maximum,GPU,32-client and durability qualification pending.";
        SwingUtilities.invokeAndWait(()->{var frame=new JFrame("Voxel One region stream CLI evidence");var area=new JTextArea(text);area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);area.setFont(new Font(Font.MONOSPACED,Font.PLAIN,15));area.setMargin(new Insets(20,20,20,20));area.setBackground(new Color(18,25,35));area.setForeground(new Color(220,235,245));frame.add(area);frame.setSize(1000,520);frame.setLocation(60,60);frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);frame.setVisible(true);});
    }
}
