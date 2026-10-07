package dev.jayms.render;

import org.joml.Vector3f;

/** Projected surface decal: local box, RGB tint and opacity. No world/collision mutation. */
public record Decal(Vector3f center,Vector3f normal,float radius,float depth,Vector3f color,float opacity) {
    public Decal {
        if(center==null||normal==null||color==null||!center.isFinite()||!normal.isFinite()||!color.isFinite()||normal.lengthSquared()<.001f||!Float.isFinite(radius)||radius<=0||!Float.isFinite(depth)||depth<=0||!Float.isFinite(opacity)||opacity<0||opacity>1)throw new IllegalArgumentException("Invalid decal");
        center=new Vector3f(center);normal=new Vector3f(normal).normalize();color=new Vector3f(color);
    }
    @Override public Vector3f center(){return new Vector3f(center);}
    @Override public Vector3f normal(){return new Vector3f(normal);}
    @Override public Vector3f color(){return new Vector3f(color);}
}
