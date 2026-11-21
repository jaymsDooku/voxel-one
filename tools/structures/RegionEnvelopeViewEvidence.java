import java.awt.*;
import java.nio.charset.StandardCharsets;
import javax.swing.*;

/** Fixed synthetic envelope child; no live profiles or private account data. */
public final class RegionEnvelopeViewEvidence {
    public static void main(String[] args)throws Exception {
        var child=new ProcessBuilder("java","-Xmx512m","-cp",System.getProperty("java.class.path"),"RegionEnvelopeViewCheck").redirectErrorStream(true).start();
        byte[] bytes=child.getInputStream().readNBytes(16385);if(bytes.length>16384){child.destroyForcibly();throw new IllegalStateException("Child output cap");}
        int exit=child.waitFor();String output=new String(bytes,StandardCharsets.UTF_8);if(exit!=0)throw new IllegalStateException("Synthetic child failed: "+output);
        String text="Voxel One: bounded envelope section views\n\nActual child: java -Xmx512m -cp "+System.getProperty("java.class.path")+" RegionEnvelopeViewCheck\n\nObserved exit: "+exit+"\n\n"+output+"\n\nVersions1..4 retain byte-exact library, geometry, survival and city data.\nUsed/unused models, fractional geometry and explicit air survive.\nShort/long declared section lengths and inner CRC corruption reject.\nOffset/length slices and actual generation-file restart pass.\n\nEncoded library/geometry sections use bounded views of the existing\nenvelope array. No new encoded section arrays; decoded state remains\nmaterialized. No measured peak-memory or speedup claim.\n\nPrivate prototype only. Caps and rollout gates unchanged.\nProduction backend and full city/GPU/32-client qualification pending.";
        SwingUtilities.invokeAndWait(()->{var frame=new JFrame("Voxel One envelope view CLI evidence");var area=new JTextArea(text);area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);area.setFont(new Font(Font.MONOSPACED,Font.PLAIN,15));area.setMargin(new Insets(20,20,20,20));area.setBackground(new Color(18,25,35));area.setForeground(new Color(220,235,245));frame.add(area);frame.setSize(1000,520);frame.setLocation(60,60);frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);frame.setVisible(true);});
    }
}
