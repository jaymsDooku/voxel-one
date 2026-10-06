package dev.jayms.render;

import static org.lwjgl.opengl.GL33.*;

import dev.jayms.net.Blocks;

import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/** Small deterministic tile set; mipmaps and anisotropy keep distant surfaces stable. */
public final class MaterialTextures implements AutoCloseable {
    public final int id;

    public static int layer(int type) {
        return switch (type) {
            case Blocks.DIRT -> 0;
            case Blocks.GRASS -> 1;
            case Blocks.STONE -> 2;
            case Blocks.SAND -> 3;
            case Blocks.SNOW -> 4;
            case Blocks.WOOD -> 5;
            case Blocks.LEAVES -> 6;
            case Blocks.PLANKS -> 7;
            case Blocks.BRICKS -> 8;
            case Blocks.GLASS -> 9;
            case Blocks.LED -> 10;
            case Blocks.ASPHALT -> 11;
            case Blocks.ROAD_LINE_X -> 12;
            case Blocks.ROAD_LINE_Z -> 13;
            default -> -1;
        };
    }

    public MaterialTextures() {
        id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, id);
        ByteBuffer pixels = MemoryUtil.memAlloc(32 * 32 * 14 * 4);
        try {
            for (int l = 0; l < 14; l++)
                for (int y = 0; y < 32; y++)
                    for (int x = 0; x < 32; x++) {
                        int hash = (x * 374761393 + y * 668265263 + l * 1274126177);
                        hash = (hash ^ (hash >>> 13)) * 1274126177;
                        int shade = 210 + (hash >>> 24 & 31);
                        if (l == 5 || l == 7) shade = (int) (222 + 12 * Math.sin(x * .8 + y * .15));
                        if (l == 7 && (y % 8 == 0 || x % 16 == (y / 8 % 2) * 8)) shade = 155;
                        if (l == 8 && (y % 8 == 0 || x % 16 == (y / 8 % 2) * 8)) shade = 145;
                        if (l == 9) shade = 245;
                        if (l == 10) shade = x % 8 == 0 || y % 8 == 0 ? 190 : 255;
                        if (l >= 11) {
                            shade = 50 + (hash >>> 24 & 15);
                            if (l == 12 && y >= 14 && y <= 17 || l == 13 && x >= 14 && x <= 17)
                                shade = 250;
                        }
                        pixels.put((byte) shade)
                                .put((byte) shade)
                                .put((byte) shade)
                                .put((byte) 255);
                    }
            pixels.flip();
            glTexImage3D(
                    GL_TEXTURE_2D_ARRAY,
                    0,
                    GL_RGBA8,
                    32,
                    32,
                    14,
                    0,
                    GL_RGBA,
                    GL_UNSIGNED_BYTE,
                    pixels);
        } finally {
            MemoryUtil.memFree(pixels);
        }
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_REPEAT);
        if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic)
            glTexParameterf(GL_TEXTURE_2D_ARRAY, 0x84FE, Math.min(8, glGetFloat(0x84FF)));
    }

    @Override
    public void close() {
        glDeleteTextures(id);
    }
}
