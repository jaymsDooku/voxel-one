import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Captures only its own sanitized integration-result window, never the desktop.
 * This is CLI evidence, not a rendered game-client or GPU qualification. */
public final class StructureNetworkEvidence {
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("summary.txt output.png");
        byte[] bytes;
        try(var in=Files.newInputStream(Path.of(args[0]))) {bytes=in.readNBytes(16*1024+1);}
        if(bytes.length>16*1024)throw new IllegalArgumentException("Summary size limit");
        String text=new String(bytes,StandardCharsets.UTF_8);
        var frame=new JFrame("Voxel One: real TLS integration result");
        try {
            SwingUtilities.invokeAndWait(()->{
                var area=new JTextArea(text);area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);
                area.setFont(new Font(Font.MONOSPACED,Font.PLAIN,16));area.setMargin(new Insets(20,20,20,20));
                area.setBackground(new Color(18,25,35));area.setForeground(new Color(220,235,245));
                frame.add(area);frame.setSize(1000,680);frame.setLocation(40,40);frame.setVisible(true);
            });
            var robot=new Robot();robot.waitForIdle();Toolkit.getDefaultToolkit().sync();
            Rectangle[] bounds={null};
            SwingUtilities.invokeAndWait(()->bounds[0]=new Rectangle(frame.getLocationOnScreen(),frame.getSize()));
            if(!ImageIO.write(robot.createScreenCapture(bounds[0]),"png",Path.of(args[1]).toFile()))
                throw new IllegalStateException("PNG encoder unavailable");
        }finally{SwingUtilities.invokeAndWait(frame::dispose);}
    }
}
