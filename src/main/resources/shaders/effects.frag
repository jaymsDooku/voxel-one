#version 330 core
in vec2 vUV;
layout(location=0) out vec4 outColor;
layout(location=1) out float outDepth;
uniform sampler2D uScene,uDepth,uHistory,uHistoryDepth;
uniform mat4 uInverseVP,uVP,uPreviousVP;
uniform vec3 uCamera,uSun;
uniform float uDaylight,uAmbient,uFogDensity;
uniform float uJitterU,uJitterV;
uniform int uFrame,uTAA,uStationary,uAO,uContact,uPreviewContactWeight,uSSR,uGI,uVolume;
uniform int uDecalCount;
uniform vec3 uDecalCenter[16],uDecalNormal[16],uDecalColor[16];
uniform float uDecalRadius[16],uDecalDepth[16],uDecalOpacity[16];
const float PI=3.14159265;
vec3 world(vec2 uv,float d){vec4 p=uInverseVP*vec4(uv*2.-1.,d*2.-1.,1);return p.xyz/p.w;}
vec3 project(vec3 p){vec4 q=uVP*vec4(p,1);return q.xyz/q.w*.5+.5;}
bool onScreen(vec3 q){return q.z>0.&&q.z<1.&&all(greaterThan(q.xy,vec2(0)))&&all(lessThan(q.xy,vec2(1)));}
float noise(vec2 p){return fract(sin(dot(p,vec2(12.9898,78.233))+float(uFrame)*.754)*43758.5453);}
// Screen-space ray marching uses world-distance thickness, not a constant nonlinear depth epsilon.
bool hit(vec3 origin,vec3 dir,float stride,int steps,vec3 receiverPlane,out vec2 uv){
    for(int i=1;i<=48;i++){
        if(i>steps)break;
        vec3 p=origin+dir*(float(i)*stride);vec3 q=project(p);
        if(!onScreen(q))break;
        ivec2 size=textureSize(uDepth,0);
        ivec2 pixel=clamp(ivec2(q.xy*vec2(size)),ivec2(0),size-1);
        float d=texelFetch(uDepth,pixel,0).r;if(d>=.999999)continue;
        if(receiverPlane.z>=0.){
            // Window depth is affine across a triangle. Compare at the actual
            // nearest depth texel so an oblique receiver cannot occlude itself.
            ivec2 receiver=clamp(ivec2((vUV+vec2(uJitterU,uJitterV))*vec2(size)),ivec2(0),size-1);
            vec2 pixelDelta=vec2(pixel-receiver);
            float planeDepth=receiverPlane.z+dot(receiverPlane.xy,pixelDelta);
            // D24 quantization affects both the reference and its finite differences.
            float tolerance=(1.+abs(pixelDelta.x)+abs(pixelDelta.y))*(2./16777215.);
            if(d>=planeDepth-tolerance)continue;
        }
        vec3 surface=world(q.xy,d);
        float along=dot(surface-uCamera,normalize(p-uCamera));
        float rayDistance=length(p-uCamera);
        if(rayDistance>along+.015&&rayDistance<along+stride*1.8){uv=q.xy;return true;}
    }
    return false;
}
void main(){
    vec2 texel=1./vec2(textureSize(uDepth,0));
    vec2 sceneUV=vUV+vec2(uJitterU,uJitterV);
    float depth=texture(uDepth,sceneUV).r;
    vec4 scene=texture(uScene,sceneUV);
    vec3 color=scene.rgb,p=world(sceneUV,depth);
    vec3 V=normalize(uCamera-p),N=vec3(0,1,0);
    if(depth<.999999){
        // Choose the least discontinuous derivative at silhouettes.
        float right=texture(uDepth,sceneUV+vec2(texel.x,0)).r,left=texture(uDepth,sceneUV-vec2(texel.x,0)).r;
        float up=texture(uDepth,sceneUV+vec2(0,texel.y)).r,down=texture(uDepth,sceneUV-vec2(0,texel.y)).r;
        vec3 a=world(sceneUV+vec2(texel.x,0),right)-p;
        vec3 b=p-world(sceneUV-vec2(texel.x,0),left);
        vec3 c=world(sceneUV+vec2(0,texel.y),up)-p;
        vec3 d=p-world(sceneUV-vec2(0,texel.y),down);
        bool useRight=dot(a,a)<dot(b,b),useUp=dot(c,c)<dot(d,d);
        N=normalize(cross(useRight?a:b,useUp?c:d));
        if(dot(N,V)<0.)N=-N;
        float occlusion=0.;vec3 bounce=vec3(0);
        // Horizon AO: integrate the maximum elevation in four azimuths, bounded to 2 world units.
        if(uAO==1||uGI==1)for(int axis=0;axis<4;axis++){
            float angle=float(axis)*PI*.5+noise(gl_FragCoord.xy)*.35;
            vec2 dir=vec2(cos(angle),sin(angle));float horizon=0.;
            for(int step=1;step<=4;step++){
                vec2 uv=sceneUV+dir*texel*float(step*3);
                if(any(lessThan(uv,vec2(0)))||any(greaterThan(uv,vec2(1))))continue;
                float z=texture(uDepth,uv).r;if(z>=.999999)continue;
                vec3 delta=world(uv,z)-p;float dist=length(delta);
                if(dist>.02&&dist<2.){
                    float cosine=max(0.,dot(N,delta/dist)-.12)*(1.-dist/2.);
                    horizon=max(horizon,cosine);
                    if(uGI==1)bounce+=texture(uScene,uv).rgb*cosine*.018;
                }
            }
            occlusion+=horizon;
        }
        if(uAO==1)color*=1.-clamp(occlusion*.18,0.,.65);
        if(uGI==1)color+=bounce;
        vec2 uv;
        vec3 receiverPlane=vec3(useRight?right-depth:depth-left,useUp?up-depth:depth-down,depth);
        float directShare=uPreviewContactWeight==1?scene.a:1.;
        if(uContact==1&&directShare>0.&&dot(N,uSun)>0.&&hit(p+N*.025,uSun,.08,12,receiverPlane,uv))
            color*=1.-.35*uDaylight*directShare;
        if(uSSR==1&&hit(p+N*.035,reflect(-V,N),.18,32,vec3(0,0,-1),uv)){
            float edge=clamp(min(min(uv.x,uv.y),min(1.-uv.x,1.-uv.y))*12.,0.,1.);
            float fresnel=.04+.96*pow(1.-max(0.,dot(N,V)),5.);
            color=mix(color,texture(uScene,uv).rgb,edge*fresnel*.35);
        }
    }
    if(depth<.999999){
        for(int i=0;i<16;i++){
            if(i>=uDecalCount)break;
            vec3 delta=p-uDecalCenter[i];float plane=dot(delta,uDecalNormal[i]);
            float radial=length(delta-plane*uDecalNormal[i]);
            if(abs(plane)<uDecalDepth[i]&&radial<uDecalRadius[i]&&dot(N,uDecalNormal[i])>.6){
                float alpha=uDecalOpacity[i]*(1.-smoothstep(.8,1.,radial/uDecalRadius[i]));
                color=mix(color,uDecalColor[i],alpha);
            }
        }
    }
    if(uVolume==1&&depth<.999999){
        vec3 ray=p-uCamera;float distance=min(length(ray),240.);vec3 dir=normalize(ray);
        float transmittance=1.;vec3 scatter=vec3(0);
        for(int i=0;i<12;i++){
            float t=(float(i)+noise(vUV*1000.))*distance/12.;vec3 samplePoint=uCamera+dir*t;
            float density=uFogDensity*exp(clamp(-(samplePoint.y-24.)*.025,-8.,4.));
            float extinction=exp(-density*distance/12.);
            vec2 uv;float visible=hit(samplePoint,uSun,1.5,6,vec3(0,0,-1),uv)?0.:1.;
            float g=.65,cosine=dot(dir,uSun);
            float phase=(1.-g*g)/(4.*PI*pow(1.+g*g-2.*g*cosine,1.5));
            vec3 light=vec3(.18,.27,.42)*uAmbient+vec3(1.,.88,.62)*phase*visible*uDaylight*2.;
            scatter+=transmittance*(1.-extinction)*light;transmittance*=extinction;
        }
        color=color*transmittance+scatter;
    }
    if(uTAA==1){
        // Opposite jitter phases can cover opposite sides of a subpixel edge.
        // At rest they contribute coverage to the very same output pixel.
        vec3 low=color,high=color;
        float minDepth=depth,maxDepth=depth;
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++){
            vec2 uv=sceneUV+vec2(x,y)*texel;
            vec3 s=texture(uScene,uv).rgb;low=min(low,s);high=max(high,s);
            if(uStationary==1){
                float z=texture(uDepth,uv).r;
                minDepth=min(minDepth,z);maxDepth=max(maxDepth,z);
            }
        }
        vec4 prev=uPreviousVP*vec4(p,1);
        vec3 q=uStationary==1?vec3(vUV,depth):prev.xyz/prev.w*.5+.5;
        float depthTolerance=max(.00003,fwidth(depth)*2.);
        if(uStationary==1||(prev.w>0.&&onScreen(q))){
            float oldDepth=texture(uHistoryDepth,q.xy).r;
            bool sameSurface=uStationary==1?
                oldDepth>=minDepth-depthTolerance&&oldDepth<=maxDepth+depthTolerance:
                abs(oldDepth-q.z)<depthTolerance;
            if(sameSurface){
                vec3 history=clamp(texture(uHistory,q.xy).rgb,low,high);
                float motion=length((q.xy-vUV)*vec2(textureSize(uHistory,0)));
                color=mix(color,history,clamp(.96-motion*.04,.1,.96));
            }
        }
    }
    outColor=vec4(max(color,vec3(0)),1);outDepth=depth;
}
