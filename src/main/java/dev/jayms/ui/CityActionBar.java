package dev.jayms.ui;

/** Shared dock geometry keeps rendered icons and pointer targets in sync. */
public final class CityActionBar {
    private CityActionBar() {}
    static final int[] TOOLS = {-1, 4, 0, 1, 2, 3, 5, 6, 7};
    private static final String[] LABELS = {"Inspect", "Roads", "Residential", "Commercial",
            "Industrial", "Agriculture", "Economy", "Special", "Exchange"};
    public static float cell(int width) { return Math.min(44, (width - 32f) / 9); }
    public static float left(int width) { return (width - cell(width) * 9) / 2; }
    public static float top(int height) { return height - 58; }
    public static int hit(float x, float y, int width, int height) {
        float cell = cell(width), offset = x - left(width);
        if (cell <= 4 || offset < 0 || offset >= cell * 9 || y < top(height)
                || y >= top(height) + 42) return -1;
        int index = (int)(offset / cell);
        return offset - index * cell < cell - 4 ? index : -1;
    }
    static void render(Overlay ui, int width, int height, int tool, int hovered) {
        float cell = cell(width), left = left(width), top = top(height);
        ui.rectangle(left - 6, top - 6, cell * 9 + 8, 54, .015f, .025f, .04f, .92f);
        for (int i = 0; i < TOOLS.length; i++) {
            boolean active = tool == TOOLS[i] || (i == 1 && tool == 11)
                    || (i == 7 && tool >= 8 && tool <= 10);
            float x = left + i * cell;
            ui.rectangle(x, top, cell - 4, 42, active ? .12f : .035f,
                    active ? .32f : hovered == i ? .18f : .09f, active ? .36f : .13f, 1);
            if (active) ui.rectangle(x + 4, top + 38, cell - 12, 2, .3f, .95f, 1, 1);
            icon(ui, i, x + (cell - 4) / 2, top + 20, Math.min(1, (cell - 10) / 28));
        }
        if (hovered >= 0) {
            String label = LABELS[hovered];
            float tw = ui.textWidth(label, 1.3f);
            float x = Math.max(8, Math.min(width - tw - 24,
                    left + (hovered + .5f) * cell - tw / 2 - 8));
            ui.rectangle(x, top - 66, tw + 16, 25, .025f, .06f, .09f, 1);
            ui.text(label, x + 8, top - 59, 1.3f);
        }
    }
    private static void icon(Overlay ui, int kind, float x, float y, float s) {
        // Small line glyphs: lens, road, home, shop, factory, crop, chart, civic hall, exchange.
        float[][] lines = switch (kind) {
            case 0 -> new float[][] {{-9,-10,3,-10},{3,-10,7,-6},{7,-6,7,4},{7,4,3,8},
                    {3,8,-9,8},{-9,8,-13,4},{-13,4,-13,-6},{-13,-6,-9,-10},{6,7,13,14}};
            case 1 -> new float[][] {{-10,-13,-10,13},{10,-13,10,13},{0,-12,0,-5},{0,-2,0,4},{0,7,0,13}};
            case 2 -> new float[][] {{-13,-2,0,-13},{0,-13,13,-2},{-10,-4,-10,12},{-10,12,10,12},
                    {10,12,10,-4},{-3,12,-3,3},{-3,3,3,3},{3,3,3,12}};
            case 3 -> new float[][] {{-12,-10,12,-10},{-12,-10,-14,-3},{12,-10,14,-3},
                    {-14,-3,14,-3},{-10,-3,-10,12},{-10,12,10,12},{10,12,10,-3},{-3,-3,-3,12},{4,2,7,2}};
            case 4 -> new float[][] {{-12,12,12,12},{12,12,12,-13},{12,-13,6,-13},{6,-13,6,-1},
                    {6,-1,-3,-7},{-3,-7,-3,-1},{-3,-1,-12,-7},{-12,-7,-12,12},{-7,4,-4,4},{1,4,4,4}};
            case 5 -> new float[][] {{0,-13,0,13},{0,-6,-9,-12},{-9,-12,-9,-6},{-9,-6,0,0},
                    {0,-6,9,-12},{9,-12,9,-6},{9,-6,0,0},{0,5,-9,-1},{0,5,9,-1}};
            case 6 -> new float[][] {{-12,-13,-12,12},{-12,12,13,12},{-7,7,-2,0},{-2,0,4,3},
                    {4,3,12,-9},{5,-9,12,-9},{12,-9,12,-2}};
            case 7 -> new float[][] {{-14,-5,0,-13},{0,-13,14,-5},{-14,-5,14,-5},
                    {-10,-2,-10,9},{0,-2,0,9},{10,-2,10,9},{-14,12,14,12}};
            default -> new float[][] {{-13,-6,12,-6},{12,-6,5,-13},{12,-6,5,1},
                    {13,7,-12,7},{-12,7,-5,0},{-12,7,-5,14}};
        };
        for (float[] l : lines) ui.line(x + l[0]*s, y + l[1]*s, x + l[2]*s,
                y + l[3]*s, 2, .78f, .91f, .94f, 1);
    }
}
