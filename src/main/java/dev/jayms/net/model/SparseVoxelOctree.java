package dev.jayms.net.model;

import java.io.*;

/** Null nodes represent air; uniform regions collapse to one colored leaf. */
public final class SparseVoxelOctree {
    private static final class Node {
        final int color;
        final Node[] children;

        Node(int color) {
            this.color = color;
            children = null;
        }

        Node(Node[] children) {
            color = 0;
            this.children = children;
        }
    }

    public record Hit(float distance, int nx, int ny, int nz, float side) {}

    private Node root;
    private final int size;
    private boolean frozen;

    public SparseVoxelOctree(int size) {
        if (size < 1 || size > 256 || (size & (size - 1)) != 0)
            throw new IllegalArgumentException(
                    "Resolution must be a power of two from 1 through 256");
        this.size = size;
    }

    public int size() {
        return size;
    }

    public int get(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= size || y >= size || z >= size) return 0;
        Node n = root;
        for (int half = size / 2; n != null && n.children != null; half /= 2) {
            int index = (x >= half ? 1 : 0) | (y >= half ? 2 : 0) | (z >= half ? 4 : 0);
            if (x >= half) x -= half;
            if (y >= half) y -= half;
            if (z >= half) z -= half;
            n = n.children[index];
        }
        return n == null ? 0 : n.color;
    }

    public void set(int x, int y, int z, int color) {
        if (frozen) throw new IllegalStateException("Published models are immutable");
        if (x < 0 || y < 0 || z < 0 || x >= size || y >= size || z >= size)
            throw new IndexOutOfBoundsException();
        if (color != 0 && (color >>> 24) != 255)
            throw new IllegalArgumentException("Color must be opaque ARGB or air");
        root = set(root, size, x, y, z, color);
    }

    private static Node set(Node n, int size, int x, int y, int z, int color) {
        if (size == 1) return color == 0 ? null : new Node(color);
        if (n != null && n.children == null && n.color == color || n == null && color == 0)
            return n;
        int h = size / 2;
        Node[] children = n != null && n.children != null ? n.children.clone() : new Node[8];
        if (n != null && n.children == null) java.util.Arrays.fill(children, n);
        int index = (x >= h ? 1 : 0) | (y >= h ? 2 : 0) | (z >= h ? 4 : 0);
        children[index] = set(children[index], h, x % h, y % h, z % h, color);
        int first = children[0] == null ? 0 : children[0].color;
        boolean uniform = true;
        for (Node child : children)
            if (child != null && child.children != null
                    || (child == null ? 0 : child.color) != first) {
                uniform = false;
                break;
            }
        return uniform ? (first == 0 ? null : new Node(first)) : new Node(children);
    }

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, int color) {
        if (frozen) throw new IllegalStateException("Published octrees are immutable");
        if (x0 < 0 || y0 < 0 || z0 < 0 || x1 > size || y1 > size || z1 > size || x1 < x0 || y1 < y0
                || z1 < z0) throw new IndexOutOfBoundsException();
        if (color != 0 && color >>> 24 != 255)
            throw new IllegalArgumentException("Opaque value required");
        root = fill(root, 0, 0, 0, size, x0, y0, z0, x1, y1, z1, color);
    }

    private static Node fill(
            Node n,
            int x,
            int y,
            int z,
            int side,
            int ax,
            int ay,
            int az,
            int bx,
            int by,
            int bz,
            int color) {
        if (ax >= x + side || ay >= y + side || az >= z + side || bx <= x || by <= y || bz <= z)
            return n;
        if (ax <= x && ay <= y && az <= z && bx >= x + side && by >= y + side && bz >= z + side)
            return color == 0 ? null : new Node(color);
        if (n == null && color == 0 || n != null && n.children == null && n.color == color)
            return n;
        Node[] children = n != null && n.children != null ? n.children.clone() : new Node[8];
        if (n != null && n.children == null) java.util.Arrays.fill(children, n);
        int h = side / 2;
        for (int i = 0; i < 8; i++)
            children[i] =
                    fill(
                            children[i],
                            x + ((i & 1) != 0 ? h : 0),
                            y + ((i & 2) != 0 ? h : 0),
                            z + ((i & 4) != 0 ? h : 0),
                            h,
                            ax,
                            ay,
                            az,
                            bx,
                            by,
                            bz,
                            color);
        int first = children[0] == null ? 0 : children[0].color;
        for (Node child : children)
            if (child != null && child.children != null
                    || (child == null ? 0 : child.color) != first) return new Node(children);
        return first == 0 ? null : new Node(first);
    }

    /** Read an aligned region without expanding any uniform nodes. -1 denotes a mixed region. */
    public int uniform(int x, int y, int z, int side) {
        Node n = regionNode(x, y, z, side);
        return n == null ? 0 : n.children == null ? n.color : -1;
    }

    public SparseVoxelOctree region(int x, int y, int z, int side) {
        SparseVoxelOctree result = new SparseVoxelOctree(side);
        result.root = regionNode(x, y, z, side);
        return result;
    }

    private Node regionNode(int x, int y, int z, int side) {
        if (side < 1
                || side > size
                || (side & (side - 1)) != 0
                || x < 0
                || y < 0
                || z < 0
                || x + side > size
                || y + side > size
                || z + side > size
                || x % side != 0
                || y % side != 0
                || z % side != 0)
            throw new IllegalArgumentException("Aligned octree region required");
        Node n = root;
        for (int span = size; span > side && n != null && n.children != null; ) {
            int h = span / 2;
            n = n.children[(x >= h ? 1 : 0) | (y >= h ? 2 : 0) | (z >= h ? 4 : 0)];
            x %= h;
            y %= h;
            z %= h;
            span = h;
        }
        return n;
    }

    // Nodes are persistent: edits copy only the path to a leaf, so snapshots share safely.
    public SparseVoxelOctree copy() {
        SparseVoxelOctree c = new SparseVoxelOctree(size);
        c.root = root;
        return c;
    }

    public SparseVoxelOctree freeze() {
        frozen = true;
        return this;
    }

    public int nodes() {
        return nodes(root);
    }

    private static int nodes(Node n) {
        if (n == null) return 0;
        int count = 1;
        if (n.children != null) for (Node child : n.children) count += nodes(child);
        return count;
    }

    public int occupied() {
        return occupied(root, size);
    }

    private static int occupied(Node n, int size) {
        if (n == null) return 0;
        if (n.children == null) return size * size * size;
        int count = 0;
        for (Node child : n.children) count += occupied(child, size / 2);
        return count;
    }

    public boolean intersects(float x0, float y0, float z0, float x1, float y1, float z1) {
        return intersects(root, 0, 0, 0, 1, x0, y0, z0, x1, y1, z1);
    }

    private static boolean intersects(
            Node n,
            float x,
            float y,
            float z,
            float side,
            float ax,
            float ay,
            float az,
            float bx,
            float by,
            float bz) {
        if (n == null
                || bx <= x
                || by <= y
                || bz <= z
                || ax >= x + side
                || ay >= y + side
                || az >= z + side) return false;
        if (n.children == null) return true;
        float h = side / 2;
        for (int i = 0; i < 8; i++)
            if (intersects(
                    n.children[i],
                    x + ((i & 1) != 0 ? h : 0),
                    y + ((i & 2) != 0 ? h : 0),
                    z + ((i & 4) != 0 ? h : 0),
                    h,
                    ax,
                    ay,
                    az,
                    bx,
                    by,
                    bz)) return true;
        return false;
    }

    public Hit raycast(
            float ox, float oy, float oz, float dx, float dy, float dz, float start, float end) {
        if (!Float.isFinite(ox)
                || !Float.isFinite(oy)
                || !Float.isFinite(oz)
                || !Float.isFinite(dx)
                || !Float.isFinite(dy)
                || !Float.isFinite(dz)
                || dx == 0 && dy == 0 && dz == 0) return null;
        return raycast(root, 0, 0, 0, 1, ox, oy, oz, dx, dy, dz, start, end);
    }

    private static Hit raycast(
            Node n,
            float x,
            float y,
            float z,
            float side,
            float ox,
            float oy,
            float oz,
            float dx,
            float dy,
            float dz,
            float start,
            float end) {
        if (n == null) return null;
        float near = 0, far = end;
        int nx = 0, ny = 0, nz = 0;
        float[] low = {x, y, z}, o = {ox, oy, oz}, d = {dx, dy, dz};
        for (int axis = 0; axis < 3; axis++) {
            if (d[axis] == 0) {
                if (o[axis] < low[axis] || o[axis] >= low[axis] + side) return null;
                continue;
            }
            float a = (low[axis] - o[axis]) / d[axis], b = (low[axis] + side - o[axis]) / d[axis];
            float enter = Math.min(a, b), leave = Math.max(a, b);
            if (enter >= near) {
                near = enter;
                nx = ny = nz = 0;
                int normal = d[axis] > 0 ? -1 : 1;
                if (axis == 0) nx = normal;
                else if (axis == 1) ny = normal;
                else nz = normal;
            }
            far = Math.min(far, leave);
            if (near > far) return null;
        }
        if (far < start) return null;
        if (n.children == null) return new Hit(Math.max(start, near), nx, ny, nz, side);
        Hit best = null;
        float half = side / 2;
        for (int i = 0; i < 8; i++) {
            Hit hit =
                    raycast(
                            n.children[i],
                            x + ((i & 1) != 0 ? half : 0),
                            y + ((i & 2) != 0 ? half : 0),
                            z + ((i & 4) != 0 ? half : 0),
                            half,
                            ox,
                            oy,
                            oz,
                            dx,
                            dy,
                            dz,
                            start,
                            best == null ? far : best.distance);
            if (hit != null && (best == null || hit.distance < best.distance)) best = hit;
        }
        return best;
    }

    public record Leaf(int x, int y, int z, int side, int color) {}

    public java.util.List<Leaf> leaves() {
        var result = new java.util.ArrayList<Leaf>();
        leaves(root, 0, 0, 0, size, result);
        return java.util.List.copyOf(result);
    }

    private static void leaves(Node n, int x, int y, int z, int side, java.util.List<Leaf> out) {
        if (n == null) return;
        if (n.children == null) {
            out.add(new Leaf(x, y, z, side, n.color));
            return;
        }
        int h = side / 2;
        for (int i = 0; i < 8; i++)
            leaves(
                    n.children[i],
                    x + ((i & 1) != 0 ? h : 0),
                    y + ((i & 2) != 0 ? h : 0),
                    z + ((i & 4) != 0 ? h : 0),
                    h,
                    out);
    }

    public void write(DataOutputStream out) throws IOException {
        out.writeByte(size == 256 ? 0 : size);
        if (size == 256) out.writeShort(size);
        write(root, out);
    }

    private static void write(Node n, DataOutputStream out) throws IOException {
        if (n == null) {
            out.writeByte(0);
            return;
        }
        if (n.children == null) {
            out.writeByte(1);
            out.writeInt(n.color);
        } else {
            out.writeByte(2);
            for (Node child : n.children) write(child, out);
        }
    }

    public static SparseVoxelOctree read(DataInputStream in) throws IOException {
        int size = in.readUnsignedByte();
        if (size == 0) size = in.readUnsignedShort();
        if (size < 1 || size > 256 || (size & (size - 1)) != 0)
            throw new IOException("Invalid octree resolution");
        SparseVoxelOctree tree = new SparseVoxelOctree(size);
        tree.root = read(in, size);
        return tree;
    }

    private static Node read(DataInputStream in, int size) throws IOException {
        int tag = in.readUnsignedByte();
        if (tag == 0) return null;
        if (tag == 1) {
            int color = in.readInt();
            if (color >>> 24 != 255) throw new IOException("Invalid voxel color");
            return new Node(color);
        }
        if (tag != 2 || size == 1) throw new IOException("Invalid octree branch");
        Node[] children = new Node[8];
        for (int i = 0; i < 8; i++) children[i] = read(in, size / 2);
        return new Node(children);
    }
}
