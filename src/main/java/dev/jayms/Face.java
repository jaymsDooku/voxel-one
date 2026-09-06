package dev.jayms;

public enum Face {

    RIGHT(1, 0, 0,
            new float[]{
                    1, 0, 0,
                    1, 1, 0,
                    1, 1, 1,
                    1, 0, 1
            }),
    LEFT(-1, 0, 0,
            new float[]{
                    0, 0, 1,
                    0, 1, 1,
                    0, 1, 0,
                    0, 0, 0
            }),
    TOP(0, 1, 0,
            new float[]{
                    0, 1, 1,
                    1, 1, 1,
                    1, 1, 0,
                    0, 1, 0
            }),
    BOTTOM(
            0, -1, 0,
            new float[]{
                    0, 0, 0,
                    1, 0, 0,
                    1, 0, 1,
                    0, 0, 1
            }
    ),
    FRONT(
            0, 0, 1,
            new float[]{
                    1, 0, 1,
                    1, 1, 1,
                    0, 1, 1,
                    0, 0, 1
            }
    ),

    BACK(
            0, 0, -1,
            new float[]{
                    0, 0, 0,
                    0, 1, 0,
                    1, 1, 0,
                    1, 0, 0
            }
    );

    private int dx;
    private int dy;
    private int dz;
    private float[] vertices;

    Face(int dx, int dy, int dz, float[] vertices) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.vertices = vertices;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public int dz() {
        return dz;
    }

    public float[] vertices() {
        return vertices;
    }
}
