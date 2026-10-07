#version 330 core
in vec2 vUV;out vec4 fragColor;
uniform sampler2D uScene;
uniform sampler3D uLut;
uniform float uLutSize;
uniform float uExposure,uSaturation,uContrast;
uniform int uBloom;
vec3 aces(vec3 x){return clamp((x*(2.51*x+.03))/(x*(2.43*x+.59)+.14),0.,1.);}
void main(){
    vec3 color=textureLod(uScene,vUV,0.).rgb;
    if(uBloom==1){
        vec3 bloom=vec3(0);vec2 pixel=1./vec2(textureSize(uScene,0));
        for(int level=2;level<=6;level++){
            float lod=float(level);vec3 blur=vec3(0);
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)
                blur+=textureLod(uScene,vUV+vec2(x,y)*pixel*exp2(lod),lod).rgb;
            bloom+=max(blur/9.-vec3(1.),vec3(0))*.04;
        }
        color+=bloom;
    }
    color=aces(color*uExposure);
    float luma=dot(color,vec3(.2126,.7152,.0722));
    color=mix(vec3(luma),color,uSaturation);
    color=clamp((color-.5)*uContrast+.5,0.,1.);
    color=texture(uLut,color*((uLutSize-1.)/uLutSize)+.5/uLutSize).rgb;
    fragColor=vec4(pow(color,vec3(1./2.2)),1);
}
