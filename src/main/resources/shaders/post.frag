#version 330 core
in vec2 vUV;
out vec4 fragColor;
uniform sampler2D uScene;
uniform int uFXAA;
vec3 scene(vec2 p){return texture(uScene,p).rgb;}
float luma(vec3 c){return dot(c,vec3(.299,.587,.114));}
vec3 tone(vec3 x){return clamp((x*(2.51*x+.03))/(x*(2.43*x+.59)+.14),0.,1.);}
void main(){
    vec2 pixel=1./vec2(textureSize(uScene,0));vec3 c=scene(vUV);
    if(uFXAA==1){float n=luma(scene(vUV+vec2(0,pixel.y))),s=luma(scene(vUV-vec2(0,pixel.y))),e=luma(scene(vUV+vec2(pixel.x,0))),w=luma(scene(vUV-vec2(pixel.x,0)));float low=min(min(n,s),min(e,w)),high=max(max(n,s),max(e,w));
        if(high-low>max(.05,high*.12)){vec2 direction=normalize(vec2(-(n-s),e-w)+vec2(.00001))*pixel;c=(c*2.+scene(vUV+direction*.5)+scene(vUV-direction*.5))*.25;}}
    vec3 bloom=vec3(0);for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++){vec3 bright=scene(vUV+vec2(x,y)*pixel*5.);bloom+=max(bright-vec3(1.2),vec3(0));}
    fragColor=vec4(pow(tone(c+bloom*.035),vec3(1./2.2)),1);
}
