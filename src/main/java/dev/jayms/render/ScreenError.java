package dev.jayms.render;

/** Projected geometric error in pixels, shared by perspective and orthographic terrain selection. */
public final class ScreenError {
    private ScreenError() {}
    public static float pixels(float worldError,float distance,float projectionY,int height,boolean orthographic){
        if(worldError<0||distance<0||height<1||!Float.isFinite(worldError)||!Float.isFinite(distance)||!Float.isFinite(projectionY))throw new IllegalArgumentException("Invalid screen error input");
        return worldError*Math.abs(projectionY)*height*.5f/(orthographic?1:Math.max(1,distance));
    }
    public static int tileSize(float distance,float projectionY,int height,boolean orthographic,float limit){
        int tile=16;
        while(tile<1024&&pixels(tile*2/16f,distance,projectionY,height,orthographic)<=limit)tile*=2;
        return tile;
    }
}
