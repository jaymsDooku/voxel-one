package dev.jayms;

public record MeshData(float[] vertices, int[] indices, float[] surface) {
    public MeshData(float[] vertices, int[] indices) {
        this(vertices, indices, null);
    }
}
