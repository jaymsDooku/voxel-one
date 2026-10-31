import java.awt.*;
import java.nio.charset.StandardCharsets;
import javax.swing.*;

/** Fixed synthetic private-backup integration; no live profiles or accounts. */
public final class WorldOverrideCursorEvidence {
    public static void main(String[] args)throws Exception {
        var child=new ProcessBuilder("java","-Xmx512m","-cp",System.getProperty("java.class.path"),"WorldOverrideCursorCheck").redirectErrorStream(true).start();
        byte[] bytes=child.getInputStream().readNBytes(16385);if(bytes.length>16384){child.destroyForcibly();throw new IllegalStateException("Child output cap");}
        int exit=child.waitFor();String output=new String(bytes,StandardCharsets.UTF_8);if(exit!=0)throw new IllegalStateException("Synthetic child failed: "+output);
        String text="Voxel One: stored-world override cursor and private backup\n\nActual child: java -Xmx512m -cp "+System.getProperty("java.class.path")+" WorldOverrideCursorCheck\n\nObserved exit: "+exit+"\n\n"+output+"\n\n2054 stored cells, including fractional LED, explicit air and a custom\nmodel, restart with empty flat history. World-only stale commit rejects;\nretry saves current glass geometry. Legacy source file stays absent.\n\nWorker cursor reads frozen COW cells in batches of at most1024.\nHistory/world revisions still fence owner publication. Cursor budget\nand terrain failures poison the cursor; implicit terrain is omitted.\n\nPrivate backup only; production save backend remains unchanged.\nFull city/maximum,GPU,32-client and durability qualification pending.";
        SwingUtilities.invokeAndWait(()->{var frame=new JFrame("Voxel One world override CLI evidence");var area=new JTextArea(text);area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);area.setFont(new Font(Font.MONOSPACED,Font.PLAIN,15));area.setMargin(new Insets(20,20,20,20));area.setBackground(new Color(18,25,35));area.setForeground(new Color(220,235,245));frame.add(area);frame.setSize(1000,540);frame.setLocation(60,60);frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);frame.setVisible(true);});
    }
}
