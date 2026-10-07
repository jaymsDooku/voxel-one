#version 330 core
in vec2 vUV;out float fragDepth;
uniform sampler2D uSource;
void main(){
    ivec2 p=ivec2(gl_FragCoord.xy)*2,size=textureSize(uSource,0)-1;
    float a=texelFetch(uSource,min(p,size),0).r,b=texelFetch(uSource,min(p+ivec2(1,0),size),0).r;
    float c=texelFetch(uSource,min(p+ivec2(0,1),size),0).r,d=texelFetch(uSource,min(p+ivec2(1,1),size),0).r;
    fragDepth=max(max(a,b),max(c,d));
}
