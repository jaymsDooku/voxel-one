import dev.jayms.*;
import dev.jayms.net.*;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

/** CPU raster evidence of actual engine MeshData; no OpenGL or interactive play is claimed. */
public class TerrainViewEvidence {
    static final int W = 960, H = 600;
    static BufferedImage image;
    static float[] depth;
    static Matrix4f transform;

    static void draw(MeshData data, int ox, int oz) {
        float[] v = data.vertices();
        int[] indices = data.indices();
        float[][] screen = new float[v.length / 9][4];
        for (int i = 0; i < screen.length; i++) {
            Vector4f p =
                    transform.transform(
                            new Vector4f(v[i * 9] + ox, v[i * 9 + 1], v[i * 9 + 2] + oz, 1));
            screen[i] =
                    new float[] {
                        (p.x / p.w * .5f + .5f) * W, (.5f - p.y / p.w * .5f) * H, p.z / p.w, p.w
                    };
        }
        for (int i = 0; i < indices.length; i += 3) {
            int ia = indices[i], ib = indices[i + 1], ic = indices[i + 2];
            float[] a = screen[ia], b = screen[ib], c = screen[ic];
            if (a[3] <= 0 || b[3] <= 0 || c[3] <= 0) continue;
            float area = edge(a, b, c[0], c[1]);
            if (Math.abs(area) < .001) continue;
            int x0 = Math.max(0, (int) Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
            int x1 = Math.min(W - 1, (int) Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
            int y0 = Math.max(0, (int) Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
            int y1 = Math.min(H - 1, (int) Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
            float light =
                    .5f
                            + .5f
                                    * Math.max(
                                            0,
                                            v[ia * 9 + 3] * .45f
                                                    + v[ia * 9 + 4] * .78f
                                                    - v[ia * 9 + 5] * .45f);
            int rgb = 0;
            for (int n = 0; n < 3; n++)
                rgb =
                        rgb << 8
                                | Math.min(
                                        255,
                                        (int) (255 * Math.pow(v[ia * 9 + 6 + n] * light, .85)));
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++) {
                    float u = edge(b, c, x + .5f, y + .5f) / area,
                            w = edge(c, a, x + .5f, y + .5f) / area,
                            t = 1 - u - w;
                    if (u < 0 || w < 0 || t < 0) continue;
                    float d = u * a[2] + w * b[2] + t * c[2];
                    if (d >= -1 && d <= 1 && d < depth[y * W + x]) {
                        depth[y * W + x] = d;
                        image.setRGB(x, y, rgb);
                    }
                }
        }
    }

    static float edge(float[] a, float[] b, float x, float y) {
        return (x - a[0]) * (b[1] - a[1]) - (y - a[1]) * (b[0] - a[0]);
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        Terrain terrain = new Terrain(Terrain.DEFAULT_SEED);
        var mesher = new DistantTerrainMesher(terrain, List.of());
        for (int viewIndex = 0; viewIndex < 3; viewIndex++) {
            boolean planning = viewIndex == 0;
            image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            depth = new float[W * H];
            Arrays.fill(depth, Float.POSITIVE_INFINITY);
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) image.setRGB(x, y, 0x99bddb);
            IsometricCamera overview = new IsometricCamera();
            overview.zoom(3);
            Camera ground = new Camera();
            ground.position().set(18, 28.65f, 24);
            ground.setYaw(-38.2f);
            ground.setPitch(3.45f);
            if (viewIndex == 2) {
                for (int x = 40; x < 100; x++) {
                    var f = terrain.fields(x, 24);
                    if (f.waterLevel() == f.height() + 1) {
                        ground.position().set(x + .5f, f.height() + 2.6f, 24.5f);
                        break;
                    }
                }
                ground.setYaw(90);
                ground.setPitch(-9);
            }
            Matrix4f projection =
                    planning
                            ? overview.projection(new WorldBounds(-384, -384, 384, 512), W, H)
                            : new Matrix4f()
                                    .perspective(
                                            (float) Math.toRadians(70), W / (float) H, .1f, 1800);
            Camera camera = planning ? overview.camera() : ground;
            Vector3f eye = new Vector3f(camera.position());
            Matrix4f view = camera.createViewMatrix();
            transform = projection.mul(view);
            for (int x = -384; x < 384; x += 64)
                for (int z = -384; z < 512; z += 64) {
                    int size = x >= -64 && x < 192 && z >= -128 && z < 192 ? 16 : 64;
                    for (int a = 0; a < 64; a += size)
                        for (int b = 0; b < 64; b += size) {
                            var tile = new DistantTerrainPlan.Tile(x + a, z + b, size);
                            draw(mesher.build(tile), tile.x(), tile.z());
                        }
                }
            long visible = 0;
            for (float d : depth) if (Float.isFinite(d)) visible++;
            if (visible < W * H / 5) throw new AssertionError("Terrain missing from view");
            ImageIO.write(
                    image,
                    "png",
                    out.resolve(
                                    planning
                                            ? "terrain-cpu-city-planning.png"
                                            : viewIndex == 1
                                                    ? "terrain-cpu-ground-level.png"
                                                    : "terrain-cpu-river-bank.png")
                            .toFile());
            System.out.println(
                    (planning ? "City planning" : "Ground level")
                            + ": "
                            + visible
                            + " visible terrain pixels");
        }
    }
}
