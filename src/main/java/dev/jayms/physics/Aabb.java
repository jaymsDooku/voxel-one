package dev.jayms.physics;

import org.joml.Vector3f;

/** Immutable validated bounds. Contact touching is distinct from overlap. */
public record Aabb(float x0,float y0,float z0,float x1,float y1,float z1) {
    public Aabb {
        if(!Float.isFinite(x0)||!Float.isFinite(y0)||!Float.isFinite(z0)||!Float.isFinite(x1)||!Float.isFinite(y1)||!Float.isFinite(z1)||x1<x0||y1<y0||z1<z0)throw new IllegalArgumentException("Invalid bounds");
    }
    public float min(int axis){return axis==0?x0:axis==1?y0:z0;}
    public float max(int axis){return axis==0?x1:axis==1?y1:z1;}
    public boolean overlaps(Aabb b){return x0<b.x1&&x1>b.x0&&y0<b.y1&&y1>b.y0&&z0<b.z1&&z1>b.z0;}
    public Aabb expanded(float e){return new Aabb(x0-e,y0-e,z0-e,x1+e,y1+e,z1+e);}
    public Aabb translate(Vector3f d){return new Aabb(x0+d.x,y0+d.y,z0+d.z,x1+d.x,y1+d.y,z1+d.z);}
    public Aabb swept(Vector3f d){return new Aabb(x0+Math.min(0,d.x),y0+Math.min(0,d.y),z0+Math.min(0,d.z),x1+Math.max(0,d.x),y1+Math.max(0,d.y),z1+Math.max(0,d.z));}
    public record Hit(float time,Vector3f normal) {}
    public Hit sweep(Aabb b,Vector3f displacement) {
        if(!displacement.isFinite())throw new IllegalArgumentException("Invalid displacement");
        if(overlaps(b))return new Hit(0,penetrationNormal(b));
        float entry=Float.NEGATIVE_INFINITY,exit=Float.POSITIVE_INFINITY;int axis=-1;
        for(int a=0;a<3;a++) {
            float v=displacement.get(a);
            if(Math.abs(v)<1e-9f){if(max(a)<=b.min(a)||min(a)>=b.max(a))return null;continue;}
            float enter=(v>0?b.min(a)-max(a):b.max(a)-min(a))/v;
            float leave=(v>0?b.max(a)-min(a):b.min(a)-max(a))/v;
            if(enter>entry){entry=enter;axis=a;}exit=Math.min(exit,leave);
        }
        if(axis<0||entry>exit||exit<0||entry<0||entry>1)return null;
        return new Hit(entry,new Vector3f().setComponent(axis,displacement.get(axis)>0?-1:1));
    }
    public Vector3f penetrationNormal(Aabb b) {
        float best=Float.POSITIVE_INFINITY;int axis=0;float sign=1;
        for(int a=0;a<3;a++) {
            float negative=max(a)-b.min(a),positive=b.max(a)-min(a);
            if(negative<best){best=negative;axis=a;sign=-1;}if(positive<best){best=positive;axis=a;sign=1;}
        }
        return new Vector3f().setComponent(axis,sign);
    }
    public float penetration(Aabb b,Vector3f normal) {
        for(int a=0;a<3;a++)if(normal.get(a)!=0)return normal.get(a)<0?max(a)-b.min(a):b.max(a)-min(a);
        return 0;
    }
    public Hit ray(Vector3f origin,Vector3f direction,float distance) {
        if(!origin.isFinite()||!direction.isFinite()||!Float.isFinite(distance)||distance<0)throw new IllegalArgumentException("Invalid ray");
        Aabb point=new Aabb(origin.x,origin.y,origin.z,origin.x,origin.y,origin.z);
        if(origin.x>x0&&origin.x<x1&&origin.y>y0&&origin.y<y1&&origin.z>z0&&origin.z<z1)return new Hit(0,new Vector3f());
        return point.sweep(this,new Vector3f(direction).mul(distance));
    }
}
