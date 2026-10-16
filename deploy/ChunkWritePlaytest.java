package dev.jayms;

import dev.jayms.net.Blocks;
import dev.jayms.net.Protocol;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** CPU-only integration evidence; draws actual cell occupancy without OpenGL. */
public class ChunkWritePlaytest {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        var image = new BufferedImage(1000, 390, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(22, 26, 33)); g.fillRect(0, 0, 1000, 390);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
        g.setColor(Color.WHITE);
        g.drawString("Chunk.setBlock CPU playtest | actual cell slices at local z=0 | no OpenGL", 20, 26);
        var c = new Chunk();
        c.setBlock(2, 3, 4, Blocks.STONE);
        c.installMesh(null, null, c.meshRevision());
        panel(g, c, 0, "Stone before");
        long geometry = c.geometryVersion(), mesh = c.meshRevision();
        var snapshot = c.cell(2, 3, 4).leaves();
        c.setBlock(2, 3, 4, Blocks.STONE);
        if (geometry != c.geometryVersion() || mesh != c.meshRevision() || c.dirty()
                || !snapshot.equals(c.cell(2, 3, 4).leaves())) throw new AssertionError("repeat changed state");
        panel(g, c, 1, "Repeat: unchanged");
        c.apply(new Protocol.Edit(2, 3, 4, 0, 1, 0, 0, 0));
        panel(g, c, 2, "Half-cell carved");
        geometry = c.geometryVersion(); mesh = c.meshRevision();
        c.setBlock(2, 3, 4, Blocks.STONE);
        if (c.geometryVersion() != geometry + 1 || c.meshRevision() != mesh + 1
                || !snapshot.equals(c.cell(2, 3, 4).leaves())) throw new AssertionError("replacement failed");
        panel(g, c, 3, "Full stone restored");
        c.setBlock(2, 3, 4, Blocks.FLOWER_POT);
        if (!c.models().containsValue(Blocks.FLOWER_POT)) throw new AssertionError("model missing");
        c.setBlock(2, 3, 4, 0);
        if (!c.models().isEmpty() || c.getBlock(2, 3, 4) != 0) throw new AssertionError("model retained");
        try { c.setBlock(16, 0, 0, 0); throw new AssertionError("bounds accepted"); }
        catch (IndexOutOfBoundsException expected) { }
        g.setColor(Color.WHITE);
        g.drawString("PASS: repeat preserves state; fractional replacement mutates; model removal; bounds throw", 20, 362);
        g.dispose();
        ImageIO.write(image, "png", out.resolve("chunk-unchanged-write.png").toFile());
        System.out.println("PASS: repeat, fractional replacement, model removal, bounds; CPU cell evidence saved");
    }

    private static void panel(Graphics2D g, Chunk c, int panel, String label) {
        int left = 20 + panel * 245;
        g.setColor(Color.WHITE); g.drawString(label, left, 62);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
            int value = c.cell(2, 3, 4).get(x, y, 0);
            g.setColor(value == 0 ? new Color(45, 51, 60) : new Color(166, 181, 195));
            g.fillRect(left + x * 13, 82 + (15 - y) * 13, 12, 12);
        }
        g.setColor(Color.WHITE);
        g.drawString("geometry=" + c.geometryVersion(), left, 310);
        g.drawString("mesh=" + c.meshRevision() + " dirty=" + c.dirty(), left, 330);
    }
}
